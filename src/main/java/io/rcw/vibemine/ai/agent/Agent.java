package io.rcw.vibemine.ai.agent;

import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;

import java.util.List;

public abstract class Agent {
    private final String model, apiKey;

    public Agent(final String model, final String apiKey) {
        this.model = model;
        this.apiKey = apiKey;
    }

    public abstract VibedPluginSchema generateFromConversation(final Conversation conversation);

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }
}
