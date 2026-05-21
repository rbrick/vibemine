package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Central wrapper cache for stable JavaScript object identity.
 * <p>Use this instead of directly constructing wrappers when returning existing
 * Bukkit/Paper objects from another binding. That way repeated calls like
 * {@code player.getWorld() === server.getWorld("world")} can resolve to the same
 * Java wrapper instance for the same underlying object.</p>
 */
public final class VibeRuntimeCache {
    private static final Map<World, VibeWorld> WORLDS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, VibePlayer> PLAYERS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, VibeEntity> ENTITIES = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Block, VibeBlock> BLOCKS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Inventory, VibeInventory> INVENTORIES = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private VibeRuntimeCache() {}

    /**
     * Returns the canonical wrapper for a world.
     */
    public static VibeWorld world(World world) {
        if (world == null) return null;
        synchronized (WORLDS) {
            return WORLDS.computeIfAbsent(world, VibeWorld::new);
        }
    }

    /**
     * Returns the canonical wrapper for a player.
     */
    public static VibePlayer player(Player player) {
        if (player == null) return null;
        synchronized (PLAYERS) {
            return PLAYERS.computeIfAbsent(player, VibePlayer::new);
        }
    }

    /**
     * Returns the canonical wrapper for an entity.
     */
    public static VibeEntity entity(Entity entity) {
        if (entity == null) return null;
        synchronized (ENTITIES) {
            return ENTITIES.computeIfAbsent(entity, VibeEntity::new);
        }
    }

    /**
     * Returns the canonical wrapper for a block while the underlying block remains referenced.
     */
    public static VibeBlock block(Block block) {
        if (block == null) return null;
        synchronized (BLOCKS) {
            return BLOCKS.computeIfAbsent(block, VibeBlock::new);
        }
    }

    /**
     * Returns the canonical wrapper for an inventory while the underlying inventory remains referenced.
     */
    public static VibeInventory inventory(Inventory inventory) {
        if (inventory == null) return null;
        synchronized (INVENTORIES) {
            return INVENTORIES.computeIfAbsent(inventory, VibeInventory::new);
        }
    }

    /**
     * Wraps a command sender, preserving player singleton identity where possible.
     */
    public static VibeSender sender(CommandSender sender) {
        if (sender instanceof Player player) return player(player);
        return new VibeSender(sender);
    }
}
