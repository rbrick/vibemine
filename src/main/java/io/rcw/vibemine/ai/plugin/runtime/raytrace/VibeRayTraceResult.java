package io.rcw.vibemine.ai.plugin.runtime.raytrace;

import io.rcw.vibemine.ai.plugin.runtime.VibeBlock;
import io.rcw.vibemine.ai.plugin.runtime.VibeEntity;
import io.rcw.vibemine.ai.plugin.runtime.VibeLocation;
import io.rcw.vibemine.ai.plugin.runtime.VibeRuntimeWrappers;
import org.bukkit.util.RayTraceResult;

/**
 * JavaScript-safe wrapper for a Bukkit ray trace hit result.
 */
public final class VibeRayTraceResult {
    private final RayTraceResult result;

    /**
     * Creates a wrapper around a Bukkit ray trace result.
     */
    public VibeRayTraceResult(RayTraceResult result) {
        this.result = result;
    }

    /**
     * Returns whether the ray trace hit anything.
     */
    public boolean hasHit() { return this.result != null; }

    /**
     * Returns the hit position, or {@code null} if nothing was hit.
     */
    public VibeLocation getHitPosition() {
        if (this.result == null) return null;
        var vector = this.result.getHitPosition();
        var world = this.result.getHitBlock() != null ? this.result.getHitBlock().getWorld()
                : this.result.getHitEntity() != null ? this.result.getHitEntity().getWorld()
                : null;
        return world == null ? null : new VibeLocation(vector.toLocation(world));
    }

    /**
     * Returns the hit block, or {@code null} if no block was hit.
     */
    public VibeBlock getBlock() {
        return this.result == null ? null : VibeRuntimeWrappers.block(this.result.getHitBlock());
    }

    /**
     * Returns the hit entity, or {@code null} if no entity was hit.
     */
    public VibeEntity getEntity() {
        return this.result == null ? null : VibeRuntimeWrappers.entity(this.result.getHitEntity());
    }

    /**
     * Returns the hit block face name, or {@code null} if unavailable.
     */
    public String getBlockFace() {
        return this.result == null || this.result.getHitBlockFace() == null ? null : this.result.getHitBlockFace().name();
    }

    /**
     * Returns the underlying Bukkit result.
     */
    RayTraceResult unwrap() { return this.result; }
}
