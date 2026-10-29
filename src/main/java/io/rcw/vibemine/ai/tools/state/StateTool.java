package io.rcw.vibemine.ai.tools.state;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.PluginStateStore;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

@Named("state")
public final class StateTool implements Tool<StateTool.StateInput, StateTool.StateOutput> {
    private final Plugin plugin;
    public StateTool(Plugin plugin) { this.plugin = plugin; }
    @Override public Class<StateInput> inputClass() { return StateInput.class; }
    @Override public Class<StateOutput> outputClass() { return StateOutput.class; }

    @Override public StateOutput execute(Player player, StateInput input) {
        try {
            if (input == null || input.action == null) throw new IllegalArgumentException("action and plugin are required");
            var manager = ((Vibemine) plugin).getVibedPluginManager();
            var store = manager.stateStore();
            String owner = player.getUniqueId().toString();
            Object result = switch (input.action.toLowerCase(Locale.ROOT)) {
                case "status" -> view(store.workspace(input.plugin, owner), store.active(input.plugin));
                case "checkpoint" -> store.checkpoint(input.plugin, owner, version(input), input.message);
                case "history" -> store.history(input.plugin, 100);
                case "branches" -> store.branches(input.plugin);
                case "branch" -> {
                    store.branch(input.plugin, input.branch, input.checkpoint);
                    yield "Branch created; checkout explicitly to switch";
                }
                case "fork" -> view(store.fork(input.plugin, owner, version(input), input.branch), store.active(input.plugin));
                case "checkout" -> view(store.checkout(input.plugin, owner, version(input), input.branch), store.active(input.plugin));
                case "delete" -> view(store.delete(input.plugin, owner, version(input), input.file), store.active(input.plugin));
                case "activate" -> {
                    if (Bukkit.isPrimaryThread()) throw new IllegalStateException("Agent activation must be requested off-thread");
                    // Empty means no active checkpoint; null means the agent forgot the concurrency guard.
                    if (input.expectedActive == null) throw new IllegalArgumentException("expectedActive is required (empty string when none)");
                    String expected = input.expectedActive.isEmpty() ? null : input.expectedActive;
                    manager.activateAsync(player, input.plugin, input.checkpoint, expected).join();
                    yield "Activated " + input.checkpoint;
                }
                default -> throw new IllegalArgumentException("Unknown state action");
            };
            return new StateOutput(true, null, result);
        } catch (Exception exception) {
            Throwable cause = exception;
            while (cause.getCause() != null) cause = cause.getCause();
            return new StateOutput(false, cause.getMessage(), null);
        }
    }

    private static long version(StateInput input) {
        if (input.expectedVersion == null) throw new IllegalArgumentException("expectedVersion is required; read status first");
        return input.expectedVersion;
    }

    private static StateView view(PluginStateStore.Workspace workspace, String active) {
        return new StateView(workspace.branch(), workspace.base(), workspace.version(), workspace.dirty(),
                workspace.files().keySet().stream().sorted().toList(), active == null ? "" : active);
    }

    @Override public String usage() {
        return "Source checkpoints and branches. For fields unused by an action send empty strings and expectedVersion=0. status opens your isolated persistent workspace and returns version, files and active checkpoint. "
                + "checkpoint(message,expectedVersion) saves all edits and returns an immutable ID; refresh status afterward. "
                + "branch(branch,checkpoint) creates a branch; checkout(branch,expectedVersion) switches only a clean workspace. "
                + "fork(branch,expectedVersion) moves your workspace including dirty edits onto a new branch; use it when a shared branch has advanced. history/branches list saved state; delete(file,expectedVersion) deletes a workspace file. "
                + "activate(checkpoint,expectedActive) explicitly loads a checkpoint; requires operator activation permission. "
                + "Never loses workspace edits on activation or branch switching. World/database/runtime state is not versioned.";
    }

    public record StateInput(String action, String plugin, String message, String checkpoint, String branch,
                             String file, Long expectedVersion, String expectedActive) {}
    public record StateOutput(boolean success, String error, Object result) {}
    public record StateView(String branch, String base, long version, boolean dirty, List<String> files, String active) {}
}
