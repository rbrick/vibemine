package io.rcw.vibemine.ai.tools.command;


import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

@Named("command")
public final class CommandTool implements Tool<CommandTool.CommandInput, CommandTool.CommandOutput> {

    public record CommandInput(List<String> command) {
    }

    public record CommandOutput(boolean success) {
    }


    @Override
    public Class<CommandInput> inputClass() {
        return CommandInput.class;
    }

    @Override
    public Class<CommandOutput> outputClass() {
        return CommandOutput.class;
    }

    @Override
    public CommandOutput execute(Player player, CommandInput commandInput) {
        Bukkit.getScheduler().runTask(Vibemine.getInstance(), () -> {
            for (String command : commandInput.command()) {
                Bukkit.dispatchCommand(player, command);
            }
        });
        // ACK
        return new CommandOutput(true);
    }

    @Override
    public String usage() {
        return """
                Pass in a series of full commands.
                Example: ["time set day", "gamemode creative"]
                """;
    }
}
