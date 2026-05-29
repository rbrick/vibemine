package io.rcw.vibemine.ai.plugin.runtime;

import io.rcw.vibemine.ai.plugin.integrations.VibeIntegrations;
import io.rcw.vibemine.ai.plugin.runtime.database.VibeKeyStore;
import io.rcw.vibemine.ai.plugin.runtime.permissions.VibePermissions;
import io.rcw.vibemine.ai.plugin.runtime.scheduler.VibeScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        install(bindings, new VibeScheduler());
    }

    public static void install(Value bindings, VibeScheduler scheduler) {
        bindings.putMember("server", new VibeServer());
        bindings.putMember("inventories", new VibeInventories());
        bindings.putMember("permissions", new VibePermissions());
        bindings.putMember("scheduler", scheduler);
        bindings.putMember("database", new VibeKeyStore("global"));
        VibeIntegrations.install(bindings);
    }

    public static void install(Value bindings, VibeScheduler scheduler, String namespace) {
        install(bindings, scheduler);
        bindings.putMember("database", new VibeKeyStore(namespace));
    }

    /**
     * JavaScript binding for {@code install}.
     */
    public static void install(Value bindings, String namespace) {
        install(bindings, new VibeScheduler(), namespace);
    }

    public static void install(Value bindings, CommandSender sender) {
        install(bindings);
        bindings.putMember("sender", VibeRuntimeCache.sender(sender));
        if (sender instanceof Player player) {
            bindings.putMember("player", VibeRuntimeCache.player(player));
            bindings.putMember("world", VibeRuntimeCache.world(player.getWorld()));
        }
    }

    /**
     * JavaScript binding for {@code install} using a scheduler that keeps the polyglot context
     * alive until scheduled callbacks finish (or repeating tasks are cancelled).
     */
    public static VibeScheduler install(Value bindings, CommandSender sender, Context context) {
        var scheduler = new VibeScheduler(context, new AtomicInteger(), new AtomicBoolean(false), new AtomicBoolean(false));
        install(bindings, scheduler);
        bindings.putMember("sender", VibeRuntimeCache.sender(sender));
        if (sender instanceof Player player) {
            bindings.putMember("player", VibeRuntimeCache.player(player));
            bindings.putMember("world", VibeRuntimeCache.world(player.getWorld()));
        }
        return scheduler;
    }
}
