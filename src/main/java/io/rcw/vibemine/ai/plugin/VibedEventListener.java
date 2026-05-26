package io.rcw.vibemine.ai.plugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

final class VibedEventListener implements Listener {
    private final VibedPluginManager plugins;

    VibedEventListener(VibedPluginManager plugins) {
        this.plugins = plugins;
    }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) { plugins.dispatch("player_join", event); }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) { plugins.dispatch("player_quit", event); }
    @EventHandler public void onPlayerInteract(PlayerInteractEvent event) { plugins.dispatch("player_interact", event); }
    @EventHandler public void onPlayerMove(PlayerMoveEvent event) { plugins.dispatch("player_move", event); }
    @EventHandler public void onBlockBreak(BlockBreakEvent event) { plugins.dispatch("block_break", event); }
    @EventHandler public void onBlockPlace(BlockPlaceEvent event) { plugins.dispatch("block_place", event); }
    @EventHandler public void onEntityDamageByEntity(EntityDamageByEntityEvent event) { plugins.dispatch("entity_damage_by_entity", event); }
    @EventHandler public void onPlayerDeath(PlayerDeathEvent event) { plugins.dispatch("player_death", event); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { plugins.dispatch("inventory_click", event); }
    @EventHandler public void onAsyncChat(AsyncChatEvent event) { plugins.dispatch("async_chat", event); }
}
