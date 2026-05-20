package io.rcw.vibemine.ai.agent;

import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.tools.Tool;

import java.util.concurrent.CompletableFuture;

public abstract class Agent {

    private final String model, apiKey;

    public Agent(final String model, final String apiKey) {
        this.model = model;
        this.apiKey = apiKey;
    }

    public abstract CompletableFuture<AgentResponse> generateFromConversation(final Conversation conversation);

    public abstract CompletableFuture<String> summarize(final Conversation conversation);

    // registers a tool
    public abstract void registerTool(Tool<?, ?> tool);


    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }
}
