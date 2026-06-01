package io.rcw.vibemine.ai.tools.io;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Named("file_edit")
public class FileEditTool implements Tool<FileEditTool.FileEditInput, FileEditTool.FileEditOutput> {
    private final Plugin plugin;

    public FileEditTool(Plugin plugin) { this.plugin = plugin; }

    @Override public Class<FileEditInput> inputClass() { return FileEditInput.class; }
    @Override public Class<FileEditOutput> outputClass() { return FileEditOutput.class; }

    @Override
    public FileEditOutput execute(Player player, FileEditInput input) {
        try {
            var path = PluginFileToolSupport.resolve(plugin, input.plugin, input.file);
            if (!Files.exists(path)) return new FileEditOutput(false, "file not found");
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (input.oldText == null || input.oldText.isEmpty()) return new FileEditOutput(false, "oldText is required");
            int first = content.indexOf(input.oldText);
            if (first < 0) return new FileEditOutput(false, "oldText not found");
            if (content.indexOf(input.oldText, first + input.oldText.length()) >= 0) return new FileEditOutput(false, "oldText is not unique");
            String updated = content.substring(0, first) + (input.newText == null ? "" : input.newText) + content.substring(first + input.oldText.length());
            Files.writeString(path, updated, StandardCharsets.UTF_8);
            String loadError = PluginFileToolSupport.reloadIfManifest(plugin, input.plugin, input.file);
            if (loadError != null) return new FileEditOutput(true, "Saved, but auto-load failed: " + loadError);
            return new FileEditOutput(true, "");
        } catch (Exception exception) {
            return new FileEditOutput(false, exception.getMessage());
        }
    }

    public record FileEditInput(String plugin, String file, String oldText, String newText) {}
    public record FileEditOutput(boolean success, String error) {}
}
