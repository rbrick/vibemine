package io.rcw.vibemine.ai.plugin.runtime;

import io.rcw.vibemine.ai.plugin.runtime.permissions.VibePermissions;
import io.rcw.vibemine.ai.plugin.runtime.raytrace.VibeRayTraceResult;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.Value;

import java.util.Map;

/**
 * JavaScript-safe wrapper/binding for Vibe Player functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibePlayer extends VibeSender {
    private final Player player;

    /**
     * JavaScript binding for {@code VibePlayer}.
     */
    public VibePlayer(Player player) {
        super(player);
        this.player = player;
    }

    /**
     * JavaScript binding for {@code getId}.
     */
    public String getId() { return this.player.getUniqueId().toString(); }
    /**
     * JavaScript binding for {@code getDisplayName}.
     */
    public String getDisplayName() { return this.player.displayName().toString(); }
    /**
     * JavaScript binding for {@code getLocation}.
     */
    public VibeLocation getLocation() { return new VibeLocation(this.player.getLocation()); }
    /**
     * JavaScript binding for {@code getWorld}.
     */
    public VibeWorld getWorld() { return VibeRuntimeCache.world(this.player.getWorld()); }
    /**
     * JavaScript binding for {@code getTargetBlock}.
     */
    public VibeBlock getTargetBlock(int maxDistance) {
        Block block = this.player.getTargetBlockExact(maxDistance);
        return VibeRuntimeCache.block(block);
    }

    /**
     * Ray traces blocks from the player's eyes.
     */
    public VibeRayTraceResult rayTraceBlocks(double maxDistance) {
        return new VibeRayTraceResult(this.player.rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER));
    }

    /**
     * Ray traces entities from the player's eyes.
     */
    public VibeRayTraceResult rayTraceEntities(double maxDistance) {
        var eye = this.player.getEyeLocation();
        return new VibeRayTraceResult(this.player.getWorld().rayTraceEntities(eye, eye.getDirection(), maxDistance, entity -> !entity.equals(this.player)));
    }

    /**
     * Ray traces blocks and entities from the player's eyes, returning the nearest hit.
     */
    public VibeRayTraceResult rayTrace(double maxDistance) {
        var eye = this.player.getEyeLocation();
        return new VibeRayTraceResult(this.player.getWorld().rayTrace(eye, eye.getDirection(), maxDistance, FluidCollisionMode.NEVER, true, 0.25, entity -> !entity.equals(this.player)));
    }

    /**
     * JavaScript binding for {@code setAllowFlight}.
     */
    public void setAllowFlight(boolean allowFlight) {
        this.player.setAllowFlight(allowFlight);
        if (!allowFlight && this.player.isFlying()) this.player.setFlying(false);
    }

    /**
     * JavaScript binding for {@code getAllowFlight}.
     */
    public boolean getAllowFlight() {
        return this.player.getAllowFlight();
    }

    /**
     * JavaScript binding for {@code canFly}.
     */
    public boolean canFly() {
        return getAllowFlight();
    }

    /**
     * Backwards-compatible JavaScript binding for {@code allowedFlight}.
     */
    public boolean allowedFlight() {
        return getAllowFlight();
    }

    /**
     * JavaScript binding for {@code setPermission}.
     */
    public void setPermission(String permission, boolean value) {
        new VibePermissions().set(this, permission, value);
    }

    /**
     * JavaScript binding for {@code grantPermission}.
     */
    public void grantPermission(String permission) {
        setPermission(permission, true);
    }

    /**
     * JavaScript binding for {@code denyPermission}.
     */
    public void denyPermission(String permission) {
        setPermission(permission, false);
    }

    /**
     * JavaScript binding for {@code unsetPermission}.
     */
    public void unsetPermission(String permission) {
        new VibePermissions().unset(this, permission);
    }

    /**
     * JavaScript binding for {@code clearPermissions}.
     */
    public void clearPermissions() {
        new VibePermissions().clear(this);
    }

    /**
     * JavaScript binding for {@code setPermissions}.
     */
    public void setPermissions(Map<String, Object> permissions) {
        new VibePermissions().setMany(this, permissions);
    }

    /**
     * JavaScript binding for {@code setPermissions} with a JavaScript object.
     */
    public void setPermissions(Value permissions) {
        new VibePermissions().setMany(this, permissions);
    }

    /**
     * JavaScript binding for {@code replacePermissions}.
     */
    public void replacePermissions(Map<String, Object> permissions) {
        new VibePermissions().clearAndSet(this, permissions);
    }

    /**
     * JavaScript binding for {@code replacePermissions} with a JavaScript object.
     */
    public void replacePermissions(Value permissions) {
        new VibePermissions().clearAndSet(this, permissions);
    }

    /**
     * JavaScript binding for {@code applyWildcardPermission}.
     */
    public Map<String, Boolean> applyWildcardPermission(String wildcard, boolean value) {
        return new VibePermissions().applyWildcard(this, wildcard, value);
    }

    /**
     * JavaScript binding for {@code setFlying}.
     */
    public void setFlying(boolean flying) {
        if (flying && !this.player.getAllowFlight()) this.player.setAllowFlight(true);
        this.player.setFlying(flying);
    }

    /**
     * JavaScript binding for {@code setFly}.
     */
    public void setFly(boolean fly) {
        setFlying(fly);
    }

    /**
     * JavaScript binding for {@code toggleFlight}.
     */
    public boolean toggleFlight() {
        boolean enabled = !this.player.getAllowFlight();
        setAllowFlight(enabled);
        if (!enabled) this.player.setFlying(false);
        return enabled;
    }

    /**
     * JavaScript binding for {@code isFlying}.
     */
    public boolean isFlying() {
        return this.player.isFlying();
    }

    /**
     * JavaScript binding for {@code setWalkSpeed}.
     */
    public void setWalkSpeed(float speed) {
        this.player.setWalkSpeed(speed);
    }

    /**
     * JavaScript binding for {@code getWalkSpeed}.
     */
    public float getWalkSpeed() {
        return this.player.getWalkSpeed();
    }

    /**
     * JavaScript binding for {@code getHealth}.
     */
    public double getHealth() { return this.player.getHealth(); }
    /**
     * JavaScript binding for {@code setHealth}.
     */
    public void setHealth(double health) { this.player.setHealth(Math.max(0.0, Math.min(health, this.player.getMaxHealth()))); }
    /**
     * JavaScript binding for {@code getFoodLevel}.
     */
    public int getFoodLevel() { return this.player.getFoodLevel(); }
    /**
     * JavaScript binding for {@code setFoodLevel}.
     */
    public void setFoodLevel(int foodLevel) { this.player.setFoodLevel(Math.max(0, Math.min(foodLevel, 20))); }
    /**
     * JavaScript binding for {@code getGameMode}.
     */
    public String getGameMode() { return this.player.getGameMode().name(); }
    /**
     * JavaScript binding for {@code setGameMode}.
     */
    public void setGameMode(String gameMode) { this.player.setGameMode(GameMode.valueOf(gameMode.toUpperCase())); }

    /**
     * JavaScript binding for {@code teleport}.
     */
    public void teleport(VibeLocation location) { this.player.teleport(location.unwrap()); }
    /**
     * JavaScript binding for {@code getInventory}.
     */
    public VibeInventory getInventory() { return VibeRuntimeCache.inventory(this.player.getInventory()); }
    /**
     * JavaScript binding for {@code getEnderChest}.
     */
    public VibeInventory getEnderChest() { return VibeRuntimeCache.inventory(this.player.getEnderChest()); }
    /**
     * JavaScript binding for {@code openInventory}.
     */
    public void openInventory(VibeInventory inventory) { this.player.openInventory(inventory.unwrap()); }
    /**
     * JavaScript binding for {@code closeInventory}.
     */
    public void closeInventory() { this.player.closeInventory(); }

    /**
     * JavaScript binding for {@code getItemInHand}.
     */
    public VibeItem getItemInHand() { return getItemInMainHand(); }
    /**
     * JavaScript binding for {@code getItemInMainHand}.
     */
    public VibeItem getItemInMainHand() { return new VibeItem(this.player.getInventory().getItemInMainHand()); }
    /**
     * JavaScript binding for {@code getItemInOffHand}.
     */
    public VibeItem getItemInOffHand() { return new VibeItem(this.player.getInventory().getItemInOffHand()); }
    /**
     * JavaScript binding for {@code setItemInHand}.
     */
    public void setItemInHand(VibeItem item) { setItemInMainHand(item); }
    /**
     * JavaScript binding for {@code setItemInMainHand}.
     */
    public void setItemInMainHand(VibeItem item) { this.player.getInventory().setItemInMainHand(item.unwrap()); }
    /**
     * JavaScript binding for {@code setItemInOffHand}.
     */
    public void setItemInOffHand(VibeItem item) { this.player.getInventory().setItemInOffHand(item.unwrap()); }

    /**
     * JavaScript binding for {@code getInventoryItem}.
     */
    public VibeItem getInventoryItem(int slot) { return new VibeItem(this.player.getInventory().getItem(slot)); }
    /**
     * JavaScript binding for {@code setInventoryItem}.
     */
    public void setInventoryItem(int slot, VibeItem item) { this.player.getInventory().setItem(slot, item.unwrap()); }
    /**
     * JavaScript binding for {@code getHeldItemSlot}.
     */
    public int getHeldItemSlot() { return this.player.getInventory().getHeldItemSlot(); }
    /**
     * JavaScript binding for {@code setHeldItemSlot}.
     */
    public void setHeldItemSlot(int slot) { this.player.getInventory().setHeldItemSlot(slot); }

    /**
     * JavaScript binding for {@code giveItem}.
     */
    public void giveItem(String material, int amount) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        this.player.getInventory().addItem(new ItemStack(matched, Math.max(1, amount)));
    }

    /**
     * JavaScript binding for {@code giveNamedItem}.
     */
    public void giveNamedItem(String material, int amount, String name) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        VibeItem item = new VibeItem(new ItemStack(matched, Math.max(1, amount)));
        item.setName(name);
        give(item);
    }

    /**
     * JavaScript binding for {@code giveTaggedItem}.
     */
    public void giveTaggedItem(String material, int amount, String name, String key, String value) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        VibeItem item = new VibeItem(new ItemStack(matched, Math.max(1, amount)));
        item.setName(name);
        item.setData(key, value);
        give(item);
    }

    /**
     * JavaScript binding for {@code give}.
     */
    public void give(VibeItem item) { this.player.getInventory().addItem(item.unwrap()); }

    /**
     * JavaScript binding for {@code playSound} at the player's current location.
     */
    public void playSound(String sound, float volume, float pitch) {
        this.player.playSound(this.player.getLocation(), sound, volume, pitch);
    }

    /**
     * JavaScript-friendly overload for numeric volume/pitch values.
     */
    public void playSound(String sound, double volume, double pitch) {
        playSound(sound, (float) volume, (float) pitch);
    }

    /**
     * JavaScript binding for {@code playSound} at the player's current location with default volume/pitch.
     */
    public void playSound(String sound) {
        playSound(sound, 1.0f, 1.0f);
    }

    /**
     * JavaScript binding for {@code playSoundAt}.
     */
    public void playSoundAt(VibeLocation location, String sound, float volume, float pitch) {
        if (location == null) throw new IllegalArgumentException("Location cannot be null");
        this.player.playSound(location.unwrap(), sound, volume, pitch);
    }

    /**
     * JavaScript-friendly overload for numeric volume/pitch values.
     */
    public void playSoundAt(VibeLocation location, String sound, double volume, double pitch) {
        playSoundAt(location, sound, (float) volume, (float) pitch);
    }

    /**
     * JavaScript binding for {@code playSoundAt} with default volume/pitch.
     */
    public void playSoundAt(VibeLocation location, String sound) {
        playSoundAt(location, sound, 1.0f, 1.0f);
    }

    /**
     * Sends a formatted message to the player's action bar.
     */
    public void sendActionBar(String message) { this.player.sendActionBar(VibeText.component(message)); }

    /**
     * Alias for {@link #sendActionBar(String)}.
     */
    public void actionBar(String message) { sendActionBar(message); }

    /**
     * JavaScript binding for {@code kick}.
     */
    public void kick(String message) { this.player.kick(VibeText.component(message)); }

    /**
     * JavaScript binding for {@code unwrap}.
     */
    @Override
    Player unwrap() { return this.player; }
}
