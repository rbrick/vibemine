package io.rcw.vibemine.ai.agent.impl;

import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import io.rcw.vibemine.ai.agent.Agent;

public abstract class OpenAIAgent extends Agent {
    private final OpenAIClientAsync  openAIClient;

    public OpenAIAgent(String model, String apiKey) {
        super(model, apiKey);
        this.openAIClient = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).build();
    }
}
