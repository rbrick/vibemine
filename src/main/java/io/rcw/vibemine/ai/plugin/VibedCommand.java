package io.rcw.vibemine.ai.plugin;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jspecify.annotations.Nullable;

public class VibedCommand implements BasicCommand {

    // TODO(ryan): integrate into brigadier for more complex commands.
    public VibedCommand(String label) {}


    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {

    }

    @Override
    public @Nullable String permission() {
        return "";
    }
}
