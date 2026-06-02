package io.rcw.vibemine.ai.plugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.*;
import org.bukkit.event.weather.*;
import org.bukkit.event.world.*;

final class VibedEventListener implements Listener {
    private final VibedPluginManager plugins;

    VibedEventListener(VibedPluginManager plugins) {
        this.plugins = plugins;
    }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) { dispatch("player_join", event); }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) { dispatch("player_quit", event); }
    @EventHandler public void onPlayerKick(PlayerKickEvent event) { dispatch("player_kick", event); }
    @EventHandler public void onPlayerLogin(PlayerLoginEvent event) { dispatch("player_login", event); }
    @EventHandler public void onPlayerRespawn(PlayerRespawnEvent event) { dispatch("player_respawn", event); }
    @EventHandler public void onPlayerChangedWorld(PlayerChangedWorldEvent event) { dispatch("player_changed_world", event); }
    @EventHandler public void onPlayerTeleport(PlayerTeleportEvent event) { dispatch("player_teleport", event); }
    @EventHandler public void onPlayerInteract(PlayerInteractEvent event) { dispatch("player_interact", event); }
    @EventHandler public void onPlayerInteractEntity(PlayerInteractEntityEvent event) { dispatch("player_interact_entity", event); }
    @EventHandler public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) { dispatch("player_interact_at_entity", event); }
    @EventHandler public void onPlayerMove(PlayerMoveEvent event) { dispatch("player_move", event); }
    @EventHandler public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) { dispatch("player_command_preprocess", event); }
    @EventHandler public void onPlayerDropItem(PlayerDropItemEvent event) { dispatch("player_drop_item", event); }
    @EventHandler public void onPlayerAttemptPickupItem(PlayerAttemptPickupItemEvent event) { dispatch("player_attempt_pickup_item", event); }
    @EventHandler public void onPlayerItemConsume(PlayerItemConsumeEvent event) { dispatch("player_item_consume", event); }
    @EventHandler public void onPlayerItemBreak(PlayerItemBreakEvent event) { dispatch("player_item_break", event); }
    @EventHandler public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) { dispatch("player_game_mode_change", event); }
    @EventHandler public void onPlayerToggleSneak(PlayerToggleSneakEvent event) { dispatch("player_toggle_sneak", event); }
    @EventHandler public void onPlayerToggleSprint(PlayerToggleSprintEvent event) { dispatch("player_toggle_sprint", event); }
    @EventHandler public void onPlayerToggleFlight(PlayerToggleFlightEvent event) { dispatch("player_toggle_flight", event); }
    @EventHandler public void onPlayerBedEnter(PlayerBedEnterEvent event) { dispatch("player_bed_enter", event); }
    @EventHandler public void onPlayerBedLeave(PlayerBedLeaveEvent event) { dispatch("player_bed_leave", event); }
    @EventHandler public void onPlayerDeath(PlayerDeathEvent event) { dispatch("player_death", event); }
    @EventHandler public void onAsyncChat(AsyncChatEvent event) { dispatch("async_chat", event); }

    @EventHandler public void onBlockBreak(BlockBreakEvent event) { dispatch("block_break", event); }
    @EventHandler public void onBlockPlace(BlockPlaceEvent event) { dispatch("block_place", event); }
    @EventHandler public void onBlockDamage(BlockDamageEvent event) { dispatch("block_damage", event); }
    @EventHandler public void onBlockBurn(BlockBurnEvent event) { dispatch("block_burn", event); }
    @EventHandler public void onBlockExplode(BlockExplodeEvent event) { dispatch("block_explode", event); }
    @EventHandler public void onBlockFade(BlockFadeEvent event) { dispatch("block_fade", event); }
    @EventHandler public void onBlockForm(BlockFormEvent event) { dispatch("block_form", event); }
    @EventHandler public void onBlockGrow(BlockGrowEvent event) { dispatch("block_grow", event); }
    @EventHandler public void onBlockIgnite(BlockIgniteEvent event) { dispatch("block_ignite", event); }
    @EventHandler public void onBlockPhysics(BlockPhysicsEvent event) { dispatch("block_physics", event); }
    @EventHandler public void onBlockPistonExtend(BlockPistonExtendEvent event) { dispatch("block_piston_extend", event); }
    @EventHandler public void onBlockPistonRetract(BlockPistonRetractEvent event) { dispatch("block_piston_retract", event); }
    @EventHandler public void onBlockRedstone(BlockRedstoneEvent event) { dispatch("block_redstone", event); }
    @EventHandler public void onLeavesDecay(LeavesDecayEvent event) { dispatch("leaves_decay", event); }
    @EventHandler public void onSignChange(SignChangeEvent event) { dispatch("sign_change", event); }

    @EventHandler public void onEntityDamage(EntityDamageEvent event) { dispatch("entity_damage", event); }
    @EventHandler public void onEntityDamageByEntity(EntityDamageByEntityEvent event) { dispatch("entity_damage_by_entity", event); }
    @EventHandler public void onEntityDeath(EntityDeathEvent event) { dispatch("entity_death", event); }
    @EventHandler public void onEntityExplode(EntityExplodeEvent event) { dispatch("entity_explode", event); }
    @EventHandler public void onEntitySpawn(EntitySpawnEvent event) { dispatch("entity_spawn", event); }
    @EventHandler public void onCreatureSpawn(CreatureSpawnEvent event) { dispatch("creature_spawn", event); }
    @EventHandler public void onEntityTarget(EntityTargetEvent event) { dispatch("entity_target", event); }
    @EventHandler public void onEntityTeleport(EntityTeleportEvent event) { dispatch("entity_teleport", event); }
    @EventHandler public void onEntityChangeBlock(EntityChangeBlockEvent event) { dispatch("entity_change_block", event); }
    @EventHandler public void onEntityCombust(EntityCombustEvent event) { dispatch("entity_combust", event); }
    @EventHandler public void onEntityPickupItem(EntityPickupItemEvent event) { dispatch("entity_pickup_item", event); }
    @EventHandler public void onFoodLevelChange(FoodLevelChangeEvent event) { dispatch("food_level_change", event); }
    @EventHandler public void onProjectileHit(ProjectileHitEvent event) { dispatch("projectile_hit", event); }
    @EventHandler public void onProjectileLaunch(ProjectileLaunchEvent event) { dispatch("projectile_launch", event); }

    @EventHandler public void onInventoryOpen(InventoryOpenEvent event) { dispatch("inventory_open", event); }
    @EventHandler public void onInventoryClose(InventoryCloseEvent event) { dispatch("inventory_close", event); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { dispatch("inventory_click", event); }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) { dispatch("inventory_drag", event); }
    @EventHandler public void onCraftItem(CraftItemEvent event) { dispatch("craft_item", event); }
    @EventHandler public void onFurnaceSmelt(FurnaceSmeltEvent event) { dispatch("furnace_smelt", event); }

    @EventHandler public void onWorldInit(WorldInitEvent event) { dispatch("world_init", event); }
    @EventHandler public void onWorldLoad(WorldLoadEvent event) { dispatch("world_load", event); }
    @EventHandler public void onWorldSave(WorldSaveEvent event) { dispatch("world_save", event); }
    @EventHandler public void onWorldUnload(WorldUnloadEvent event) { dispatch("world_unload", event); }
    @EventHandler public void onChunkLoad(ChunkLoadEvent event) { dispatch("chunk_load", event); }
    @EventHandler public void onChunkUnload(ChunkUnloadEvent event) { dispatch("chunk_unload", event); }
    @EventHandler public void onPortalCreate(PortalCreateEvent event) { dispatch("portal_create", event); }
    @EventHandler public void onSpawnChange(SpawnChangeEvent event) { dispatch("spawn_change", event); }
    @EventHandler public void onStructureGrow(StructureGrowEvent event) { dispatch("structure_grow", event); }
    @EventHandler public void onTimeSkip(TimeSkipEvent event) { dispatch("time_skip", event); }

    @EventHandler public void onWeatherChange(WeatherChangeEvent event) { dispatch("weather_change", event); }
    @EventHandler public void onLightningStrike(LightningStrikeEvent event) { dispatch("lightning_strike", event); }
    @EventHandler public void onThunderChange(ThunderChangeEvent event) { dispatch("thunder_change", event); }

    @EventHandler public void onServerCommand(ServerCommandEvent event) { dispatch("server_command", event); }
    @EventHandler public void onServerListPing(ServerListPingEvent event) { dispatch("server_list_ping", event); }
    @EventHandler public void onServerLoad(ServerLoadEvent event) { dispatch("server_load", event); }

    private void dispatch(String eventName, org.bukkit.event.Event event) {
        debug(eventName);
        plugins.dispatch(eventName, event);
    }


    private void debug(String message) {
        org.bukkit.Bukkit.getLogger().info("[Vibemine event listener] " + message);
    }
}
