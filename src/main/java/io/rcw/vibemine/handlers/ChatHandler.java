package io.rcw.vibemine.handlers;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.rcw.vibemine.ai.chat.Conversation;
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

        event.setCancelled(true); // prevent from sending message




        conversation.

    }

}
