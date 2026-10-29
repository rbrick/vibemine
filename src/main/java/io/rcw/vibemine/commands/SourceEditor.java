package io.rcw.vibemine.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.PluginStateStore;
import io.rcw.vibemine.ai.plugin.VibedPluginManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

final class SourceEditor {
    static final String PERMISSION = "vibemine.vibe.ide";
    private static final int MAX_LENGTH = 16000;
    private final VibedPluginManager manager;
    private final Vibemine plugin = JavaPlugin.getPlugin(Vibemine.class);
    private final PluginStateStore store;

    SourceEditor(VibedPluginManager manager) {
        this.manager = manager;
        this.store = manager.stateStore();
    }

    void open(Player player) {
        run(player, () -> {
            var names = new java.util.TreeSet<>(manager.pluginNames());
            names.addAll(store.workspacePlugins(player.getUniqueId().toString()));
            select(player, "Plugins", names.stream().map(name -> button(player, name, () -> openFiles(player, name))).toList());
        });
    }

    private void openFiles(Player player, String name) throws IOException {
        var workspace = store.workspace(name, player.getUniqueId().toString());
        select(player, name + " / " + workspace.branch(), workspace.files().keySet().stream()
                .filter(file -> file.endsWith(".js")).sorted()
                .map(file -> button(player, file, () -> edit(player, name, file))).toList());
    }

    private void select(Player player, String title, List<ActionButton> buttons) {
        if (buttons.isEmpty()) {
            message(player, Component.text("No source files or plugins found.", NamedTextColor.YELLOW));
            return;
        }
        show(player, Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title)).build())
                .type(DialogType.multiAction(buttons).exitAction(ActionButton.builder(Component.text("Close")).build()).build())));
    }

    private void edit(Player player, String name, String file) throws IOException {
        String owner = player.getUniqueId().toString();
        var workspace = store.workspace(name, owner);
        String original = workspace.files().get(file);
        if (original == null) throw new IOException("Source file no longer exists");
        if (original.length() > MAX_LENGTH) throw new IOException("File exceeds the 16,000-character dialog limit; use agent file tools");
        var input = DialogInput.text("source", Component.text("Source"))
                .labelVisible(false).width(600).initial(original).maxLength(MAX_LENGTH)
                .multiline(TextDialogInput.MultilineOptions.create(null, 240)).build();
        var save = ActionButton.builder(Component.text("Save checkpoint")).action(DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(player.getUniqueId())) return;
            String text = response.getText("source");
            run(player, () -> {
                if (text == null || text.length() > MAX_LENGTH) throw new IOException("Invalid source text");
                var changed = store.write(name, owner, workspace.version(), file, text);
                String id = store.checkpoint(name, owner, changed.version(), "Edit " + file);
                message(player, Component.text("Saved checkpoint " + id + ". ", NamedTextColor.GREEN)
                        .append(Component.text("[Activate]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/vibe activate " + name + " " + id))));
            });
        }, options())).build();
        show(player, Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(Path.of(file).getFileName().toString())).inputs(List.of(input)).build())
                .type(DialogType.confirmation(save, ActionButton.builder(Component.text("Cancel")).build()))));
    }

    private ActionButton button(Player player, String label, IOAction action) {
        return ActionButton.builder(Component.text(label)).action(DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player actor && actor.getUniqueId().equals(player.getUniqueId())) run(player, action);
        }, options())).build();
    }

    private static ClickCallback.Options options() {
        return ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(30)).build();
    }

    private void show(Player player, Dialog dialog) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.hasPermission(PERMISSION)) player.showDialog(dialog);
        });
    }

    private void message(Player player, Component text) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.sendMessage(text);
        });
    }

    private void run(Player player, IOAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !player.hasPermission(PERMISSION)) return;
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try { action.run(); }
                catch (IOException | RuntimeException exception) {
                    message(player, Component.text("Editor: " + exception.getMessage(), NamedTextColor.RED));
                }
            });
        });
    }

    @FunctionalInterface private interface IOAction { void run() throws IOException; }
}
