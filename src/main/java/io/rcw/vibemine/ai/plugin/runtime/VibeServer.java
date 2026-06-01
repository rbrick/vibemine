package io.rcw.vibemine.ai.plugin.runtime;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.runtime.worldgen.VibeChunkGenerators;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.Value;

import java.util.List;

/**
 * JavaScript-safe wrapper/binding for Vibe Server functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeServer {
    /**
     * JavaScript binding for {@code broadcast}.
     */
    public void broadcast(String message) {
        Bukkit.broadcast(VibeText.component(message));
    }

    /**
     * Sends a message only to players listed in a JavaScript object/array.
     * For objects like state.players, member names are treated as player names.
     * For arrays, each element is treated as a player name.
     */
    public void broadcastPlayers(Value players, String message) {
        if (players == null || players.isNull()) return;
        if (players.hasArrayElements()) {
            for (long i = 0; i < players.getArraySize(); i++) sendToPlayerName(players.getArrayElement(i), message);
            return;
        }
        if (players.hasMembers()) {
            for (String name : players.getMemberKeys()) sendToPlayerName(name, message);
        }
    }

    /**
     * Alias for {@link #broadcastPlayers(Value, String)}.
     */
    public void broadcastToPlayers(Value players, String message) {
        broadcastPlayers(players, message);
    }

    private void sendToPlayerName(Value name, String message) {
        if (name != null && !name.isNull()) sendToPlayerName(name.asString(), message);
    }

    private void sendToPlayerName(String name, String message) {
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) player.sendMessage(VibeText.component(message));
    }

    /**
     * JavaScript binding for {@code getPlayer}.
     */
    public VibePlayer getPlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        return VibeRuntimeWrappers.player(player);
    }

    /**
     * JavaScript binding for {@code getOnlinePlayers}.
     */
    public List<VibePlayer> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream().map(VibeRuntimeWrappers::player).toList();
    }

    /**
     * JavaScript binding for {@code getWorld}.
     */
    public VibeWorld getWorld(String name) {
        World world = Bukkit.getWorld(name);
        return VibeRuntimeWrappers.world(world);
    }

    /**
     * JavaScript binding for {@code getWorlds}.
     */
    public List<VibeWorld> getWorlds() {
        return Bukkit.getWorlds().stream().map(VibeRuntimeWrappers::world).toList();
    }

    /**
     * Creates or loads a world. Options may include seed, environment (normal/nether/the_end), and generator.
     * Generator examples:
     * {type:"void"}
     * {type:"layers", layers:[{from:-64,to:-61,material:"bedrock"},{from:-60,to:58,material:"stone"},{height:3,material:"dirt"},{height:1,material:"grass_block"}]}
     */
    public VibeWorld createWorld(String name, Value options) {
        validateWorldName(name);
        World existing = Bukkit.getWorld(name);
        if (existing != null) return VibeRuntimeWrappers.world(existing);

        WorldCreator creator = new WorldCreator(name);
        if (options != null && !options.isNull()) {
            if (options.hasMember("seed") && !options.getMember("seed").isNull()) creator.seed(options.getMember("seed").asLong());
            if (options.hasMember("environment") && !options.getMember("environment").isNull()) {
                creator.environment(parseEnvironment(options.getMember("environment").asString()));
            }
            Value generator = options.hasMember("generator") ? options.getMember("generator") : options;
            if (generator != null && !generator.isNull() && generator.isString()) {
                var manager = Vibemine.getInstance().getVibedPluginManager();
                if (manager != null) creator.generator(manager.exportedChunkGenerator(generator.asString()));
            } else {
                try {
                    var chunkGenerator = VibeChunkGenerators.fromOptions(generator);
                    if (chunkGenerator != null) creator.generator(chunkGenerator);
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException("World '" + name + "' generator options failed to compile: " + exception.getMessage(), exception);
                }
            }
        }

        World world = Bukkit.createWorld(creator);
        if (world == null) throw new IllegalStateException("World could not be created: " + name);
        return VibeRuntimeWrappers.world(world);
    }

    /** JavaScript-friendly overload for a normal world with default generation. */
    public VibeWorld createWorld(String name) { return createWorld(name, null); }

    /** Creates or loads an empty void world. */
    public VibeWorld createVoidWorld(String name) {
        validateWorldName(name);
        World existing = Bukkit.getWorld(name);
        if (existing != null) return VibeRuntimeWrappers.world(existing);
        WorldCreator creator = new WorldCreator(name).generator(VibeChunkGenerators.voidGenerator());
        World world = Bukkit.createWorld(creator);
        if (world == null) throw new IllegalStateException("World could not be created: " + name);
        return VibeRuntimeWrappers.world(world);
    }

    /** Unloads a world by name. Does not delete world files. */
    public boolean unloadWorld(String name, boolean save) {
        World world = Bukkit.getWorld(name);
        return world != null && Bukkit.unloadWorld(world, save);
    }

    /**
     * Places a rectangular cuboid structure in a world. Blocks is a JavaScript array of
     * {x,y,z,material} or {x,y,z,blockData}; coordinates are relative to origin.
     */
    public int placeStructure(String worldName, int originX, int originY, int originZ, Value blocks) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalArgumentException("Unknown world: " + worldName);
        if (blocks == null || blocks.isNull() || !blocks.hasArrayElements()) throw new IllegalArgumentException("blocks must be an array");
        int placed = 0;
        for (long i = 0; i < blocks.getArraySize(); i++) {
            Value block = blocks.getArrayElement(i);
            int x = memberInt(block, "x", 0);
            int y = memberInt(block, "y", 0);
            int z = memberInt(block, "z", 0);
            String data = memberString(block, "blockData", null);
            if (data == null) data = memberString(block, "material", "minecraft:air");
            world.getBlockAt(originX + x, originY + y, originZ + z).setBlockData(parseBlockData(data), false);
            placed++;
        }
        return placed;
    }

    /**
     * JavaScript binding for {@code getMaxPlayers}.
     */
    public int getMaxPlayers() { return Bukkit.getMaxPlayers(); }
    /**
     * JavaScript binding for {@code getVersion}.
     */
    public String getVersion() { return Bukkit.getVersion(); }

    private void validateWorldName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("World name cannot be blank");
        if (!name.matches("[a-zA-Z0-9_\\-]+")) throw new IllegalArgumentException("World names may only contain letters, numbers, _ and -");
    }

    private World.Environment parseEnvironment(String environment) {
        if (environment == null || environment.isBlank()) return World.Environment.NORMAL;
        String normalized = environment.trim().toUpperCase().replace('-', '_');
        if (normalized.equals("NETHER")) return World.Environment.NETHER;
        if (normalized.equals("END")) return World.Environment.THE_END;
        return World.Environment.valueOf(normalized);
    }

    private int memberInt(Value value, String key, int fallback) {
        if (value == null || value.isNull() || !value.hasMember(key) || value.getMember(key).isNull()) return fallback;
        return value.getMember(key).asInt();
    }

    private String memberString(Value value, String key, String fallback) {
        if (value == null || value.isNull() || !value.hasMember(key) || value.getMember(key).isNull()) return fallback;
        return value.getMember(key).asString();
    }

    private org.bukkit.block.data.BlockData parseBlockData(String materialOrData) {
        if (materialOrData == null || materialOrData.isBlank()) throw new IllegalArgumentException("Block material/data cannot be blank");
        String value = materialOrData.trim();
        try {
            return Bukkit.createBlockData(value);
        } catch (IllegalArgumentException ignored) {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(value);
            if (material == null) throw new IllegalArgumentException("Unknown material/block data: " + materialOrData);
            return material.createBlockData();
        }
    }
}
