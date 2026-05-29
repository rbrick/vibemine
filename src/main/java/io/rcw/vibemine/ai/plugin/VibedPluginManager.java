package io.rcw.vibemine.ai.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import org.bukkit.help.GenericCommandHelpTopic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
        if (listener != null) HandlerList.unregisterAll(listener);
    }

    public Collection<VibedPlugin> plugins() {
        return List.copyOf(plugins.values());
    }

    public List<String> pluginNames() {
        try {
            Files.createDirectories(pluginsDirectory);
            try (var paths = Files.list(pluginsDirectory)) {
                return paths
                        .filter(path -> path.toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                        .sorted()
                        .toList();
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to list persisted vibed plugins", exception);
            return List.of();
        }
    }

    public boolean isEnabled(String name) {
        return plugins.containsKey(normalizeName(name));
    }

    public synchronized VibedPlugin enablePlugin(String name) throws IOException {
        String normalized = normalizeName(name);
        Path path = pluginsDirectory.resolve(normalized + ".json");
        if (!Files.exists(path)) throw new NoSuchFileException(normalized + ".json");
        return load(path);
    }

    public synchronized boolean disablePlugin(String name) {
        return unload(name);
    }

    public synchronized boolean deletePlugin(String name) throws IOException {
        String normalized = normalizeName(name);
        boolean wasLoaded = unload(normalized);
        Path path = pluginsDirectory.resolve(normalized + ".json");
        return Files.deleteIfExists(path) || wasLoaded;
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

    public synchronized VibedPlugin saveAndLoad(String json) throws IOException {
        VibedPluginSchema schema = parseSchema(json);
        return saveAndLoad(schema);
    }

    public synchronized VibedPlugin patchAndLoad(JsonObject patch) throws IOException {
        return saveAndLoad(patchedJson(patch));
    }

    public synchronized String existingPluginJson(String name) throws IOException {
        String normalized = normalizeName(name);
        Path path = pluginsDirectory.resolve(normalized + ".json");
        if (!Files.exists(path)) throw new NoSuchFileException(normalized + ".json");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public synchronized String patchedJson(JsonObject patch) throws IOException {
        if (patch == null || !patch.has("name")) throw new IllegalArgumentException("Patch is missing plugin name");
        String name = normalizeName(patch.get("name").getAsString());
        JsonObject existing = Vibemine.GSON.fromJson(existingPluginJson(name), JsonObject.class);
        if (existing == null) throw new IllegalArgumentException("Existing plugin JSON is empty");

        copyIfPresent(patch, existing, "description");
        copyIfPresent(patch, existing, "globals");
        copyIfPresent(patch, existing, "version");
        patchArrayByKey(existing, patch, "commands", "label");
        patchArrayByKey(existing, patch, "events", "event");

        return Vibemine.GSON.toJson(existing);
    }

    private synchronized VibedPlugin saveAndLoad(VibedPluginSchema schema) throws IOException {
        String name = normalizeName(schema.name());
        Path path = pluginsDirectory.resolve(name + ".json");
        Path tempPath = pluginsDirectory.resolve(name + ".json.tmp");

        Files.createDirectories(pluginsDirectory);

        // Updating a generated plugin is intentionally a full replacement on disk.
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

    private void copyIfPresent(JsonObject source, JsonObject target, String key) {
        if (source.has(key)) target.add(key, source.get(key));
    }

    private void patchArrayByKey(JsonObject existing, JsonObject patch, String arrayName, String keyName) {
        if (!patch.has(arrayName) || !patch.get(arrayName).isJsonArray()) return;
        var current = existing.has(arrayName) && existing.get(arrayName).isJsonArray()
                ? existing.getAsJsonArray(arrayName)
                : new com.google.gson.JsonArray();

        for (JsonElement patchElement : patch.getAsJsonArray(arrayName)) {
            if (!patchElement.isJsonObject()) continue;
            JsonObject patchObject = patchElement.getAsJsonObject();
            if (!patchObject.has(keyName)) continue;
            String key = normalizeName(patchObject.get(keyName).getAsString());
            int index = findObjectIndex(current, keyName, key);
            boolean delete = patchObject.has("delete") && patchObject.get("delete").getAsBoolean();
            if (delete) {
                if (index >= 0) current.remove(index);
            } else if (index >= 0) {
                JsonObject merged = current.get(index).getAsJsonObject();
                patchObject.entrySet().forEach(entry -> {
                    if (!entry.getKey().equals("delete")) merged.add(entry.getKey(), entry.getValue());
                });
            } else {
                current.add(patchObject);
            }
        }
        existing.add(arrayName, current);
    }

    private int findObjectIndex(com.google.gson.JsonArray array, String keyName, String key) {
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (object.has(keyName) && normalizeName(object.get(keyName).getAsString()).equals(key)) return i;
        }
        return -1;
    }

    private void moveIntoPlace(Path tempPath, Path path) throws IOException {
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized VibedPlugin load(Path path) throws IOException {
        VibedPluginSchema schema = parseSchema(Files.readString(path, StandardCharsets.UTF_8));
        String name = normalizeName(schema.name());
        if (plugins.containsKey(name)) {
            unload(name);
        }
        VibedPlugin vibedPlugin = new VibedPlugin(plugin, schema);
        vibedPlugin.enable();
        plugins.put(vibedPlugin.name(), vibedPlugin);
        plugin.getLogger().info("Loaded vibed plugin '" + vibedPlugin.name() + "' with " + vibedPlugin.events().size() + " events and " + vibedPlugin.commands().size() + " commands");
        vibedPlugin.events().forEach(event -> {
            String normalizedEventName = normalizeEventName(event.eventName());
            eventIndex.computeIfAbsent(normalizedEventName, ignored -> new CopyOnWriteArrayList<>()).add(vibedPlugin);
            plugin.getLogger().info("Registered vibed event '" + normalizedEventName + "' for plugin '" + vibedPlugin.name() + "'");
        });
        vibedPlugin.commands().forEach(command -> registerCommand(vibedPlugin, command));
        return vibedPlugin;
    }

    public synchronized boolean unload(String name) {
        VibedPlugin removed = plugins.remove(normalizeName(name));
        if (removed == null) return false;
        removed.disable();
        eventIndex.entrySet().removeIf(entry -> {
            entry.getValue().remove(removed);
            return entry.getValue().isEmpty();
        });
        removed.commands().forEach(command -> Optional.ofNullable(registeredCommands.remove(command.label())).ifPresent(this::unregisterCommand));
        refreshPlayerCommandTrees();
        return true;
    }

    void dispatch(String eventName, Event event) {
        String normalizedEventName = normalizeEventName(eventName);
        List<VibedPlugin> listeners = List.copyOf(eventIndex.getOrDefault(normalizedEventName, List.of()));
        plugin.getLogger().info("Dispatching vibed event '" + normalizedEventName + "' from " + event.getClass().getSimpleName() + " to " + listeners.size() + " plugin(s)");
        for (VibedPlugin vibedPlugin : listeners) {
            plugin.getLogger().info("Executing vibed event '" + normalizedEventName + "' for plugin '" + vibedPlugin.name() + "'");
            vibedPlugin.executeEvent(normalizedEventName, event);
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
        synchronized (commandMap) {
            commandMap.register("vibemine", bukkitCommand);
            Bukkit.getCommandMap().getKnownCommands().put(command.label(), bukkitCommand);
            Bukkit.getCommandMap().getKnownCommands().put("vibemine:" + command.label(), bukkitCommand);
        }
        registeredCommands.put(command.label(), bukkitCommand);
        registerHelpTopic(bukkitCommand);
        plugin.getLogger().info("Registered vibed command '/" + command.label() + "' in Bukkit CommandMap and HelpMap");
        refreshPlayerCommandTrees();
    }

    private void unregisterCommand(String label) {
        Optional.ofNullable(registeredCommands.remove(label)).ifPresent(this::unregisterCommand);
        synchronized (Bukkit.getCommandMap()) {
            Bukkit.getCommandMap().getKnownCommands().remove(label);
            Bukkit.getCommandMap().getKnownCommands().remove("vibemine:" + label);
        }
        unregisterHelpTopic(label);
    }

    private void unregisterCommand(Command command) {
        command.unregister(Bukkit.getCommandMap());
        synchronized (Bukkit.getCommandMap()) {
            Map<String, Command> knownCommands = Bukkit.getCommandMap().getKnownCommands();
            List<String> labelsToRemove = knownCommands.entrySet().stream()
                    .filter(entry -> entry.getValue() == command)
                    .map(Map.Entry::getKey)
                    .toList();
            labelsToRemove.forEach(knownCommands::remove);
        }
        unregisterHelpTopic(command.getName());
    }

    private void registerHelpTopic(Command command) {
        try {
            unregisterHelpTopic(command.getName());
            Bukkit.getHelpMap().addTopic(new GenericCommandHelpTopic(command));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not register help topic for /" + command.getName(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void unregisterHelpTopic(String label) {
        String normalizedLabel = label.startsWith("/") ? label : "/" + label;
        try {
            for (Field field : Bukkit.getHelpMap().getClass().getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Map<Object, Object> map = (Map<Object, Object>) field.get(Bukkit.getHelpMap());
                map.remove(normalizedLabel);
                map.remove(label);
                map.remove("vibemine:" + label);
                map.remove("/vibemine:" + label);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.FINE, "Could not unregister help topic for /" + label, exception);
        }
    }

    private void refreshPlayerCommandTrees() {
        if (!plugin.isEnabled()) return;

        // Delay command-tree refresh until after command map mutation has completed. Calling
        // updateCommands inline during hot-swap can race Paper's async command-tree builder.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Could not refresh player command trees", exception);
            }
        }, 2L);
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

    static String normalizeEventName(String eventName) {
        if (eventName == null) return "";
        return eventName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
