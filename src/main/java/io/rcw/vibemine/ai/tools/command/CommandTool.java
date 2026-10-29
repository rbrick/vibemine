package io.rcw.vibemine.ai.tools.command;


import io.rcw.vibemine.FoliaScheduler;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import io.rcw.vibemine.commands.VibeCommandSender;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Named("command")
public final class CommandTool implements Tool<CommandTool.CommandInput, CommandTool.CommandOutput> {

    public record CommandInput(List<String> command) {
    }

    public record CommandOutput(boolean success, List<String> messages, String error) {
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
    public CommandOutput execute(Player player, CommandInput input) {
        if (player == null || input == null || input.command() == null || input.command().isEmpty()) {
            return new CommandOutput(false, List.of(), "At least one command is required");
        }

        try {
            return FoliaScheduler.entity(player, () -> {
                var messages = new ArrayList<String>();
                var sender = new VibeCommandSender(player,
                        message -> messages.add(PlainTextComponentSerializer.plainText().serialize(message)));
                boolean success = true;
                for (String command : input.command()) {
                    if (command == null || command.isBlank()) continue;
                    success &= Bukkit.dispatchCommand(sender, command.startsWith("/") ? command.substring(1) : command);
                }
                return new CommandOutput(success, List.copyOf(messages), null);
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return new CommandOutput(false, List.of(), cause.getMessage());
        }
    }

    @Override
    public String usage() {
        return """
                Pass in a series of full commands. Command replies are returned in messages.
                Example: ["time set day", "gamemode creative"]
                """;
    }
}
