package io.rcw.vibemine.ai.plugin.context;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.rcw.vibemine.ai.plugin.ExecutionContext;
import io.rcw.vibemine.ai.plugin.runtime.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
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
        setMessage(component(message));
    }

    public void setMessage(Component message) {
        if (event instanceof AsyncChatEvent chatEvent) chatEvent.message(message == null ? Component.empty() : message);
    }

    public void setFormat(String format) {
        setFormat(component(format));
    }

    public void setFormat(Component format) {
        if (!(event instanceof AsyncChatEvent chatEvent)) return;
        Component safeFormat = format == null ? Component.empty() : format;
        chatEvent.renderer((source, sourceDisplayName, message, viewer) -> safeFormat
                .replaceText(builder -> builder.matchLiteral("{player}").replacement(sourceDisplayName))
                .replaceText(builder -> builder.matchLiteral("{name}").replacement(sourceDisplayName))
                .replaceText(builder -> builder.matchLiteral("{message}").replacement(message))
                .hoverEvent(HoverEvent.showText(Component.text("Sent by " + source.getName()))));
    }

    public void broadcast(String message) {
        broadcast(component(message));
    }

    public void broadcast(Component message) {
        if (event instanceof AsyncChatEvent chatEvent) {
            Component safeMessage = message == null ? Component.empty() : message;
            chatEvent.viewers().forEach(viewer -> viewer.sendMessage(safeMessage));
        }
    }

    private Component component(String message) {
        if (message == null) return Component.empty();
        if (message.indexOf('<') >= 0 && message.indexOf('>') > message.indexOf('<')) {
            try {
                return MINI_MESSAGE.deserialize(message);
            } catch (RuntimeException ignored) {
                // Fall back to legacy ampersand formatting below.
            }
        }
        return LEGACY.deserialize(message);
    }

}
