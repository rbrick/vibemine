package io.rcw.vibemine.ai.plugin;

import com.google.gson.JsonSyntaxException;
import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VibedPluginManager {
    private final Vibemine plugin;
    private final Path pluginsDirectory;
    private final Map<String, VibedPlugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, List<VibedPlugin>> eventIndex = new ConcurrentHashMap<>();
    private final Map<String, Command> registeredCommands = new ConcurrentHashMap<>();
    private VibedEventListener listener;

    public VibedPluginManager(Vibemine plugin) {
        this.plugin = plugin;
        this.pluginsDirectory = plugin.getDataFolder().toPath().resolve("vibed-plugins");
    }

    public void enable() {
        try {
            Files.createDirectories(pluginsDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create vibed plugin directory", exception);
        }
        listener = new VibedEventListener(this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        loadAll();
    }

    public void disable() {
        plugins.values().forEach(VibedPlugin::disable);
        plugins.clear();
        eventIndex.clear();
        registeredCommands.values().forEach(this::unregisterCommand);
        registeredCommands.clear();
        refreshPlayerCommandTrees();
        if (listener != null) HandlerList.unregisterAll(listener);
    }

    public Collection<VibedPlugin> plugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    public void loadAll() {
        try (var paths = Files.list(pluginsDirectory)) {
            paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                try {
                    load(path);
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load vibed plugin " + path.getFileName(), exception);
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to list vibed plugins", exception);
        }
    }

    public VibedPlugin saveAndLoad(String json) throws IOException {
        VibedPluginSchema schema = parseSchema(json);
        String name = normalizeName(schema.name());
        Path path = pluginsDirectory.resolve(name + ".json");
        Path tempPath = pluginsDirectory.resolve(name + ".json.tmp");

        Files.createDirectories(pluginsDirectory);

        // Updating a generated plugin is intentionally a full replacement:
        // disable the live instance, unregister commands/listeners, delete the old
        // persisted file, write the new JSON, then load from the fresh file.
        unload(name);
        Files.deleteIfExists(path);

        Files.writeString(tempPath, Vibemine.GSON.toJson(schema), StandardCharsets.UTF_8);
        moveIntoPlace(tempPath, path);

        try {
            return load(path);
        } catch (Exception exception) {
            Files.deleteIfExists(path);
            throw exception;
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private void moveIntoPlace(Path tempPath, Path path) throws IOException {
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public VibedPlugin load(Path path) throws IOException {
        VibedPluginSchema schema = parseSchema(Files.readString(path, StandardCharsets.UTF_8));
        String name = normalizeName(schema.name());
        if (plugins.containsKey(name)) {
            unload(name);
        }
        VibedPlugin vibedPlugin = new VibedPlugin(plugin, schema);
        vibedPlugin.enable();
        plugins.put(vibedPlugin.name(), vibedPlugin);
        vibedPlugin.events().forEach(event -> eventIndex.computeIfAbsent(event.eventName(), ignored -> new ArrayList<>()).add(vibedPlugin));
        vibedPlugin.commands().forEach(command -> registerCommand(vibedPlugin, command));
        return vibedPlugin;
    }

    public boolean unload(String name) {
        VibedPlugin removed = plugins.remove(normalizeName(name));
        if (removed == null) return false;
        removed.disable();
        eventIndex.values().forEach(list -> list.remove(removed));
        removed.commands().forEach(command -> Optional.ofNullable(registeredCommands.remove(command.label())).ifPresent(this::unregisterCommand));
        refreshPlayerCommandTrees();
        return true;
    }

    void dispatch(String eventName, Event event) {
        for (VibedPlugin vibedPlugin : eventIndex.getOrDefault(eventName, List.of())) {
            vibedPlugin.executeEvent(eventName, event);
        }
    }

    private void registerCommand(VibedPlugin vibedPlugin, VibedCommand command) {
        unregisterCommand(command.label());

        Command bukkitCommand = new Command(command.label(), vibedPlugin.description(), "/" + command.label(), List.of()) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!testPermission(sender)) return true;
                command.execute(sender, args);
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
                if (!testPermissionSilent(sender)) return List.of();
                return List.copyOf(command.suggest(sender, args));
            }
        };
        bukkitCommand.setPermission(command.permission());
        bukkitCommand.setPermissionMessage("You do not have permission to use this vibed command.");

        CommandMap commandMap = Bukkit.getCommandMap();
        commandMap.register("vibemine", bukkitCommand);
        registeredCommands.put(command.label(), bukkitCommand);
        refreshPlayerCommandTrees();
    }

    private void unregisterCommand(String label) {
        Optional.ofNullable(registeredCommands.remove(label)).ifPresent(this::unregisterCommand);
        Bukkit.getCommandMap().getKnownCommands().remove(label);
        Bukkit.getCommandMap().getKnownCommands().remove("vibemine:" + label);
    }

    private void unregisterCommand(Command command) {
        command.unregister(Bukkit.getCommandMap());
        Bukkit.getCommandMap().getKnownCommands().entrySet().removeIf(entry -> entry.getValue() == command);
    }

    private void refreshPlayerCommandTrees() {
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    private VibedPluginSchema parseSchema(String json) {
        try {
            VibedPluginSchema schema = Vibemine.GSON.fromJson(json, VibedPluginSchema.class);
            if (schema == null || schema.name() == null || schema.name().isBlank()) throw new IllegalArgumentException("Missing plugin name");
            return schema;
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Invalid vibed plugin JSON", exception);
        }
    }

    static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }
}
