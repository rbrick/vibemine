package io.rcw.vibemine.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.rcw.vibemine.ai.plugin.runtime.VibeRuntimeBindings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.NonNull;

public class VibeCommand implements BasicCommand {



    public void execute(CommandSourceStack source, String @NonNull [] args) {
        final CommandSender sender = source.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("console can't vibe", NamedTextColor.DARK_RED));
            return;
        }

        // clear the chat for the player
//
//        Bukkit.getScheduler().runTaskAsynchronously(Vibemine.getInstance(), () -> {
//            // begin a conversation
//            Conversation.beginConversation(player);
//        });


        try (var context =
                     Context.newBuilder("js")
                             .allowHostAccess(HostAccess.ALL)
                             .allowHostClassLookup((filter) -> true)

                             .allowAllAccess(true).build()) {


            var bindings = context.getBindings("js");

            VibeRuntimeBindings.install(bindings, player);
            Value fn = context.eval("js", """
                    (function() {
                      if (!permissions.check(sender, "vibemine.vibe")) return;

                      player.sendMessage(`&c&lHello, ${player.getName()}`);

                      const hit = player.rayTrace(50);
                      if (hit.hasHit()) {
                        const block = hit.getBlock();
                        const entity = hit.getEntity();
                        if (block !== null) {
                          player.sendMessage(`&7You are looking at &e${block.getType()} &7at ${block.getX()}, ${block.getY()}, ${block.getZ()}`);
                        } else if (entity !== null) {
                          player.sendMessage(`&7You are looking at entity &e${entity.getType()}`);
                        }
                      } else {
                        player.sendMessage("&7You are not looking at anything within 50 blocks.");
                      }

                      scheduler.later(20, () => player.sendMessage("&aScheduler binding works!"));
                    })
                    """);

            fn.execute();
        }




    }
}
