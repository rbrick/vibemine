package io.rcw.vibemine.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.rcw.vibemine.Items;
import io.rcw.vibemine.code.Highlighting;
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

        sender.sendMessage(Highlighting.highlight("""
                class PlayerStats {
                    constructor(name) {
                        this.name = name;
                        this.level = 42;
                        this.health = 99.5;
                        this.online = true;
                    }
                
                    damage(amount) {
                        this.health -= amount;
                
                        if (this.health <= 0) {
                            return "dead";
                        }
                
                        return `HP: ${this.health}`;
                    }
                }
                
                
                function test(a,b) {
                  return a+b+1;
                }
                
                const stats = new PlayerStats("Ryan");
                
                for (let i = 0; i < 3; i++) {
                    console.log(stats.damage(i * 5));
                }
                """));
    }
}
