package io.rcw.vibemine.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** A command sender that keeps the caller's identity and routes command replies to an interceptor. */
public final class VibeCommandSender implements CommandSender {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final CommandSender delegate;
    private final Consumer<Component> messages;

    public VibeCommandSender(CommandSender delegate, Consumer<Component> messages) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void sendMessage(String message) {
        messages.accept(LEGACY.deserialize(message));
    }

    @Override
    public void sendMessage(String... messages) {
        for (String message : messages) sendMessage(message);
    }

    @Override
    public void sendMessage(UUID sender, String message) {
        sendMessage(message);
    }

    @Override
    public void sendMessage(UUID sender, String... messages) {
        sendMessage(messages);
    }

    @Override
    public void sendMessage(Component message) {
        messages.accept(message);
    }

    @Override public Server getServer() { return delegate.getServer(); }
    @Override public String getName() { return delegate.getName(); }
    @Override public Component name() { return delegate.name(); }
    @Override public Spigot spigot() { return delegate.spigot(); }
    @Override public boolean isOp() { return delegate.isOp(); }
    @Override public void setOp(boolean value) { delegate.setOp(value); }
    @Override public boolean isPermissionSet(String name) { return delegate.isPermissionSet(name); }
    @Override public boolean isPermissionSet(Permission permission) { return delegate.isPermissionSet(permission); }
    @Override public boolean hasPermission(String name) { return delegate.hasPermission(name); }
    @Override public boolean hasPermission(Permission permission) { return delegate.hasPermission(permission); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return delegate.addAttachment(plugin, name, value); }
    @Override public PermissionAttachment addAttachment(Plugin plugin) { return delegate.addAttachment(plugin); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return delegate.addAttachment(plugin, name, value, ticks); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return delegate.addAttachment(plugin, ticks); }
    @Override public void removeAttachment(PermissionAttachment attachment) { delegate.removeAttachment(attachment); }
    @Override public void recalculatePermissions() { delegate.recalculatePermissions(); }
    @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return delegate.getEffectivePermissions(); }
}
