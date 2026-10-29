package io.rcw.vibemine.ai.tools.io;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@Named("file_read")
public class FileReadTool implements Tool<FileReadTool.FileReadInput, FileReadTool.FileReadOutput> {
    private final Plugin plugin;
    public FileReadTool(Plugin plugin) { this.plugin = plugin; }
    @Override public Class<FileReadInput> inputClass() { return FileReadInput.class; }
    @Override public Class<FileReadOutput> outputClass() { return FileReadOutput.class; }

    @Override public FileReadOutput execute(Player player, FileReadInput input) {
        try {
            var workspace = PluginFileToolSupport.store(plugin).workspace(input.plugin, PluginFileToolSupport.owner(player));
            String path = io.rcw.vibemine.ai.plugin.PluginStateStore.sourcePath(input.file);
            String content = workspace.files().get(path);
            return new FileReadOutput(content != null, content == null ? "file not found" : "", content, workspace.version());
        } catch (Exception exception) {
            return new FileReadOutput(false, exception.getMessage(), null, null);
        }
    }

    @Override public String usage() { return "Read a file in your isolated workspace. Returns the workspace version needed for writes. Use state action=status to list files."; }
    public record FileReadInput(String plugin, String file) {}
    public record FileReadOutput(boolean success, String error, String content, Long version) {}
}
