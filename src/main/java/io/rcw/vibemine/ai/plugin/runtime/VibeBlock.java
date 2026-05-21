package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Material;
import org.bukkit.block.Block;

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
     * JavaScript binding for {@code setType}.
     */
    public void setType(String material) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        this.block.setType(matched);
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
    public VibeWorld getWorld() { return VibeRuntimeCache.world(this.block.getWorld()); }

    /**
     * JavaScript binding for {@code unwrap}.
     */
    public Block unwrap() { return this.block; }
}
