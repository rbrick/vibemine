package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Factory for JavaScript-safe runtime wrappers.
 * <p>Wrappers are intentionally not cached because Bukkit/Paper objects such as
 * players can become stale across logout/login cycles.</p>
 */
public final class VibeRuntimeWrappers {
    private VibeRuntimeWrappers() {}

    /**
     * Returns a wrapper for a world.
     */
    public static VibeWorld world(World world) {
        return world == null ? null : new VibeWorld(world);
    }

    /**
     * Returns a wrapper for a player.
     */
    public static VibePlayer player(Player player) {
        return player == null ? null : new VibePlayer(player);
    }

    /**
     * Returns a wrapper for an entity.
     */
    public static VibeEntity entity(Entity entity) {
        return entity == null ? null : new VibeEntity(entity);
    }

    /**
     * Returns a wrapper for a block.
     */
    public static VibeBlock block(Block block) {
        return block == null ? null : new VibeBlock(block);
    }

    /**
     * Returns a wrapper for an inventory.
     */
    public static VibeInventory inventory(Inventory inventory) {
        return inventory == null ? null : new VibeInventory(inventory);
    }

    /**
     * Wraps a command sender.
     */
    public static VibeSender sender(CommandSender sender) {
        if (sender instanceof Player player) return player(player);
        return new VibeSender(sender);
    }
}
