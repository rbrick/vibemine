package io.rcw.vibemine.ai.plugin.runtime;

import io.rcw.vibemine.ai.plugin.runtime.permissions.VibePermissions;
import io.rcw.vibemine.ai.plugin.runtime.scheduler.VibeScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.Value;

/**
 * JavaScript-safe wrapper/binding for Vibe RuntimeBindings functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeRuntimeBindings {
    private VibeRuntimeBindings() {}

    /**
     * JavaScript binding for {@code install}.
     */
    public static void install(Value bindings) {
        bindings.putMember("server", new VibeServer());
        bindings.putMember("inventories", VibeInventories.class);
        bindings.putMember("permissions", new VibePermissions());
        bindings.putMember("scheduler", new VibeScheduler());
    }

    /**
     * JavaScript binding for {@code install}.
     */
    public static void install(Value bindings, CommandSender sender) {
        install(bindings);
        bindings.putMember("sender", VibeRuntimeCache.sender(sender));
        if (sender instanceof Player player) {
            bindings.putMember("player", VibeRuntimeCache.player(player));
            bindings.putMember("world", VibeRuntimeCache.world(player.getWorld()));
        }
    }
}
