package io.rcw.vibemine.ai.tools.plugin;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;

import java.util.List;

@Named("plugin_context")
public final class PluginContextTool implements Tool<PluginContextTool.PluginContextInput, PluginContextTool.PluginContextOutput> {
    public record PluginContextInput(String name) {}
    public record PluginSummary(String name, String json, long workspaceVersion) {}
    public record PluginContextOutput(List<String> availablePlugins, PluginSummary plugin, String error) {}

    @Override public Class<PluginContextInput> inputClass() { return PluginContextInput.class; }
    @Override public Class<PluginContextOutput> outputClass() { return PluginContextOutput.class; }

    @Override public PluginContextOutput execute(Player player, PluginContextInput input) {
        var manager = Vibemine.getInstance().getVibedPluginManager();
        List<String> names = manager.pluginNames();
        try {
            var available = new java.util.TreeSet<>(names);
            available.addAll(manager.stateStore().workspacePlugins(player.getUniqueId().toString()));
            names = List.copyOf(available);
            if (names.isEmpty() && (input == null || input.name() == null || input.name().isBlank() || input.name().equals("latest"))) return new PluginContextOutput(names, null, null);
            String name = input == null || input.name() == null || input.name().isBlank() || input.name().equals("latest")
                    ? names.getFirst() : input.name();
            var workspace = manager.stateStore().workspace(name, player.getUniqueId().toString());
            return new PluginContextOutput(names, new PluginSummary(name, workspace.files().get("plugin.json"), workspace.version()), null);
        } catch (Exception exception) { return new PluginContextOutput(names, null, exception.getMessage()); }
    }

    @Override public String usage() {
        return "List existing plugins and read the selected plugin.json from your isolated workspace. Supply name to select a plugin; omitted name selects the first alphabetically. Use state status to list workspace files and active checkpoint.";
    }
}
