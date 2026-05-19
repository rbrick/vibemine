package io.rcw.vibemine.ai.events.agent;

import io.rcw.vibemine.ai.agent.AgentResponse;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.events.VibeMineBaseEvent;
import org.bukkit.entity.Player;

public final class AsyncAgentResponseEvent extends VibeMineBaseEvent {
    private final Player player;
    private final Conversation conversation;
    private final AgentResponse agentResponse;


    public AsyncAgentResponseEvent(Player player, Conversation conversation, AgentResponse agentResponse) {
        super(true);

        this.player = player;
        this.conversation = conversation;
        this.agentResponse = agentResponse;
    }

    public Player getPlayer() {
        return player;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public AgentResponse getAgentResponse() {
        return agentResponse;
    }
}
