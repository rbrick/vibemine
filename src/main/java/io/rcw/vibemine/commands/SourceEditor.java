package io.rcw.vibemine.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.SourceFiles;
import io.rcw.vibemine.ai.plugin.VibedPluginManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

final class SourceEditor {
    static final String PERMISSION = "vibemine.vibe.ide";
    private final VibedPluginManager manager;
    private final Vibemine plugin = JavaPlugin.getPlugin(Vibemine.class);
    private final SourceFiles files = new SourceFiles(plugin.getDataFolder().toPath().resolve("vibed-plugins"));

    SourceEditor(VibedPluginManager manager) {
        this.manager = manager;
    }

    void open(Player player) {
        if (!player.hasPermission(PERMISSION)) return;
        select(player, "Plugins", manager.pluginNames().stream()
                .map(name -> button(player, name, () -> openFiles(player, name))).toList());
    }

    private void openFiles(Player player, String name) throws IOException {
        select(player, name, files.list(name).stream()
                .map(file -> button(player, file, () -> edit(player, name, file))).toList());
    }

    private void select(Player player, String title, List<ActionButton> buttons) {
        if (buttons.isEmpty()) {
            player.sendMessage(Component.text("No source files or plugins found.", NamedTextColor.YELLOW));
            return;
        }
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title)).build())
                .type(DialogType.multiAction(buttons).exitAction(ActionButton.builder(Component.text("Close")).build()).build())));
    }

    private void edit(Player player, String name, String file) throws IOException {
        String original = files.read(name, file);
        var input = DialogInput.text("source", Component.text("Source"))
                .labelVisible(false).width(600).initial(original).maxLength(SourceFiles.MAX_LENGTH)
                .multiline(TextDialogInput.MultilineOptions.create(null, 240)).build();
        var save = ActionButton.builder(Component.text("Save")).action(DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(player.getUniqueId())) return;
            String text = response.getText("source");
            run(player, () -> {
                files.save(name, file, original, text);
                player.sendMessage(Component.text("Saved " + file + ". Use /vibe enable " + name + " to reload.", NamedTextColor.GREEN));
            });
        }, options())).build();
        player.showDialog(Dialog.create(builder -> builder.empty()
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

    private void run(Player player, IOAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !player.hasPermission(PERMISSION)) return;
            try {
                action.run();
            } catch (IOException | RuntimeException exception) {
                player.sendMessage(Component.text("Editor: " + exception.getMessage(), NamedTextColor.RED));
            }
        });
    }

    @FunctionalInterface
    private interface IOAction {
        void run() throws IOException;
    }
}
