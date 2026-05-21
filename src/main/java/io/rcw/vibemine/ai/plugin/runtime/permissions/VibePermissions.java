package io.rcw.vibemine.ai.plugin.runtime.permissions;

import io.rcw.vibemine.ai.plugin.runtime.VibeSender;

/**
 * JavaScript-safe permission helper facade.
 */
public final class VibePermissions {
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
}
