package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.command.CommandSender;

/**
 * JavaScript-safe wrapper/binding for Vibe Sender functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public class VibeSender {
    protected final CommandSender sender;

    /**
     * JavaScript binding for {@code VibeSender}.
     */
    public VibeSender(CommandSender sender) {
        this.sender = sender;
    }

    /**
     * JavaScript binding for {@code getName}.
     */
    public String getName() { return this.sender.getName(); }
    /**
     * JavaScript binding for {@code sendMessage}.
     */
    public void sendMessage(String message) { this.sender.sendMessage(VibeText.component(message)); }
    /**
     * JavaScript binding for {@code hasPermission}.
     */
    public boolean hasPermission(String permission) { return this.sender.hasPermission(permission); }
    /**
     * Sends a denial message and returns false if the sender lacks the permission.
     */
    public boolean checkPermission(String permission) {
        if (hasPermission(permission)) return true;
        sendMessage("&cYou do not have permission: " + permission);
        return false;
    }
    /**
     * Throws if the sender lacks the permission.
     */
    public void requirePermission(String permission) {
        if (!hasPermission(permission)) throw new SecurityException("Missing permission: " + permission);
    }
    /**
     * Returns whether the sender is op.
     */
    public boolean isOp() { return this.sender.isOp(); }
    /**
     * Sets the sender op flag.
     */
    public void setOp(boolean op) { this.sender.setOp(op); }

    /**
     * JavaScript binding for {@code unwrap}.
     */
    public CommandSender unwrap() { return this.sender; }
}
