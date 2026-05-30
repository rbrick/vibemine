package io.rcw.vibemine.ai.plugin.runtime;

import io.rcw.vibemine.ai.plugin.runtime.raytrace.VibeRayTraceResult;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * JavaScript-safe wrapper/binding for Vibe Entity functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public class VibeEntity {
    protected final Entity entity;

    /**
     * JavaScript binding for {@code VibeEntity}.
     */
    public VibeEntity(Entity entity) {
        this.entity = entity;
    }

    /**
     * JavaScript binding for {@code getId}.
     */
    public String getId() { return this.entity.getUniqueId().toString(); }
    /**
     * JavaScript binding for {@code getType}.
     */
    public String getType() { return this.entity.getType().name(); }
    /**
     * JavaScript binding for {@code getName}.
     */
    public String getName() { return this.entity.getName(); }
    /**
     * JavaScript binding for {@code getLocation}.
     */
    public VibeLocation getLocation() { return new VibeLocation(this.entity.getLocation()); }
    /**
     * JavaScript binding for {@code getWorld}.
     */
    public VibeWorld getWorld() { return VibeRuntimeWrappers.world(this.entity.getWorld()); }
    /**
     * JavaScript binding for {@code isDead}.
     */
    public boolean isDead() { return this.entity.isDead(); }
    /**
     * JavaScript binding for {@code remove}.
     */
    public void remove() { this.entity.remove(); }
    /**
     * JavaScript binding for {@code teleport}.
     */
    public void teleport(VibeLocation location) { this.entity.teleport(location.unwrap()); }

    /**
     * Ray traces blocks from this entity's eyes if living, otherwise from its location.
     */
    public VibeRayTraceResult rayTraceBlocks(double maxDistance) {
        if (this.entity instanceof LivingEntity living) {
            return new VibeRayTraceResult(living.rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER));
        }
        var location = this.entity.getLocation();
        return new VibeRayTraceResult(this.entity.getWorld().rayTraceBlocks(location, location.getDirection(), maxDistance, FluidCollisionMode.NEVER, true));
    }

    /**
     * Ray traces entities from this entity's eyes if living, otherwise from its location.
     */
    public VibeRayTraceResult rayTraceEntities(double maxDistance) {
        var location = this.entity instanceof LivingEntity living ? living.getEyeLocation() : this.entity.getLocation();
        return new VibeRayTraceResult(this.entity.getWorld().rayTraceEntities(location, location.getDirection(), maxDistance, entity -> !entity.equals(this.entity)));
    }

    /**
     * Ray traces blocks and entities, returning the nearest hit.
     */
    public VibeRayTraceResult rayTrace(double maxDistance) {
        var location = this.entity instanceof LivingEntity living ? living.getEyeLocation() : this.entity.getLocation();
        return new VibeRayTraceResult(this.entity.getWorld().rayTrace(location, location.getDirection(), maxDistance, FluidCollisionMode.NEVER, true, 0.25, entity -> !entity.equals(this.entity)));
    }

    /**
     * JavaScript binding for {@code isLiving}.
     */
    public boolean isLiving() { return this.entity instanceof LivingEntity; }
    /**
     * JavaScript binding for {@code getHealth}.
     */
    public double getHealth() {
        if (this.entity instanceof LivingEntity living) return living.getHealth();
        return 0.0;
    }
    /**
     * JavaScript binding for {@code damage}.
     */
    public void damage(double amount) {
        if (this.entity instanceof LivingEntity living) living.damage(amount);
    }

    /**
     * JavaScript binding for {@code getItemInHand}.
     */
    public VibeItem getItemInHand() { return getItemInMainHand(); }
    /**
     * JavaScript binding for {@code getItemInMainHand}.
     */
    public VibeItem getItemInMainHand() {
        if (this.entity instanceof LivingEntity living && living.getEquipment() != null) {
            return new VibeItem(living.getEquipment().getItemInMainHand());
        }
        return null;
    }
    /**
     * JavaScript binding for {@code getItemInOffHand}.
     */
    public VibeItem getItemInOffHand() {
        if (this.entity instanceof LivingEntity living && living.getEquipment() != null) {
            return new VibeItem(living.getEquipment().getItemInOffHand());
        }
        return null;
    }
    /**
     * JavaScript binding for {@code setItemInHand}.
     */
    public void setItemInHand(VibeItem item) { setItemInMainHand(item); }
    /**
     * JavaScript binding for {@code setItemInMainHand}.
     */
    public void setItemInMainHand(VibeItem item) {
        if (this.entity instanceof LivingEntity living && living.getEquipment() != null) {
            living.getEquipment().setItemInMainHand(item.unwrap());
        }
    }
    /**
     * JavaScript binding for {@code setItemInOffHand}.
     */
    public void setItemInOffHand(VibeItem item) {
        if (this.entity instanceof LivingEntity living && living.getEquipment() != null) {
            living.getEquipment().setItemInOffHand(item.unwrap());
        }
    }

    /**
     * JavaScript binding for {@code unwrap}.
     */
    Entity unwrap() { return this.entity; }
}
