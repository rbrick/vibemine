package io.rcw.vibemine.ai.plugin.context;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.rcw.vibemine.ai.plugin.ExecutionContext;
import io.rcw.vibemine.ai.plugin.runtime.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacy('&');
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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
        if (event instanceof PlayerEvent playerEvent) return VibeRuntimeWrappers.player(playerEvent.getPlayer());
        if (event instanceof BlockBreakEvent blockBreakEvent) return VibeRuntimeWrappers.player(blockBreakEvent.getPlayer());
        if (event instanceof BlockPlaceEvent blockPlaceEvent) return VibeRuntimeWrappers.player(blockPlaceEvent.getPlayer());
        return null;
    }

    public VibeBlock getBlock() {
        if (event instanceof BlockEvent blockEvent) return VibeRuntimeWrappers.block(blockEvent.getBlock());
        if (event instanceof PlayerInteractEvent interactEvent) return VibeRuntimeWrappers.block(interactEvent.getClickedBlock());
        return null;
    }

    public VibeEntity getEntity() {
        if (event instanceof EntityEvent entityEvent) return VibeRuntimeWrappers.entity(entityEvent.getEntity());
        return null;
    }

    public VibeEntity getDamager() {
        if (event instanceof EntityDamageByEntityEvent damageEvent) return VibeRuntimeWrappers.entity(damageEvent.getDamager());
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
        if (event instanceof AsyncChatEvent chatEvent) return PLAIN.serialize(chatEvent.message());
        return null;
    }

    public void setMessage(String message) {
        if (event instanceof AsyncChatEvent chatEvent) chatEvent.message(component(message));
    }

    public void setFormat(String format) {
        if (!(event instanceof AsyncChatEvent chatEvent)) return;
        chatEvent.renderer((source, sourceDisplayName, message, viewer) -> component(format)
                .replaceText(builder -> builder.matchLiteral("{player}").replacement(sourceDisplayName))
                .replaceText(builder -> builder.matchLiteral("{name}").replacement(sourceDisplayName))
                .replaceText(builder -> builder.matchLiteral("{message}").replacement(message))
                .hoverEvent(HoverEvent.showText(Component.text("Sent by " + source.getName()))));
    }

    public void broadcast(String message) {
        if (event instanceof AsyncChatEvent chatEvent) {
            chatEvent.viewers().forEach(viewer -> viewer.sendMessage(component(message)));
        }
    }

    private Component component(String message) {
        return message == null ? Component.empty() : LEGACY.deserialize(message);
    }

}
