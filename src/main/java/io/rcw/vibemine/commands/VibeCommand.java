package io.rcw.vibemine.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.rcw.vibemine.Items;
import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.code.Highlighting;
import io.rcw.vibemine.code.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.stream.IntStream;

public class VibeCommand implements BasicCommand {



    public void execute(CommandSourceStack source, String @NonNull [] args) {
        final CommandSender sender = source.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("console can't vibe", NamedTextColor.DARK_RED));
            return;
        }

        // clear the chat for the player
        IntStream.range(0, 200).forEach(_ -> player.sendMessage(Component.text("")));

        Bukkit.getScheduler().runTaskAsynchronously(Vibemine.getInstance(), () -> {
            // begin a conversation
            Conversation.beginConversation(player);
        });
    }
}
