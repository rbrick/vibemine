package io.rcw.vibemine.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.rcw.vibemine.Items;
import io.rcw.vibemine.code.Highlighting;
import io.rcw.vibemine.code.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class VibeCommand implements BasicCommand {



    public void execute(CommandSourceStack source, String @NonNull [] args) {
        final CommandSender sender = source.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("console can't vibe", NamedTextColor.DARK_RED));
            return;
        }

        sender.sendMessage(Highlighting.highlight(Language.JSON,"""
                {
                  "name": "test",
                  "enabled": true,
                  "count": 42,
                  "message": "Hello\\nWorld",
                  "items": ["a", "b", "c"],
                  "nested": {
                    "value": null,
                    "flag": false
                  }
                }
                """));


        sender.sendMessage(Highlighting.highlight(Language.JAVASCRIPT, """
                function greet(name) {
                  const message = `Hello, ${name}!`;
                
                  if (name === "Steve") {
                    return true;
                  }
                
                  return message;
                }
                
                const result = greet("Alex");
                console.log(result);
                """));
    }
}
