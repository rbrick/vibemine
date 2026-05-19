package io.rcw.vibemine.ai.events.conversation;

import io.rcw.vibemine.ai.chat.Conversation;
import org.bukkit.entity.Player;

public final class PlayerConverseEvent extends PlayerConversationEvent {

    private final Conversation.Message message;

    public PlayerConverseEvent(final Player player, final Conversation conversation, final Conversation.Message message) {
        super(player, conversation);
        this.message = message;
    }

    public Conversation.Message getMessage() {
        return this.message;
    }


}
