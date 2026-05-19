package io.rcw.vibemine.ai.events.conversation;

import io.rcw.vibemine.ai.chat.Conversation;
import org.bukkit.entity.Player;

public final class ConversationStartEvent extends PlayerConversationEvent {

    public ConversationStartEvent(final Player player, final Conversation conversation) {
        super(player, conversation);
    }

}
