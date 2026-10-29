package io.rcw.vibemine.ai.tools.io;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@Named("file_write")
public class FileWriteTool implements Tool<FileWriteTool.FileWriteInput, FileWriteTool.FileWriteOutput> {
    private final Plugin plugin;
    public FileWriteTool(Plugin plugin) { this.plugin = plugin; }
    @Override public Class<FileWriteInput> inputClass() { return FileWriteInput.class; }
    @Override public Class<FileWriteOutput> outputClass() { return FileWriteOutput.class; }

    @Override public FileWriteOutput execute(Player player, FileWriteInput input) {
        try {
            var workspace = PluginFileToolSupport.store(plugin).write(input.plugin, PluginFileToolSupport.owner(player),
                    PluginFileToolSupport.version(input.expectedVersion), input.file, input.content);
            return new FileWriteOutput(true, "", workspace.version());
        } catch (Exception exception) { return new FileWriteOutput(false, exception.getMessage(), null); }
    }

    @Override public String usage() { return "Write source in your workspace, not the live plugin. Supply expectedVersion from the latest read/status/write. Checkpoint and activate explicitly after all edits."; }
    public record FileWriteInput(String plugin, String file, String content, Long expectedVersion) {}
    public record FileWriteOutput(boolean success, String error, Long version) {}
}
