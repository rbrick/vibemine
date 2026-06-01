package io.rcw.vibemine.ai.plugin.runtime.worldgen;

import io.rcw.vibemine.ai.plugin.runtime.dsl.AsmExpressionCompiler;
import io.rcw.vibemine.ai.plugin.runtime.dsl.CompiledExpression;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.WorldInfo;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class VibeChunkGenerators {
    private VibeChunkGenerators() {}

    public static ChunkGenerator voidGenerator() {
        return new VoidGenerator();
    }

    public static ChunkGenerator fromOptions(Value generator) {
        if (generator == null || generator.isNull()) return null;
        String type = stringMember(generator, "type", "normal").toLowerCase();
        return switch (type) {
            case "void", "empty" -> new VoidGenerator(parseStructures(generator));
            case "flat", "layers", "layered" -> new LayeredGenerator(parseLayers(generator, true), parseStructures(generator));
            case "rules", "dsl" -> new RuleGenerator(parseLayers(generator, true), parseStructures(generator), parseRules(generator), parseVariables(generator));
            case "compiled_column", "compiledcolumn", "asm_column", "asmcolumn" -> new CompiledColumnGenerator(parseLayers(generator, false), parseStructures(generator), parseCompiledColumns(generator), parseCompiledSpans(generator));
            case "column", "columnfunction", "column_callback" -> new ColumnCallbackGenerator(parseLayers(generator, false), parseStructures(generator), valueMember(generator, "column", valueMember(generator, "callback", null)));
            case "function", "callback", "javascript", "js" -> new CallbackGenerator(parseLayers(generator, false), parseStructures(generator), valueMember(generator, "block", valueMember(generator, "callback", null)), intMember(generator, "minY", Integer.MIN_VALUE), intMember(generator, "maxY", Integer.MAX_VALUE));
            default -> throw new IllegalArgumentException("Unsupported generator type '" + type + "'. Expected one of: void, layers, rules, compiled_column, column, function");
        };
    }

    private static List<Layer> parseLayers(Value generator, boolean defaultIfEmpty) {
        List<Layer> layers = new ArrayList<>();
        if (generator.hasMember("layers") && generator.getMember("layers").hasArrayElements()) {
            Value array = generator.getMember("layers");
            int y = intMember(generator, "minY", -64);
            for (long i = 0; i < array.getArraySize(); i++) {
                Value layer = array.getArrayElement(i);
                String material = stringMember(layer, "material", "minecraft:stone");
                if (layer.hasMember("from") || layer.hasMember("to")) {
                    int from = intMember(layer, "from", y);
                    int to = intMember(layer, "to", from);
                    layers.add(new Layer(from, to, blockData(material)));
                    y = to + 1;
                } else {
                    int height = Math.max(1, intMember(layer, "height", 1));
                    layers.add(new Layer(y, y + height - 1, blockData(material)));
                    y += height;
                }
            }
        }
        if (layers.isEmpty() && defaultIfEmpty) {
            layers.add(new Layer(-64, -61, blockData("minecraft:bedrock")));
            layers.add(new Layer(-60, 58, blockData("minecraft:stone")));
            layers.add(new Layer(59, 61, blockData("minecraft:dirt")));
            layers.add(new Layer(62, 62, blockData("minecraft:grass_block")));
        }
        return List.copyOf(layers);
    }

    private static BlockData blockData(String materialOrData) {
        if (materialOrData == null || materialOrData.isBlank()) throw new IllegalArgumentException("Layer material cannot be blank");
        String value = materialOrData.trim();
        try {
            return Bukkit.createBlockData(value);
        } catch (IllegalArgumentException ignored) {
            Material material = Material.matchMaterial(value);
            if (material == null) throw new IllegalArgumentException("Unknown layer material/block data: " + materialOrData);
            return material.createBlockData();
        }
    }

    private static String parseVariables(Value generator) {
        if (generator == null || generator.isNull() || !generator.hasMember("variables") || !generator.getMember("variables").hasMembers()) return "";
        StringBuilder declarations = new StringBuilder();
        Value variables = generator.getMember("variables");
        for (String key : variables.getMemberKeys()) {
            if (!key.matches("[A-Za-z_$][A-Za-z0-9_$]*")) continue;
            Value value = variables.getMember(key);
            if (value == null || value.isNull()) continue;
            if (value.fitsInLong()) declarations.append("var ").append(key).append("=").append(value.asLong()).append(";\n");
            else if (value.isNumber()) declarations.append("var ").append(key).append("=").append(value.asDouble()).append(";\n");
            else if (value.isBoolean()) declarations.append("var ").append(key).append("=").append(value.asBoolean()).append(";\n");
            else declarations.append("var ").append(key).append("=").append("'").append(value.asString().replace("'", "\\'")).append("';\n");
        }
        return declarations.toString();
    }

    private static List<Rule> parseRules(Value generator) {
        List<Rule> rules = new ArrayList<>();
        if (generator == null || generator.isNull() || !generator.hasMember("rules") || !generator.getMember("rules").hasArrayElements()) return List.of();
        Value array = generator.getMember("rules");
        for (long i = 0; i < array.getArraySize(); i++) {
            Value rule = array.getArrayElement(i);
            String when = stringMember(rule, "when", "false");
            String block = stringMember(rule, "block", null);
            String blockData = stringMember(rule, "blockData", block);
            if (blockData == null || blockData.isBlank()) continue;
            rules.add(new Rule(when, blockData));
        }
        return List.copyOf(rules);
    }

    private static List<Structure> parseStructures(Value generator) {
        List<Structure> structures = new ArrayList<>();
        parseStructures(generator, "structures", structures);
        parseStructures(generator, "largeStructures", structures);
        parseStructures(generator, "large_structures", structures);
        return List.copyOf(structures);
    }

    private static void parseStructures(Value generator, String key, List<Structure> structures) {
        if (generator == null || generator.isNull() || !generator.hasMember(key) || !generator.getMember(key).hasArrayElements()) return;
        Value array = generator.getMember(key);
        for (long i = 0; i < array.getArraySize(); i++) {
            Value structure = array.getArrayElement(i);
            if (!structure.hasMember("blocks") || !structure.getMember("blocks").hasArrayElements()) continue;
            List<StructureBlock> blocks = new ArrayList<>();
            Value blockArray = structure.getMember("blocks");
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (long b = 0; b < blockArray.getArraySize(); b++) {
                Value block = blockArray.getArrayElement(b);
                String data = stringMember(block, "blockData", null);
                if (data == null) data = stringMember(block, "material", "minecraft:air");
                int x = intMember(block, "x", 0);
                int y = intMember(block, "y", 0);
                int z = intMember(block, "z", 0);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                blocks.add(new StructureBlock(x, y, z, blockData(data)));
            }
            if (blocks.isEmpty()) continue;
            int configuredRadius = intMember(structure, "maxRadius", intMember(structure, "radius", -1));
            int blockRadius = Math.max(Math.max(Math.abs(minX), Math.abs(maxX)), Math.max(Math.abs(minZ), Math.abs(maxZ))) + 16;
            int searchRadius = Math.max(16, configuredRadius >= 0 ? configuredRadius : blockRadius);
            structures.add(new Structure(
                    intMember(structure, "spacing", 8),
                    doubleMember(structure, "chance", 1.0),
                    intMember(structure, "y", 64),
                    stringMember(structure, "placement", "fixed"),
                    intMember(structure, "yOffset", 0),
                    searchRadius,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    blocks
            ));
        }
    }

    private static void generateStructures(WorldInfo worldInfo, int chunkX, int chunkZ, ChunkData chunkData, List<Structure> structures) {
        if (structures.isEmpty()) return;
        int chunkWorldX = chunkX * 16;
        int chunkWorldZ = chunkZ * 16;
        int minY = chunkData.getMinHeight();
        int maxY = chunkData.getMaxHeight() - 1;
        for (Structure structure : structures) {
            int spacing = Math.max(1, structure.spacing());
            int radiusChunks = Math.max(1, (int) Math.ceil(structure.searchRadius() / 16.0) + 1);
            int minCellX = Math.floorDiv(chunkX - radiusChunks, spacing);
            int maxCellX = Math.floorDiv(chunkX + radiusChunks, spacing);
            int minCellZ = Math.floorDiv(chunkZ - radiusChunks, spacing);
            int maxCellZ = Math.floorDiv(chunkZ + radiusChunks, spacing);
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                int anchorChunkX = cellX * spacing;
                int anchorChunkZ = cellZ * spacing;
                Random random = new Random(worldInfo.getSeed() ^ (((long) anchorChunkX) << 32) ^ (anchorChunkZ * 341873128712L) ^ (structure.blocks().size() * 1000003L));
                if (random.nextDouble() > structure.chance()) continue;
                int originWorldX = anchorChunkX * 16 + random.nextInt(16);
                int originWorldZ = anchorChunkZ * 16 + random.nextInt(16);
                if (originWorldX + structure.maxX() < chunkWorldX || originWorldX + structure.minX() > chunkWorldX + 15) continue;
                if (originWorldZ + structure.maxZ() < chunkWorldZ || originWorldZ + structure.minZ() > chunkWorldZ + 15) continue;

                int originY = structure.y();
                if (structure.placement().equalsIgnoreCase("surface")) {
                    int localOriginX = originWorldX - chunkWorldX;
                    int localOriginZ = originWorldZ - chunkWorldZ;
                    if (localOriginX < 0 || localOriginX > 15 || localOriginZ < 0 || localOriginZ > 15) continue;
                    int surface = surfaceY(chunkData, localOriginX, localOriginZ);
                    if (surface == Integer.MIN_VALUE) continue;
                    originY = surface + structure.yOffset();
                }

                for (StructureBlock block : structure.blocks()) {
                    int worldX = originWorldX + block.x();
                    int y = originY + block.y();
                    int worldZ = originWorldZ + block.z();
                    int x = worldX - chunkWorldX;
                    int z = worldZ - chunkWorldZ;
                    if (x < 0 || x > 15 || z < 0 || z > 15 || y < minY || y > maxY) continue;
                    chunkData.setBlock(x, y, z, block.blockData());
                }
            }
        }
    }

    private static int surfaceY(ChunkData chunkData, int x, int z) {
        for (int y = chunkData.getMaxHeight() - 1; y >= chunkData.getMinHeight(); y--) {
            if (!chunkData.getType(x, y, z).isAir()) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static String stringMember(Value value, String key, String fallback) {
        if (value == null || value.isNull() || !value.hasMember(key) || value.getMember(key).isNull()) return fallback;
        return value.getMember(key).asString();
    }

    private static Value valueMember(Value value, String key, Value fallback) {
        if (value == null || value.isNull() || !value.hasMember(key) || value.getMember(key).isNull()) return fallback;
        return value.getMember(key);
    }

    private static int intMember(Value value, String key, int fallback) {
        if (value == null || value.isNull() || !value.hasMember(key) || value.getMember(key).isNull()) return fallback;
        Value member = value.getMember(key);
        if (member.fitsInInt()) return member.asInt();
        if (member.isNumber()) return (int) Math.floor(member.asDouble());
        if (member.isString()) {
            try {
                return (int) Math.floor(Double.parseDouble(member.asString().trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean booleanMember(Value value, String key, boolean fallback) {
        if (value == null || value.isNull() || !value.hasMember(key) || value.getMember(key).isNull()) return fallback;
        return value.getMember(key).asBoolean();
    }

    private static double doubleMember(Value value, String key, double fallback) {
        if (value == null || value.isNull() || !value.hasMember(key) || value.getMember(key).isNull()) return fallback;
        return value.getMember(key).asDouble();
    }

    private static List<CompiledColumn> parseCompiledColumns(Value generator) {
        if (generator == null || generator.isNull() || !generator.hasMember("columns") || !generator.getMember("columns").hasArrayElements()) return List.of();
        List<CompiledColumn> columns = new ArrayList<>();
        List<String> variables = new ArrayList<>(List.of("x", "z", "seed", "chunkX", "chunkZ"));
        AsmExpressionCompiler compiler = new AsmExpressionCompiler();
        Value array = generator.getMember("columns");
        for (long i = 0; i < array.getArraySize(); i++) {
            Value column = array.getArrayElement(i);
            String name = stringMember(column, "name", "c" + i);
            String expr = stringMember(column, "expr", stringMember(column, "expression", "0"));
            CompiledExpression compiled;
            try {
                compiled = compiler.compile(expr, variables);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Failed to compile compiled_column column '" + name + "' expression `" + expr + "` with available variables " + variables + ": " + exception.getMessage(), exception);
            }
            columns.add(new CompiledColumn(name, compiled));
            variables.add(name);
        }
        return List.copyOf(columns);
    }

    private static List<CompiledSpan> parseCompiledSpans(Value generator) {
        if (generator == null || generator.isNull() || !generator.hasMember("spans") || !generator.getMember("spans").hasArrayElements()) return List.of();
        List<String> variables = new ArrayList<>(List.of("x", "z", "seed", "chunkX", "chunkZ"));
        if (generator.hasMember("columns") && generator.getMember("columns").hasArrayElements()) {
            Value columns = generator.getMember("columns");
            for (long i = 0; i < columns.getArraySize(); i++) variables.add(stringMember(columns.getArrayElement(i), "name", "c" + i));
        }
        AsmExpressionCompiler compiler = new AsmExpressionCompiler();
        List<CompiledSpan> spans = new ArrayList<>();
        Value array = generator.getMember("spans");
        for (long i = 0; i < array.getArraySize(); i++) {
            Value span = array.getArrayElement(i);
            String from = stringMember(span, "from", stringMember(span, "y", "0"));
            String to = stringMember(span, "to", from);
            String block = stringMember(span, "block", stringMember(span, "material", stringMember(span, "blockData", "stone")));
            try {
                spans.add(new CompiledSpan(compiler.compile(from, variables), compiler.compile(to, variables), blockData(block)));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Failed to compile compiled_column span from `" + from + "` to `" + to + "` block `" + block + "` with available variables " + variables + ": " + exception.getMessage(), exception);
            }
        }
        return List.copyOf(spans);
    }

    private record Layer(int fromY, int toY, BlockData blockData) {}
    private record Rule(String when, String blockData) {}
    private record CompiledColumn(String name, CompiledExpression expr) {}
    private record CompiledSpan(CompiledExpression from, CompiledExpression to, BlockData blockData) {}
    private record Structure(int spacing, double chance, int y, String placement, int yOffset, int searchRadius, int minX, int maxX, int minZ, int maxZ, List<StructureBlock> blocks) {}
    private record StructureBlock(int x, int y, int z, BlockData blockData) {}

    private static final class VoidGenerator extends ChunkGenerator {
        private final List<Structure> structures;

        private VoidGenerator() {
            this(List.of());
        }

        private VoidGenerator(List<Structure> structures) {
            this.structures = structures;
        }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            generateStructures(worldInfo, chunkX, chunkZ, chunkData, structures);
        }
    }

    private abstract static class ScriptedGenerator extends ChunkGenerator {
        final Map<String, BlockData> blockCache = new ConcurrentHashMap<>();
        final ThreadLocal<Context> contexts = ThreadLocal.withInitial(() -> Context.newBuilder("js")
                .allowHostAccess(org.graalvm.polyglot.HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .build());

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        BlockData cachedBlock(String data) { return blockCache.computeIfAbsent(data, VibeChunkGenerators::blockData); }

        String helperWrapped(String source) {
            return "(function(){ var abs=Math.abs, floor=Math.floor, ceil=Math.ceil, sin=Math.sin, cos=Math.cos, sqrt=Math.sqrt, min=Math.min, max=Math.max; function mod(a,b){return ((a%b)+b)%b;} function _hash(ix,iz,s){ var n=Math.sin(ix*127.1+iz*311.7+(s||0)*74.7)*43758.5453123; return n-Math.floor(n); } function _fade(t){ return t*t*t*(t*(t*6-15)+10); } function noise2(a,b,s){ var x0=Math.floor(a), z0=Math.floor(b), x1=x0+1, z1=z0+1; var tx=_fade(a-x0), tz=_fade(b-z0); var v00=_hash(x0,z0,s), v10=_hash(x1,z0,s), v01=_hash(x0,z1,s), v11=_hash(x1,z1,s); var xa=v00+(v10-v00)*tx; var xb=v01+(v11-v01)*tx; return (xa+(xb-xa)*tz)*2-1; } var simplex2=noise2, perlin2=noise2; function fbm2(x,z,seed,oct,lac,gain){ var sum=0, amp=1, freq=1, norm=0; for(var i=0;i<oct;i++){ sum += noise2(x*freq,z*freq,seed+i*1013)*amp; norm += amp; amp *= gain; freq *= lac; } return norm===0?0:sum/norm; } function ridged2(x,z,seed,oct,lac,gain){ var sum=0, amp=1, freq=1, norm=0; for(var i=0;i<oct;i++){ var n=1-Math.abs(noise2(x*freq,z*freq,seed+i*1619)); n=n*n*2-1; sum += n*amp; norm += amp; amp *= gain; freq *= lac; } return norm===0?0:sum/norm; } function billow2(x,z,seed,oct,lac,gain){ var sum=0, amp=1, freq=1, norm=0; for(var i=0;i<oct;i++){ var n=Math.abs(noise2(x*freq,z*freq,seed+i*2027))*2-1; sum += n*amp; norm += amp; amp *= gain; freq *= lac; } return norm===0?0:sum/norm; } function clamp(v,a,b){return Math.max(a,Math.min(b,v));} function lerp(a,b,t){return a+(b-a)*t;} function smoothstep(a,b,x){var t=clamp((x-a)/(b-a),0,1); return t*t*(3-2*t);} function terrace(v,s){return s<=1?v:Math.floor(v*s)/s;} function warpX(x,z,seed,f,str){return x+noise2(x*f,z*f,seed+37)*str;} function warpZ(x,z,seed,f,str){return z+noise2(x*f,z*f,seed+73)*str;} var user = " + source + "; return function(){ return user.apply(null, arguments); }; })()";
        }

        void fillLayers(ChunkData chunkData, List<Layer> layers) {
            int minY = chunkData.getMinHeight();
            int maxY = chunkData.getMaxHeight() - 1;
            for (Layer layer : layers) {
                int from = Math.max(minY, layer.fromY());
                int to = Math.min(maxY, layer.toY());
                if (from > to) continue;
                for (int y = from; y <= to; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) chunkData.setBlock(x, y, z, layer.blockData());
            }
        }
    }

    private static final class RuleGenerator extends ScriptedGenerator {
        private final List<Layer> layers;
        private final List<Structure> structures;
        private final List<Rule> rules;
        private final String variables;
        private final ThreadLocal<List<Value>> compiledRules;

        private RuleGenerator(List<Layer> layers, List<Structure> structures, List<Rule> rules, String variables) {
            this.layers = layers;
            this.structures = structures;
            this.rules = rules;
            this.variables = variables;
            this.compiledRules = ThreadLocal.withInitial(() -> rules.stream()
                    .map(rule -> contexts.get().eval("js", "(function(x,y,z,seed,chunkX,chunkZ){ var abs=Math.abs, floor=Math.floor, sin=Math.sin, cos=Math.cos, sqrt=Math.sqrt, min=Math.min, max=Math.max; " + variables + " function mod(a,b){return ((a%b)+b)%b;} function noise2(a,b,s){ var n=Math.sin(a*12.9898+b*78.233+s*0.0001)*43758.5453; return n-Math.floor(n); } return (" + rule.when() + "); })"))
                    .toList());
        }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            fillLayers(chunkData, layers);
            try {
                List<Value> functions = compiledRules.get();
                int minY = chunkData.getMinHeight();
                int maxY = chunkData.getMaxHeight() - 1;
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = minY; y <= maxY; y++) {
                    int worldX = chunkX * 16 + x;
                    int worldZ = chunkZ * 16 + z;
                    for (int i = 0; i < functions.size(); i++) {
                        if (functions.get(i).execute(worldX, y, worldZ, worldInfo.getSeed(), chunkX, chunkZ).asBoolean()) {
                            chunkData.setBlock(x, y, z, cachedBlock(rules.get(i).blockData()));
                            break;
                        }
                    }
                }
            } catch (RuntimeException exception) {
                // Never let generated worldgen JavaScript crash chunk generation/server startup.
            }
            generateStructures(worldInfo, chunkX, chunkZ, chunkData, structures);
        }
    }

    private static final class CompiledColumnGenerator extends ChunkGenerator {
        private final List<Layer> layers;
        private final List<Structure> structures;
        private final List<CompiledColumn> columns;
        private final List<CompiledSpan> spans;

        private CompiledColumnGenerator(List<Layer> layers, List<Structure> structures, List<CompiledColumn> columns, List<CompiledSpan> spans) {
            this.layers = layers;
            this.structures = structures;
            this.columns = columns;
            this.spans = spans;
        }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            fillStaticLayers(chunkData, layers);
            int minY = chunkData.getMinHeight();
            int maxY = chunkData.getMaxHeight() - 1;
            double[] vars = new double[5 + columns.size()];
            vars[2] = worldInfo.getSeed();
            vars[3] = chunkX;
            vars[4] = chunkZ;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                vars[0] = chunkX * 16 + x;
                vars[1] = chunkZ * 16 + z;
                for (int i = 0; i < columns.size(); i++) vars[5 + i] = columns.get(i).expr().eval(vars);
                for (CompiledSpan span : spans) {
                    int from = Math.max(minY, (int) Math.floor(span.from().eval(vars)));
                    int to = Math.min(maxY, (int) Math.floor(span.to().eval(vars)));
                    if (from > to) continue;
                    for (int y = from; y <= to; y++) chunkData.setBlock(x, y, z, span.blockData());
                }
            }
            generateStructures(worldInfo, chunkX, chunkZ, chunkData, structures);
        }
    }

    private static void fillStaticLayers(ChunkData chunkData, List<Layer> layers) {
        int minY = chunkData.getMinHeight();
        int maxY = chunkData.getMaxHeight() - 1;
        for (Layer layer : layers) {
            int from = Math.max(minY, layer.fromY());
            int to = Math.min(maxY, layer.toY());
            if (from > to) continue;
            for (int y = from; y <= to; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) chunkData.setBlock(x, y, z, layer.blockData());
        }
    }

    private static final class ColumnCallbackGenerator extends ScriptedGenerator {
        private final List<Layer> layers;
        private final List<Structure> structures;
        private final String source;
        private final Value directFunction;
        private final Object directFunctionLock = new Object();
        private final ThreadLocal<Value> function;

        private ColumnCallbackGenerator(List<Layer> layers, List<Structure> structures, Value callback) {
            if (callback == null || callback.isNull()) throw new IllegalArgumentException("column generator requires column/callback source or function");
            this.layers = layers;
            this.structures = structures;
            if (callback.isString()) {
                this.source = callback.asString();
                if (this.source.isBlank()) throw new IllegalArgumentException("column generator requires column/callback source or function");
                this.directFunction = null;
                this.function = ThreadLocal.withInitial(() -> contexts.get().eval("js", helperWrapped(source)));
            } else if (callback.canExecute()) {
                this.source = null;
                this.directFunction = callback;
                this.function = ThreadLocal.withInitial(() -> directFunction);
            } else {
                throw new IllegalArgumentException("column generator column/callback must be a string or executable function");
            }
        }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            fillLayers(chunkData, layers);
            try {
                Value callback = function.get();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    int worldX = chunkX * 16 + x;
                    int worldZ = chunkZ * 16 + z;
                    Value result = executeColumnCallback(callback, worldX, worldZ, worldInfo.getSeed(), chunkX, chunkZ);
                    applyColumnResult(chunkData, x, z, result);
                }
            } catch (RuntimeException exception) {
                Bukkit.getLogger().warning("Vibemine column generator failed in world '" + worldInfo.getName() + "' chunk " + chunkX + "," + chunkZ + ": " + exception.getMessage());
            }
            generateStructures(worldInfo, chunkX, chunkZ, chunkData, structures);
        }

        private Value executeColumnCallback(Value callback, int x, int z, long seed, int chunkX, int chunkZ) {
            if (directFunction == null) return callback.execute(x, z, seed, chunkX, chunkZ);
            synchronized (directFunctionLock) {
                return callback.execute(x, z, seed, chunkX, chunkZ);
            }
        }

        private void applyColumnResult(ChunkData chunkData, int x, int z, Value result) {
            if (result == null || result.isNull()) return;
            if (result.hasArrayElements()) {
                for (long i = 0; i < result.getArraySize(); i++) applySpan(chunkData, x, z, result.getArrayElement(i));
            } else {
                applySpan(chunkData, x, z, result);
            }
        }

        private void applySpan(ChunkData chunkData, int x, int z, Value span) {
            if (span == null || span.isNull() || !span.hasMembers()) return;
            String block = stringMember(span, "block", stringMember(span, "material", stringMember(span, "blockData", null)));
            if (block == null || block.isBlank()) return;
            int from = Math.max(chunkData.getMinHeight(), intMember(span, "from", intMember(span, "y", chunkData.getMinHeight())));
            int to = Math.min(chunkData.getMaxHeight() - 1, intMember(span, "to", from));
            if (from > to) return;
            BlockData blockData = cachedBlock(block);
            for (int y = from; y <= to; y++) chunkData.setBlock(x, y, z, blockData);
        }
    }

    private static final class CallbackGenerator extends ScriptedGenerator {
        private final List<Layer> layers;
        private final List<Structure> structures;
        private final String source;
        private final Value directFunction;
        private final Object directFunctionLock = new Object();
        private final int minY;
        private final int maxY;
        private final ThreadLocal<Value> function;

        private CallbackGenerator(List<Layer> layers, List<Structure> structures, Value callback, int minY, int maxY) {
            if (callback == null || callback.isNull()) throw new IllegalArgumentException("function generator requires block/callback source or function");
            this.layers = layers;
            this.structures = structures;
            this.minY = minY;
            this.maxY = maxY;
            if (callback.isString()) {
                this.source = callback.asString();
                if (this.source.isBlank()) throw new IllegalArgumentException("function generator requires block/callback source or function");
                this.directFunction = null;
                this.function = ThreadLocal.withInitial(() -> contexts.get().eval("js", helperWrapped(source)));
            } else if (callback.canExecute()) {
                this.source = null;
                this.directFunction = callback;
                this.function = ThreadLocal.withInitial(() -> directFunction);
            } else {
                throw new IllegalArgumentException("function generator block/callback must be a string or executable function");
            }
        }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            fillLayers(chunkData, layers);
            try {
                Value callback = function.get();
                int fromY = Math.max(chunkData.getMinHeight(), minY);
                int toY = Math.min(chunkData.getMaxHeight() - 1, maxY);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = fromY; y <= toY; y++) {
                    int worldX = chunkX * 16 + x;
                    int worldZ = chunkZ * 16 + z;
                    Value result = executeBlockCallback(callback, worldX, y, worldZ, worldInfo.getSeed(), chunkX, chunkZ);
                    if (result == null || result.isNull()) continue;
                    String block = result.asString();
                    if (!block.isBlank()) chunkData.setBlock(x, y, z, cachedBlock(block));
                }
            } catch (RuntimeException exception) {
                Bukkit.getLogger().warning("Vibemine function generator failed in world '" + worldInfo.getName() + "' chunk " + chunkX + "," + chunkZ + ": " + exception.getMessage());
            }
            generateStructures(worldInfo, chunkX, chunkZ, chunkData, structures);
        }

        private Value executeBlockCallback(Value callback, int x, int y, int z, long seed, int chunkX, int chunkZ) {
            if (directFunction == null) return callback.execute(x, y, z, seed, chunkX, chunkZ);
            synchronized (directFunctionLock) {
                return callback.execute(x, y, z, seed, chunkX, chunkZ);
            }
        }
    }

    private static final class LayeredGenerator extends ChunkGenerator {
        private final List<Layer> layers;
        private final List<Structure> structures;

        private LayeredGenerator(List<Layer> layers, List<Structure> structures) {
            this.layers = layers;
            this.structures = structures;
        }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            int minY = chunkData.getMinHeight();
            int maxY = chunkData.getMaxHeight() - 1;
            for (Layer layer : layers) {
                int from = Math.max(minY, layer.fromY());
                int to = Math.min(maxY, layer.toY());
                if (from > to) continue;
                for (int y = from; y <= to; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            chunkData.setBlock(x, y, z, layer.blockData());
                        }
                    }
                }
            }
            generateStructures(worldInfo, chunkX, chunkZ, chunkData, structures);
        }
    }
}
