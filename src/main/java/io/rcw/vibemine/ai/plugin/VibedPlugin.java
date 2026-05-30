package io.rcw.vibemine.ai.plugin;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.runtime.VibeRuntimeBindings;
import io.rcw.vibemine.ai.plugin.runtime.scheduler.VibeScheduler;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;
import org.bukkit.Bukkit;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.Event;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VibedPlugin {
    private final Vibemine plugin;
    private final VibedPluginSchema schema;
    private final Map<String, VibedEvent> events = new ConcurrentHashMap<>();
    private final List<VibedCommand> commands;
    private Context context;
    private Value state;
    private VibeScheduler scheduler;

    public VibedPlugin(Vibemine plugin, VibedPluginSchema schema) {
        this.plugin = plugin;
        this.schema = schema;
        this.commands = safeList(schema.commands()).stream()
                .map(command -> new VibedCommand(this, command.label(), command.permission(), command.code()))
                .toList();
        safeList(schema.events()).forEach(event -> events.put(VibedPluginManager.normalizeEventName(event.event()), new VibedEvent(this, event.event(), event.code())));
    }

    public void enable() {
        context = Context.newBuilder("js")
                .allowHostAccess(safeHostAccess())
                .allowHostClassLookup(name -> false)
                .build();
        scheduler = new VibeScheduler();
        VibeRuntimeBindings.install(context.getBindings("js"), scheduler, name(), plugin.getVibedPluginManager());
        String globals = schema.globals() == null || schema.globals().isBlank() ? "(function() { return {}; })" : schema.globals();
        validateJavaScript(globals);
        state = context.eval("js", globals).execute();
    }

    public void disable() {
        if (scheduler != null) {
            scheduler.cancelAll();
            scheduler = null;
        }
        if (context != null) {
            context.close(true);
            context = null;
        }
    }

    public String name() { return VibedPluginManager.normalizeName(schema.name()); }
    public String description() { return schema.description() == null ? "" : schema.description(); }
    public List<VibedCommand> commands() { return commands; }
    public List<VibedEvent> events() { return List.copyOf(events.values()); }
    public List<String> imports() { return safeList(schema.imports()).stream().map(VibedPluginManager::normalizeName).toList(); }
    public java.util.Set<String> exportNames() { return schema.exports() == null ? java.util.Set.of() : java.util.Set.copyOf(schema.exports().keySet()); }
    public java.util.Set<String> pluginEventNames() { return schema.pluginEvents() == null ? java.util.Set.of() : java.util.Set.copyOf(schema.pluginEvents().keySet()); }

    public Object callExport(String exportName, Object[] args) {
        if (schema.exports() == null || !schema.exports().containsKey(exportName)) {
            throw new IllegalArgumentException("Plugin '" + name() + "' does not export '" + exportName + "'");
        }
        Object[] withState = java.util.Arrays.copyOf(args, args.length + 1);
        withState[args.length] = state;
        Value result = evalFunction(schema.exports().get(exportName)).execute(withState);
        return result == null || result.isNull() ? null : result.as(Object.class);
    }

    public void executePluginEvent(String eventName, Object payload, String sourcePlugin) {
        if (schema.pluginEvents() == null || !schema.pluginEvents().containsKey(eventName)) return;
        runOnMainThread(() -> evalFunction(schema.pluginEvents().get(eventName)).execute(payload, sourcePlugin, state));
    }

    public void executeCommand(String label, org.bukkit.command.CommandSender sender, String[] args) {
        VibedCommand command = commands.stream().filter(candidate -> candidate.label().equals(label)).findFirst().orElse(null);
        if (command == null) return;
        runOnMainThread(() -> command.executeSource(sender, args, state));
    }

    public void executeEvent(String eventName, Event event) {
        String normalizedEventName = VibedPluginManager.normalizeEventName(eventName);
        VibedEvent vibedEvent = events.get(normalizedEventName);
        if (vibedEvent == null) {
            plugin.getLogger().info("Vibed plugin '" + name() + "' has no handler for event '" + normalizedEventName + "'. Registered: " + events.keySet());
            return;
        }
        plugin.getLogger().info("Vibed plugin '" + name() + "' scheduling event '" + normalizedEventName + "' from " + event.getClass().getSimpleName());
        Runnable runnable = () -> {
            plugin.getLogger().info("Vibed plugin '" + name() + "' running event '" + normalizedEventName + "'");
            vibedEvent.executeSource(event, state);
            plugin.getLogger().info("Vibed plugin '" + name() + "' finished event '" + normalizedEventName + "'");
        };

        // AsyncChatEvent decisions (cancel/message/renderer) must be made before Paper
        // continues rendering the chat message. Run the JS on the main thread but block
        // this async event thread until it finishes so cancellation/formatting applies.
        if (event instanceof AsyncChatEvent) runOnMainThreadAndWait(runnable, event);
        else runOnMainThread(runnable, event);
    }

    Value evalFunction(String sourceCode) {
        if (context == null) throw new IllegalStateException("Plugin is not enabled");
        validateJavaScript(sourceCode);
        return context.eval("js", sourceCode);
    }

    private void validateJavaScript(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) throw new IllegalArgumentException("Missing JavaScript source");
        if (sourceCode.contains("Polyglot.eval")) {
            throw new IllegalArgumentException("Generated JavaScript must not call Polyglot.eval or request non-JS languages such as regex");
        }
    }

    private HostAccess safeHostAccess() {
        return HostAccess.newBuilder(HostAccess.ALL)
                .denyAccess(Class.class)
                .denyAccess(ClassLoader.class)
                .build();
    }

    private void runOnMainThread(Runnable runnable) {
        runOnMainThread(runnable, null);
    }

    private void runOnMainThread(Runnable runnable, Event event) {
        Runnable guarded = guarded(runnable, event);
        if (Bukkit.isPrimaryThread()) guarded.run();
        else Bukkit.getScheduler().runTask(plugin, guarded);
    }

    private void runOnMainThreadAndWait(Runnable runnable, Event event) {
        Runnable guarded = guarded(runnable, event);
        if (Bukkit.isPrimaryThread()) {
            guarded.run();
            return;
        }

        CompletableFuture<Void> complete = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                guarded.run();
                complete.complete(null);
            } catch (Throwable throwable) {
                complete.completeExceptionally(throwable);
            }
        });
        complete.join();
    }

    private Runnable guarded(Runnable runnable, Event event) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Vibed plugin '" + name() + "' failed", exception);
                notifyEventPlayer(event, exception);
            }
        };
    }

    private void notifyEventPlayer(Event event, Exception exception) {
        if (event instanceof org.bukkit.event.player.PlayerEvent playerEvent) {
            playerEvent.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                    "Vibed plugin '" + name() + "' event failed: " + exception.getMessage(),
                    net.kyori.adventure.text.format.NamedTextColor.RED
            ));
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
