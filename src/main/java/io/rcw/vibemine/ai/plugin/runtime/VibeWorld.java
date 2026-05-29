package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

/**
 * JavaScript-safe wrapper/binding for Vibe World functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeWorld {
    private final World world;

    /**
     * JavaScript binding for {@code VibeWorld}.
     */
    public VibeWorld(World world) {
        this.world = world;
    }

    /**
     * JavaScript binding for {@code getName}.
     */
    public String getName() { return this.world.getName(); }
    /**
     * JavaScript binding for {@code getTime}.
     */
    public long getTime() { return this.world.getTime(); }
    /**
     * JavaScript binding for {@code setTime}.
     */
    public void setTime(long time) { this.world.setTime(time); }
    /**
     * JavaScript binding for {@code hasStorm}.
     */
    public boolean hasStorm() { return this.world.hasStorm(); }
    /**
     * JavaScript binding for {@code setStorm}.
     */
    public void setStorm(boolean storm) { this.world.setStorm(storm); }

    /**
     * JavaScript binding for {@code location}.
     */
    public VibeLocation location(double x, double y, double z) {
        return new VibeLocation(new Location(this.world, x, y, z));
    }

    /**
     * JavaScript binding for {@code getBlockAt}.
     */
    public VibeBlock getBlockAt(int x, int y, int z) {
        return VibeRuntimeCache.block(this.world.getBlockAt(x, y, z));
    }

    /**
     * JavaScript binding for {@code setBlockAt}.
     */
    public void setBlockAt(int x, int y, int z, String material) {
        setBlockAt(x, y, z, material, false);
    }

    /**
     * JavaScript binding for {@code setBlockAt} with explicit physics behavior.
     */
    public void setBlockAt(int x, int y, int z, String material, boolean applyPhysics) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        this.world.getBlockAt(x, y, z).setType(matched, applyPhysics);
    }

    /**
     * JavaScript binding for {@code getBlockDataAt}.
     */
    public String getBlockDataAt(int x, int y, int z) {
        return this.world.getBlockAt(x, y, z).getBlockData().getAsString();
    }

    /**
     * JavaScript binding for {@code setBlockDataAt}.
     * Accepts Bukkit block data strings such as minecraft:oak_door[facing=east,half=lower,open=false].
     */
    public void setBlockDataAt(int x, int y, int z, String blockData) {
        this.world.getBlockAt(x, y, z).setBlockData(parseBlockData(blockData), false);
    }

    /**
     * JavaScript binding for {@code spawnEntity}.
     */
    public VibeEntity spawnEntity(String entityType, VibeLocation location) {
        EntityType type = EntityType.valueOf(entityType.toUpperCase());
        return VibeRuntimeCache.entity(this.world.spawnEntity(location.unwrap(), type));
    }

    /**
     * JavaScript binding for {@code strikeLightning}.
     */
    public void strikeLightning(VibeLocation location) {
        this.world.strikeLightning(location.unwrap());
    }

    /**
     * JavaScript binding for {@code playSound}.
     */
    public void playSound(VibeLocation location, String sound, float volume, float pitch) {
        if (location == null) throw new IllegalArgumentException("Location cannot be null");
        this.world.playSound(location.unwrap(), normalizeSound(sound), volume, pitch);
    }

    /**
     * JavaScript-friendly overload for numeric volume/pitch values.
     */
    public void playSound(VibeLocation location, String sound, double volume, double pitch) {
        playSound(location, sound, (float) volume, (float) pitch);
    }

    /**
     * JavaScript binding for {@code playSound} with default volume/pitch.
     */
    public void playSound(VibeLocation location, String sound) {
        playSound(location, sound, 1.0f, 1.0f);
    }

    /**
     * JavaScript binding for {@code playSoundAt}.
     */
    public void playSoundAt(double x, double y, double z, String sound, float volume, float pitch) {
        this.world.playSound(new Location(this.world, x, y, z), normalizeSound(sound), volume, pitch);
    }

    /**
     * JavaScript-friendly overload for numeric volume/pitch values.
     */
    public void playSoundAt(double x, double y, double z, String sound, double volume, double pitch) {
        playSoundAt(x, y, z, sound, (float) volume, (float) pitch);
    }

    /**
     * JavaScript binding for {@code playSoundAt} with default volume/pitch.
     */
    public void playSoundAt(double x, double y, double z, String sound) {
        playSoundAt(x, y, z, sound, 1.0f, 1.0f);
    }

    /**
     * JavaScript binding for {@code unwrap}.
     */
    World unwrap() { return this.world; }

    private String normalizeSound(String sound) {
        if (sound == null || sound.isBlank()) throw new IllegalArgumentException("Sound cannot be blank");

        String normalized = sound.trim();
        if (normalized.contains(":")) return normalized.toLowerCase();

        // Avoid Bukkit's legacy Sound enum here; Paper has marked that API for removal.
        // Accept modern keys (block.note_block.pling) and best-effort legacy enum names
        // (BLOCK_NOTE_BLOCK_PLING -> minecraft:block.note_block.pling).
        if (normalized.indexOf('.') < 0 && normalized.equals(normalized.toUpperCase())) {
            normalized = legacySoundNameToKeyPath(normalized);
        } else {
            normalized = normalized.toLowerCase();
        }
        return "minecraft:" + normalized;
    }

    private String legacySoundNameToKeyPath(String sound) {
        String lower = sound.toLowerCase();
        int first = lower.indexOf('_');
        int last = lower.lastIndexOf('_');
        if (first < 0) return lower;
        if (first == last) return lower.substring(0, first) + "." + lower.substring(first + 1);
        return lower.substring(0, first) + "." + lower.substring(first + 1, last) + "." + lower.substring(last + 1);
    }

    private BlockData parseBlockData(String blockData) {
        if (blockData == null || blockData.isBlank()) throw new IllegalArgumentException("Block data cannot be blank");
        return org.bukkit.Bukkit.createBlockData(blockData);
    }
}
