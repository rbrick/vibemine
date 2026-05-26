package io.rcw.vibemine.ai.plugin;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.rcw.vibemine.ai.plugin.context.CommandExecutionContext;
import org.bukkit.command.CommandSender;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class VibedCommand implements BasicCommand {
    private final VibedPlugin plugin;
    private final String label;
    private final String permission;
    private final String sourceCode;

    public VibedCommand(VibedPlugin plugin, String label, String permission, String sourceCode) {
        this.plugin = plugin;
        this.label = VibedPluginManager.normalizeName(label);
        this.permission = permission == null || permission.isBlank() ? null : permission;
        this.sourceCode = sourceCode;
    }

    public String label() { return label; }
    public String sourceCode() { return sourceCode; }

    public Collection<String> suggest(CommandSender sender, String[] args) {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        execute(commandSourceStack.getSender(), args);
    }

    public void execute(CommandSender sender, String[] args) {
        plugin.executeCommand(label, sender, args);
    }

    void executeSource(CommandSender sender, String[] args, Value state) {
        var context = new CommandExecutionContext(sender, args);
        plugin.evalFunction(sourceCode).execute(context, state);
    }

    @Override
    public @Nullable String permission() {
        return permission;
    }
}
