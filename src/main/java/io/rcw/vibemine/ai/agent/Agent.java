package io.rcw.vibemine.ai.agent;

import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.tools.Tool;

import java.util.concurrent.CompletableFuture;

public abstract class Agent {

    private final String model;

    public Agent(final String model) {
        this.model = model;
    }

    public abstract CompletableFuture<AgentResponse> generateFromConversation(final Conversation conversation);

    public abstract CompletableFuture<String> summarize(final Conversation conversation);

    public abstract void registerTool(Tool<?, ?> tool);

    public String getModel() {
        return model;
    }
}
