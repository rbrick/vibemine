package io.rcw.vibemine.ai.tools.io;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Named("file_read")
public class FileReadTool implements Tool<FileReadTool.FileReadInput, FileReadTool.FileReadOutput> {
    private final Plugin plugin;

    public FileReadTool(Plugin plugin) { this.plugin = plugin; }

    @Override public Class<FileReadInput> inputClass() { return FileReadInput.class; }
    @Override public Class<FileReadOutput> outputClass() { return FileReadOutput.class; }

    @Override
    public FileReadOutput execute(Player player, FileReadInput input) {
        try {
            var path = PluginFileToolSupport.resolve(plugin, input.plugin, input.file);
            if (!Files.exists(path)) return new FileReadOutput(false, "file not found", null);
            if (Files.size(path) > 128_000) return new FileReadOutput(false, "file too large", null);
            return new FileReadOutput(true, "", Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return new FileReadOutput(false, exception.getMessage(), null);
        }
    }

    public record FileReadInput(String plugin, String file) {}
    public record FileReadOutput(boolean success, String error, String content) {}
}
