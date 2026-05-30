package io.rcw.vibemine.ai.tools.plugin;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Named("plugin_context")
public final class PluginContextTool implements Tool<PluginContextTool.PluginContextInput, PluginContextTool.PluginContextOutput> {
    public record PluginContextInput(String name) { }
    public record PluginSummary(String name, long updatedAt, String json) { }
    public record PluginContextOutput(List<String> availablePlugins, PluginSummary plugin, String error) {
        public static PluginContextOutput error(String message) {
            return new PluginContextOutput(List.of(), null, message);
        }
    }

    @Override
    public Class<PluginContextInput> inputClass() {
        return PluginContextInput.class;
    }

    @Override
    public Class<PluginContextOutput> outputClass() {
        return PluginContextOutput.class;
    }

    @Override
    public PluginContextOutput execute(Player player, PluginContextInput input) {
        Path directory = Vibemine.getInstance().getDataFolder().toPath().resolve("vibed-plugins");
        try {
            Files.createDirectories(directory);
            List<Path> pluginFiles;
            try (var paths = Files.list(directory)) {
                pluginFiles = paths
                        .filter(Files::isDirectory)
                        .map(path -> path.resolve("plugin.json"))
                        .filter(Files::exists)
                        .sorted(Comparator.comparingLong(this::lastModified).reversed())
                        .toList();
            }

            List<String> names = pluginFiles.stream()
                    .map(path -> path.getParent().getFileName().toString())
                    .toList();

            if (pluginFiles.isEmpty()) return new PluginContextOutput(names, null, null);

            Optional<Path> selected = select(pluginFiles, input == null ? null : input.name());
            if (selected.isEmpty()) return new PluginContextOutput(names, null, "Unknown plugin: " + input.name());

            Path path = selected.get();
            String name = path.getParent().getFileName().toString();
            return new PluginContextOutput(names, new PluginSummary(name, lastModified(path), Files.readString(path, StandardCharsets.UTF_8)), null);
        } catch (IOException exception) {
            return PluginContextOutput.error(exception.getMessage());
        }
    }

    private Optional<Path> select(List<Path> pluginFiles, String rawName) {
        if (rawName == null || rawName.isBlank() || rawName.equalsIgnoreCase("latest")) {
            return Optional.of(pluginFiles.getFirst());
        }
        String normalized = rawName.toLowerCase().replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return pluginFiles.stream()
                .filter(path -> path.getParent().getFileName().toString().equalsIgnoreCase(normalized))
                .findFirst();
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    @Override
    public String usage() {
        return """
                Get the JSON for an existing/generated VibePlugin so you can update or expand it instead of starting from scratch.
                Input: {"name":"plugin_name"}. Omit name or use "latest" for the most recently modified plugin.
                When modifying a plugin, use file_read/file_edit/file_write on plugin.json, globals.js, commands/*.js, and events/*.js.
                """;
    }
}
