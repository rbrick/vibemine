package io.rcw.vibemine.handlers;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatHandler implements Listener {

    public ChatHandler() {}



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

        Conversation conversation = Conversation.forPlayer(player);
        var text = PlainTextComponentSerializer.plainText().serialize(event.message());

        event.setCancelled(true); // prevent from sending message
        conversation.addMessage(new Conversation.Message(Sender.USER, text, System.currentTimeMillis()));
    }

}
