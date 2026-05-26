package io.rcw.vibemine.ai.plugin;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.runtime.VibeRuntimeBindings;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VibedPlugin {
    private final Vibemine plugin;
    private final VibedPluginSchema schema;
    private final Map<String, VibedEvent> events = new ConcurrentHashMap<>();
    private final List<VibedCommand> commands;
    private Context context;
    private Value state;

    public VibedPlugin(Vibemine plugin, VibedPluginSchema schema) {
        this.plugin = plugin;
        this.schema = schema;
        this.commands = safeList(schema.commands()).stream()
                .map(command -> new VibedCommand(this, command.label(), command.permission(), command.code()))
                .toList();
        safeList(schema.events()).forEach(event -> events.put(normalize(event.event()), new VibedEvent(this, event.event(), event.code())));
    }

    public void enable() {
        context = Context.newBuilder("js")
                .allowHostAccess(safeHostAccess())
                .allowHostClassLookup(name -> false)
                .build();
        VibeRuntimeBindings.install(context.getBindings("js"), name());
        state = context.eval("js", schema.globals() == null || schema.globals().isBlank() ? "(function() { return {}; })" : schema.globals()).execute();
    }

    public void disable() {
        if (context != null) {
            context.close(true);
            context = null;
        }
    }

    public String name() { return VibedPluginManager.normalizeName(schema.name()); }
    public String description() { return schema.description() == null ? "" : schema.description(); }
    public List<VibedCommand> commands() { return commands; }
    public List<VibedEvent> events() { return List.copyOf(events.values()); }

    public void executeCommand(String label, org.bukkit.command.CommandSender sender, String[] args) {
        VibedCommand command = commands.stream().filter(candidate -> candidate.label().equals(label)).findFirst().orElse(null);
        if (command == null) return;
        runOnMainThread(() -> command.executeSource(sender, args, state));
    }

    public void executeEvent(String eventName, Event event) {
        VibedEvent vibedEvent = events.get(normalize(eventName));
        if (vibedEvent == null) return;
        runOnMainThread(() -> vibedEvent.executeSource(event, state));
    }

    Value evalFunction(String sourceCode) {
        if (context == null) throw new IllegalStateException("Plugin is not enabled");
        return context.eval("js", sourceCode);
    }

    private HostAccess safeHostAccess() {
        return HostAccess.newBuilder(HostAccess.ALL)
                .denyAccess(Class.class)
                .denyAccess(ClassLoader.class)
                .build();
    }

    private void runOnMainThread(Runnable runnable) {
        Runnable guarded = () -> {
            try {
                runnable.run();
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Vibed plugin '" + name() + "' failed", exception);
            }
        };
        if (Bukkit.isPrimaryThread()) guarded.run();
        else Bukkit.getScheduler().runTask(plugin, guarded);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
