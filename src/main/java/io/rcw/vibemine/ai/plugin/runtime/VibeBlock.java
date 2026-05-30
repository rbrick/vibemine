package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * JavaScript-safe wrapper/binding for Vibe Block functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeBlock {
    private final Block block;

    /**
     * JavaScript binding for {@code VibeBlock}.
     */
    public VibeBlock(Block block) {
        this.block = block;
    }

    /**
     * JavaScript binding for {@code getType}.
     */
    public String getType() { return this.block.getType().name(); }

    /**
     * JavaScript binding for {@code getBlockData}.
     * Returns the full Bukkit block data string, preserving properties like facing, half, open, waterlogged, etc.
     */
    public String getBlockData() { return this.block.getBlockData().getAsString(); }

    /**
     * JavaScript binding for {@code setBlockData}.
     * Accepts Bukkit block data strings such as minecraft:oak_stairs[facing=north,half=bottom].
     */
    public void setBlockData(String blockData) {
        this.block.setBlockData(parseBlockData(blockData), false);
    }

    /**
     * JavaScript binding for {@code setType}.
     */
    public void setType(String material) {
        setType(material, false);
    }

    /**
     * JavaScript binding for {@code setType} with explicit physics behavior.
     */
    public void setType(String material, boolean applyPhysics) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        this.block.setType(matched, applyPhysics);
    }

    /**
     * JavaScript binding for {@code captureState}.
     * Captures material, full block data, and tile-entity data like chest/sign contents for later restore.
     */
    public VibeBlockSnapshot captureState() { return new VibeBlockSnapshot(this.block.getState()); }

    /**
     * JavaScript binding for {@code restoreState}.
     */
    public boolean restoreState(VibeBlockSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Snapshot cannot be null");
        return snapshot.restoreTo(this, false);
    }

    /**
     * JavaScript binding for {@code restoreState} with explicit physics behavior.
     */
    public boolean restoreState(VibeBlockSnapshot snapshot, boolean applyPhysics) {
        if (snapshot == null) throw new IllegalArgumentException("Snapshot cannot be null");
        return snapshot.restoreTo(this, applyPhysics);
    }
    /**
     * JavaScript binding for {@code isAir}.
     */
    public boolean isAir() { return this.block.getType().isAir(); }

    /**
     * JavaScript binding for {@code isSolid}.
     */
    public boolean isSolid() { return this.block.getType().isSolid(); }

    /**
     * JavaScript binding for {@code getX}.
     */
    public int getX() { return this.block.getX(); }

    /**
     * JavaScript binding for {@code getY}.
     */
    public int getY() { return this.block.getY(); }

    /**
     * JavaScript binding for {@code getZ}.
     */
    public int getZ() { return this.block.getZ(); }

    /**
     * JavaScript binding for {@code getLocation}.
     */
    public VibeLocation getLocation() { return new VibeLocation(this.block.getLocation()); }
    /**
     * JavaScript binding for {@code getWorld}.
     */
    public VibeWorld getWorld() { return VibeRuntimeWrappers.world(this.block.getWorld()); }

    /**
     * JavaScript binding for {@code unwrap}.
     */
    Block unwrap() { return this.block; }

    private BlockData parseBlockData(String blockData) {
        if (blockData == null || blockData.isBlank()) throw new IllegalArgumentException("Block data cannot be blank");
        return org.bukkit.Bukkit.createBlockData(blockData);
    }
}
