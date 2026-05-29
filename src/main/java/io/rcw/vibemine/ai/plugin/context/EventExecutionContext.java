package io.rcw.vibemine.ai.plugin.context;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.rcw.vibemine.ai.plugin.ExecutionContext;
import io.rcw.vibemine.ai.plugin.runtime.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class EventExecutionContext implements ExecutionContext {
    private final String name;
    private final Event event;

    public EventExecutionContext(String name, Event event) {
        this.name = name;
        this.event = event;
    }

    public String getName() {
        return name;
    }

    public boolean isCancellable() {
        return event instanceof Cancellable;
    }

    public boolean isCancelled() {
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    public void setCancelled(boolean cancelled) {
        if (event instanceof Cancellable cancellable) {
            cancellable.setCancelled(cancelled);
        }
    }

    public VibePlayer getPlayer() {
        if (event instanceof PlayerEvent playerEvent) return VibeRuntimeCache.player(playerEvent.getPlayer());
        if (event instanceof BlockBreakEvent blockBreakEvent) return VibeRuntimeCache.player(blockBreakEvent.getPlayer());
        if (event instanceof BlockPlaceEvent blockPlaceEvent) return VibeRuntimeCache.player(blockPlaceEvent.getPlayer());
        return null;
    }

    public VibeBlock getBlock() {
        if (event instanceof BlockEvent blockEvent) return VibeRuntimeCache.block(blockEvent.getBlock());
        if (event instanceof PlayerInteractEvent interactEvent) return VibeRuntimeCache.block(interactEvent.getClickedBlock());
        return null;
    }

    public VibeEntity getEntity() {
        if (event instanceof EntityEvent entityEvent) return VibeRuntimeCache.entity(entityEvent.getEntity());
        return null;
    }

    public VibeEntity getDamager() {
        if (event instanceof EntityDamageByEntityEvent damageEvent) return VibeRuntimeCache.entity(damageEvent.getDamager());
        return null;
    }

    public VibeWorld getWorld() {
        VibePlayer player = getPlayer();
        if (player != null) return player.getWorld();

        VibeBlock block = getBlock();
        if (block != null) return block.getWorld();

        VibeEntity entity = getEntity();
        if (entity != null) return entity.getWorld();

        VibeEntity damager = getDamager();
        if (damager != null) return damager.getWorld();

        return null;
    }

    public String getAction() {
        if (event instanceof PlayerInteractEvent interactEvent) return interactEvent.getAction().name();
        return null;
    }

    public String getHand() {
        if (event instanceof PlayerInteractEvent interactEvent && interactEvent.getHand() != null) return interactEvent.getHand().name();
        return null;
    }

    public boolean isMainHand() {
        if (event instanceof PlayerInteractEvent interactEvent) return interactEvent.getHand() == EquipmentSlot.HAND;
        return true;
    }

    public boolean isOffHand() {
        if (event instanceof PlayerInteractEvent interactEvent) return interactEvent.getHand() == EquipmentSlot.OFF_HAND;
        return false;
    }

    public String getMessage() {
        if (event instanceof AsyncChatEvent chatEvent) return chatEvent.message().toString();
        return null;
    }

}
