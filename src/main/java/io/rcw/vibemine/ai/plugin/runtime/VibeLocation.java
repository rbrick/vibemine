package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * JavaScript-safe wrapper/binding for Vibe Location functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeLocation {
    private final Location location;

    /**
     * JavaScript binding for {@code VibeLocation}.
     */
    public VibeLocation(Location location) {
        this.location = location.clone();
    }

    /**
     * JavaScript binding for {@code getX}.
     */
    public double getX() { return this.location.getX(); }
    /**
     * JavaScript binding for {@code getY}.
     */
    public double getY() { return this.location.getY(); }
    /**
     * JavaScript binding for {@code getZ}.
     */
    public double getZ() { return this.location.getZ(); }
    /**
     * JavaScript binding for {@code getYaw}.
     */
    public float getYaw() { return this.location.getYaw(); }
    /**
     * JavaScript binding for {@code getPitch}.
     */
    public float getPitch() { return this.location.getPitch(); }
    /**
     * JavaScript binding for {@code getWorldName}.
     */
    public String getWorldName() { return this.location.getWorld() == null ? null : this.location.getWorld().getName(); }

    /**
     * JavaScript binding for {@code add}.
     */
    public VibeLocation add(double x, double y, double z) {
        return new VibeLocation(this.location.clone().add(x, y, z));
    }

    /**
     * JavaScript binding for {@code subtract}.
     */
    public VibeLocation subtract(double x, double y, double z) {
        return new VibeLocation(this.location.clone().subtract(x, y, z));
    }

    /**
     * JavaScript binding for {@code distance}.
     */
    public double distance(VibeLocation other) {
        return this.location.distance(other.location);
    }

    /**
     * JavaScript binding for {@code getBlock}.
     */
    public VibeBlock getBlock() {
        return VibeRuntimeCache.block(this.location.getBlock());
    }

    /**
     * JavaScript binding for {@code getWorld}
     */
    public VibeWorld getWorld() {
        return VibeRuntimeCache.world(this.location.getWorld());
    }

    /**
     * JavaScript binding for {@code unwrap}.
     */
    Location unwrap() {
        return this.location.clone();
    }


    /**
     * JavaScript binding for {@code toString}.
     */
    @Override
    public String toString() {
        return "VibeLocation{" + getWorldName() + ", " + getX() + ", " + getY() + ", " + getZ() + "}";
    }
}
