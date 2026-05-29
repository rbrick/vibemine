package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
        this.world.playSound(location.unwrap(), sound, volume, pitch);
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
        this.world.playSound(new Location(this.world, x, y, z), sound, volume, pitch);
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

    private BlockData parseBlockData(String blockData) {
        if (blockData == null || blockData.isBlank()) throw new IllegalArgumentException("Block data cannot be blank");
        return org.bukkit.Bukkit.createBlockData(blockData);
    }
}
