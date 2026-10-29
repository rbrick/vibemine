package io.rcw.vibemine.ai.tools.io;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@Named("file_edit")
public class FileEditTool implements Tool<FileEditTool.FileEditInput, FileEditTool.FileEditOutput> {
    private final Plugin plugin;
    public FileEditTool(Plugin plugin) { this.plugin = plugin; }
    @Override public Class<FileEditInput> inputClass() { return FileEditInput.class; }
    @Override public Class<FileEditOutput> outputClass() { return FileEditOutput.class; }

    @Override public FileEditOutput execute(Player player, FileEditInput input) {
        try {
            var workspace = PluginFileToolSupport.store(plugin).edit(input.plugin, PluginFileToolSupport.owner(player),
                    PluginFileToolSupport.version(input.expectedVersion), input.file, input.oldText, input.newText);
            return new FileEditOutput(true, "", workspace.version());
        } catch (Exception exception) { return new FileEditOutput(false, exception.getMessage(), null); }
    }

    @Override public String usage() { return "Replace one exact unique source fragment in your workspace. Supply expectedVersion from the latest read/status/write."; }
    public record FileEditInput(String plugin, String file, String oldText, String newText, Long expectedVersion) {}
    public record FileEditOutput(boolean success, String error, Long version) {}
}
