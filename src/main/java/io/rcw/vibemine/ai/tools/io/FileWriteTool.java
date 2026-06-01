package io.rcw.vibemine.ai.tools.io;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Named("file_write")
public class FileWriteTool implements Tool<FileWriteTool.FileWriteInput, FileWriteTool.FileWriteOutput> {
    private final Plugin plugin;

    public FileWriteTool(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override public Class<FileWriteInput> inputClass() { return FileWriteInput.class; }
    @Override public Class<FileWriteOutput> outputClass() { return FileWriteOutput.class; }

    @Override
    public FileWriteOutput execute(Player player, FileWriteInput input) {
        try {
            var path = PluginFileToolSupport.resolve(plugin, input.plugin, input.file);
            var file = input.file.replace('\\', '/');

            String validationError = validateWrite(path, file, input.content);
            if (validationError != null) return new FileWriteOutput(false, validationError);

            Files.createDirectories(path.getParent());
            Files.writeString(path, input.content == null ? "" : input.content, StandardCharsets.UTF_8);

            String loadError = PluginFileToolSupport.reloadIfManifest(plugin, input.plugin, file);
            return loadError == null
                    ? new FileWriteOutput(true, "")
                    : new FileWriteOutput(true, "Saved, but auto-load failed: " + loadError);
        } catch (Exception exception) {
            return new FileWriteOutput(false, exception.getMessage());
        }
    }

    private String validateWrite(Path path, String file, String content) {
        if (isInvalidScriptPath(file)) {
            return "JavaScript script files must be globals.js, commands/<command>.js, or events/<event>.js";
        }
        return file.equals("plugin.json") ? validateManifestReferences(path.getParent(), content) : null;
    }

    private boolean isInvalidScriptPath(String file) {
        return file.endsWith(".js")
                && !file.equals("globals.js")
                && !file.startsWith("commands/")
                && !file.startsWith("events/");
    }

    private String validateManifestReferences(Path pluginRoot, String content) {
        try {
            var manifest = JsonParser.parseString(content == null ? "" : content).getAsJsonObject();
            String globalsError = validateGlobalsPath(pluginRoot, manifest);
            if (globalsError != null) return globalsError;

            String commandError = validateScriptArray(pluginRoot, manifest, "commands", "commands/");
            if (commandError != null) return commandError;

            return validateScriptArray(pluginRoot, manifest, "events", "events/");
        } catch (Exception exception) {
            return "Invalid plugin.json: " + exception.getMessage();
        }
    }

    private String validateGlobalsPath(Path pluginRoot, JsonObject manifest) {
        if (!manifest.has("globalsPath") || manifest.get("globalsPath").isJsonNull()) return null;
        var globalsPath = manifest.get("globalsPath").getAsString();
        if (globalsPath.isBlank() || Files.exists(pluginRoot.resolve(globalsPath).normalize())) return null;
        return "plugin.json must be written last: missing globalsPath file " + globalsPath;
    }

    private String validateScriptArray(Path pluginRoot, JsonObject manifest, String arrayName, String requiredPrefix) {
        if (!manifest.has(arrayName) || !manifest.get(arrayName).isJsonArray()) return null;

        for (var element : manifest.getAsJsonArray(arrayName)) {
            if (!element.isJsonObject()) continue;
            var object = element.getAsJsonObject();
            if (!object.has("path") || object.get("path").isJsonNull()) return arrayName + " entry is missing path";

            var scriptPath = object.get("path").getAsString().replace('\\', '/');
            String pathError = validateScriptPath(pluginRoot, scriptPath, arrayName, requiredPrefix);
            if (pathError != null) return pathError;
        }
        return null;
    }

    private String validateScriptPath(Path pluginRoot, String scriptPath, String arrayName, String requiredPrefix) {
        if (!scriptPath.startsWith(requiredPrefix) || !scriptPath.endsWith(".js")) {
            return arrayName + " path must be " + requiredPrefix + "<name>.js, got " + scriptPath;
        }
        var resolved = pluginRoot.resolve(scriptPath).normalize();
        if (!resolved.startsWith(pluginRoot.normalize())) return "Path escapes plugin directory: " + scriptPath;
        if (!Files.exists(resolved)) return "plugin.json must be written last: missing script file " + scriptPath;
        return null;
    }

    public record FileWriteInput(String plugin, String file, String content) {}
    public record FileWriteOutput(boolean success, String error) {}
}
