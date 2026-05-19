package io.rcw.vibemine.ai.events.conversation;

import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.events.VibeMineBaseEvent;
import org.bukkit.entity.Player;

public abstract class PlayerConversationEvent extends VibeMineBaseEvent {
    private final Player player;
    private final Conversation conversation;


    public PlayerConversationEvent(Player player, Conversation conversation) {
        super(true);

        this.player = player;
        this.conversation = conversation;
    }

    public Player getPlayer() {
        return player;
    }

    public Conversation getConversation() {
        return conversation;
    }

}
