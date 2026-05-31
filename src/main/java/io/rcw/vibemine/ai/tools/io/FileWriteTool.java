package io.rcw.vibemine.ai.tools.io;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

@Named("file_write")
public class FileWriteTool implements Tool<FileWriteTool.FileWriteInput, FileWriteTool.FileWriteOutput> {


    private final Plugin plugin;

    public FileWriteTool(Plugin plugin) {
        this.plugin = plugin;
    }


    @Override
    public Class<FileWriteInput> inputClass() {
        return FileWriteInput.class ;
    }

    @Override
    public Class<FileWriteOutput> outputClass() {
        return FileWriteOutput.class;
    }

    @Override
    public FileWriteOutput execute(Player player, FileWriteInput fileWriteInput) {
        try {
            var path = PluginFileToolSupport.resolve(plugin, fileWriteInput.plugin, fileWriteInput.file);
            String normalizedFile = fileWriteInput.file.replace('\\', '/');
            if (normalizedFile.endsWith(".js")
                    && !normalizedFile.equals("globals.js")
                    && !normalizedFile.startsWith("commands/")
                    && !normalizedFile.startsWith("events/")) {
                return new FileWriteOutput(false, "JavaScript script files must be globals.js, commands/<command>.js, or events/<event>.js");
            }
            if (normalizedFile.equals("plugin.json")) {
                String validationError = validateManifestReferences(path.getParent(), fileWriteInput.content);
                if (validationError != null) return new FileWriteOutput(false, validationError);
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, fileWriteInput.content == null ? "" : fileWriteInput.content, StandardCharsets.UTF_8);
            if (normalizedFile.equals("plugin.json")) {
                String loadError = loadPlugin(fileWriteInput.plugin);
                if (loadError != null) return new FileWriteOutput(true, "Saved, but auto-load failed: " + loadError);
            }
            return new FileWriteOutput(true, "");
        } catch (Exception exception) {
            return new FileWriteOutput(false, exception.getMessage());
        }
    }

    private String loadPlugin(String pluginName) {
        if (!(plugin instanceof Vibemine vibemine)) return null;
        if (vibemine.getVibedPluginManager() == null) return null;
        try {
            if (Bukkit.isPrimaryThread()) {
                vibemine.getVibedPluginManager().enablePlugin(pluginName);
            } else {
                CompletableFuture<Void> future = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(vibemine, () -> {
                    try {
                        vibemine.getVibedPluginManager().enablePlugin(pluginName);
                        future.complete(null);
                    } catch (Exception exception) {
                        future.completeExceptionally(exception);
                    }
                });
                future.get();
            }
            return null;
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return cause.getMessage();
        }
    }

    private String validateManifestReferences(java.nio.file.Path pluginRoot, String content) {
        try {
            JsonObject manifest = JsonParser.parseString(content == null ? "" : content).getAsJsonObject();
            String globalsPath = manifest.has("globalsPath") && !manifest.get("globalsPath").isJsonNull()
                    ? manifest.get("globalsPath").getAsString()
                    : null;
            if (globalsPath != null && !globalsPath.isBlank() && !Files.exists(pluginRoot.resolve(globalsPath).normalize())) {
                return "plugin.json must be written last: missing globalsPath file " + globalsPath;
            }
            String commandError = validateScriptArray(pluginRoot, manifest, "commands", "path", "commands/");
            if (commandError != null) return commandError;
            String eventError = validateScriptArray(pluginRoot, manifest, "events", "path", "events/");
            if (eventError != null) return eventError;
            return null;
        } catch (Exception exception) {
            return "Invalid plugin.json: " + exception.getMessage();
        }
    }

    private String validateScriptArray(java.nio.file.Path pluginRoot, JsonObject manifest, String arrayName, String pathKey, String requiredPrefix) {
        if (!manifest.has(arrayName) || !manifest.get(arrayName).isJsonArray()) return null;
        for (var element : manifest.getAsJsonArray(arrayName)) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has(pathKey) || object.get(pathKey).isJsonNull()) return arrayName + " entry is missing path";
            String scriptPath = object.get(pathKey).getAsString().replace('\\', '/');
            if (!scriptPath.startsWith(requiredPrefix) || !scriptPath.endsWith(".js")) {
                return arrayName + " path must be " + requiredPrefix + "<name>.js, got " + scriptPath;
            }
            java.nio.file.Path resolved = pluginRoot.resolve(scriptPath).normalize();
            if (!resolved.startsWith(pluginRoot.normalize())) return "Path escapes plugin directory: " + scriptPath;
            if (!Files.exists(resolved)) return "plugin.json must be written last: missing script file " + scriptPath;
        }
        return null;
    }

    public record FileWriteInput(String plugin, String file, String content) {}

    public record FileWriteOutput(boolean success, String error) {}


}
