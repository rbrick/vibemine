package io.rcw.vibemine.ai.plugin.runtime.permissions;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.runtime.VibePlayer;
import io.rcw.vibemine.ai.plugin.runtime.VibeSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.graalvm.polyglot.Value;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JavaScript-safe permission helper facade.
 */
public final class VibePermissions {
    private static final Map<UUID, PermissionAttachment> ATTACHMENTS = new ConcurrentHashMap<>();

    /**
     * Returns whether a sender has a permission node.
     */
    public boolean has(VibeSender sender, String permission) {
        return sender != null && sender.hasPermission(permission);
    }

    /**
     * Throws a {@link SecurityException} if the sender lacks a permission node.
     */
    public void require(VibeSender sender, String permission) {
        if (!has(sender, permission)) {
            throw new SecurityException("Missing permission: " + permission);
        }
    }

    /**
     * Sends a denial message and returns {@code false} if the sender lacks a permission node.
     */
    public boolean check(VibeSender sender, String permission) {
        if (has(sender, permission)) return true;
        if (sender != null) sender.sendMessage("&cYou do not have permission: " + permission);
        return false;
    }

    /**
     * Grants or denies a permission node for a player for as long as VibeMine is loaded.
     * Pass {@code true} to grant and {@code false} to explicitly deny.
     */
    public void set(VibePlayer player, String permission, boolean value) {
        if (player == null) throw new IllegalArgumentException("Player cannot be null");
        if (permission == null || permission.isBlank()) throw new IllegalArgumentException("Permission cannot be blank");
        Player bukkitPlayer = bukkitPlayer(player);
        attachment(bukkitPlayer).setPermission(permission, value);
        bukkitPlayer.recalculatePermissions();
    }

    /**
     * Alias for {@link #set(VibePlayer, String, boolean)}.
     */
    public void setPermission(VibePlayer player, String permission, boolean value) {
        set(player, permission, value);
    }

    /**
     * Grants a permission node to a player.
     */
    public void grant(VibePlayer player, String permission) {
        set(player, permission, true);
    }

    /**
     * Explicitly denies a permission node to a player.
     */
    public void deny(VibePlayer player, String permission) {
        set(player, permission, false);
    }

    /**
     * Sets many permission nodes on a player without clearing existing values first.
     * Accepts a JavaScript object like {@code {"plugin.use": true, "plugin.admin": false}}.
     */
    public void setMany(VibePlayer player, Map<String, Object> permissions) {
        if (permissions == null) throw new IllegalArgumentException("Permissions cannot be null");
        Player bukkitPlayer = bukkitPlayer(player);
        PermissionAttachment attachment = attachment(bukkitPlayer);
        permissions.forEach((permission, value) -> {
            if (permission == null || permission.isBlank()) throw new IllegalArgumentException("Permission cannot be blank");
            attachment.setPermission(permission, asBoolean(value));
        });
        bukkitPlayer.recalculatePermissions();
    }

    /**
     * Sets many permission nodes from a JavaScript object without clearing existing values first.
     */
    public void setMany(VibePlayer player, Value permissions) {
        setMany(player, valueToMap(permissions));
    }

    /**
     * Clears permissions previously set through this facade, then applies a new permission map.
     */
    public void clearAndSet(VibePlayer player, Map<String, Object> permissions) {
        clear(player);
        setMany(player, permissions);
    }

    /**
     * Clears permissions previously set through this facade, then applies a JavaScript object.
     */
    public void clearAndSet(VibePlayer player, Value permissions) {
        clearAndSet(player, valueToMap(permissions));
    }

    /**
     * Alias for {@link #clearAndSet(VibePlayer, Map)}.
     */
    public void replace(VibePlayer player, Map<String, Object> permissions) {
        clearAndSet(player, permissions);
    }

    /**
     * Alias for {@link #clearAndSet(VibePlayer, Value)}.
     */
    public void replace(VibePlayer player, Value permissions) {
        clearAndSet(player, permissions);
    }

    /**
     * Applies a wildcard-like permission value. The wildcard node itself is set, and any currently
     * registered permissions matching the wildcard prefix are also set.
     * Example: {@code applyWildcard(player, "myplugin.*", true)}.
     */
    public Map<String, Boolean> applyWildcard(VibePlayer player, String wildcard, boolean value) {
        if (wildcard == null || wildcard.isBlank()) throw new IllegalArgumentException("Wildcard cannot be blank");
        String prefix = wildcard.endsWith("*") ? wildcard.substring(0, wildcard.length() - 1) : wildcard;
        Map<String, Boolean> applied = new LinkedHashMap<>();
        Player bukkitPlayer = bukkitPlayer(player);
        PermissionAttachment attachment = attachment(bukkitPlayer);

        attachment.setPermission(wildcard, value);
        applied.put(wildcard, value);

        for (Permission permission : Bukkit.getPluginManager().getPermissions()) {
            String name = permission.getName();
            if (name.startsWith(prefix)) {
                attachment.setPermission(name, value);
                applied.put(name, value);
            }
        }

        bukkitPlayer.recalculatePermissions();
        return applied;
    }

    /**
     * Removes this facade's grant/deny value for a permission node on a player.
     */
    public void unset(VibePlayer player, String permission) {
        if (player == null) throw new IllegalArgumentException("Player cannot be null");
        Player bukkitPlayer = bukkitPlayer(player);
        PermissionAttachment attachment = ATTACHMENTS.get(bukkitPlayer.getUniqueId());
        if (attachment == null) return;
        attachment.unsetPermission(permission);
        bukkitPlayer.recalculatePermissions();
    }

    /**
     * Alias for {@link #unset(VibePlayer, String)}.
     */
    public void unsetPermission(VibePlayer player, String permission) {
        unset(player, permission);
    }

    /**
     * Removes all permission values set through this facade for a player.
     */
    public void clear(VibePlayer player) {
        if (player == null) throw new IllegalArgumentException("Player cannot be null");
        Player bukkitPlayer = bukkitPlayer(player);
        PermissionAttachment attachment = ATTACHMENTS.remove(bukkitPlayer.getUniqueId());
        if (attachment == null) return;
        try {
            bukkitPlayer.removeAttachment(attachment);
        } catch (IllegalArgumentException ignored) {
            // Attachment was already removed by Bukkit/plugin lifecycle.
        }
        bukkitPlayer.recalculatePermissions();
    }

    private PermissionAttachment attachment(Player bukkitPlayer) {
        return ATTACHMENTS.computeIfAbsent(bukkitPlayer.getUniqueId(), ignored -> bukkitPlayer.addAttachment(Vibemine.getInstance()));
    }

    private Map<String, Object> valueToMap(Value value) {
        if (value == null || value.isNull()) throw new IllegalArgumentException("Permissions cannot be null");
        if (!value.hasMembers()) throw new IllegalArgumentException("Permissions must be an object/map");
        Map<String, Object> permissions = new LinkedHashMap<>();
        for (String key : value.getMemberKeys()) {
            permissions.put(key, value.getMember(key));
        }
        return permissions;
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isBoolean()) return polyglotValue.asBoolean();
            if (polyglotValue.isString()) return Boolean.parseBoolean(polyglotValue.asString());
            if (polyglotValue.fitsInInt()) return polyglotValue.asInt() != 0;
        }
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof String stringValue) return Boolean.parseBoolean(stringValue);
        if (value instanceof Number numberValue) return numberValue.intValue() != 0;
        throw new IllegalArgumentException("Permission value must be boolean-like: " + value);
    }

    private Player bukkitPlayer(VibePlayer player) {
        if (player == null) throw new IllegalArgumentException("Player cannot be null");
        Player bukkitPlayer = Bukkit.getPlayer(UUID.fromString(player.getId()));
        if (bukkitPlayer == null) throw new IllegalArgumentException("Player is not online: " + player.getId());
        return bukkitPlayer;
    }
}
