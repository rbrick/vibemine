package io.rcw.vibemine.handlers;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import io.rcw.vibemine.ai.events.conversation.PlayerConverseEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatHandler implements Listener {

    public ChatHandler() {
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGlobalChat(AsyncChatEvent event) {
        // prevent players conversing from receiving chat messages from others
        Conversation.clearConversing(event.viewers());
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        // not conversing
        if (!Conversation.isConversing(player)) return;

        var conversation = Conversation.forPlayer(player);
        var text = PlainTextComponentSerializer.plainText().serialize(event.message());

        event.setCancelled(true); // prevent from sending message

        var userMessage = new Conversation.Message(Sender.USER, text, System.currentTimeMillis());

        var component = Component.empty().append(
                        Component.text("You").style(Style.style(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                ).appendSpace().append(Component.text(">").style(Style.style(NamedTextColor.RED)))
                .appendSpace().append(event.message());

        conversation.addMessage(userMessage);

        player.sendMessage(component);

        Bukkit.getPluginManager().callEvent(new PlayerConverseEvent(player, conversation, userMessage));
    }

}
