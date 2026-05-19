package io.rcw.vibemine.ai.chat;

import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;

import java.util.concurrent.CompletableFuture;

public final class Summarization {

    private final OpenAIClientAsync client;
    private final String model;

    public Summarization(final String model, final String apiKey) {
        this.client = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).build();
        this.model = model;
    }


    public CompletableFuture<String> summarize(final Conversation conversation) {
        return CompletableFuture.completedFuture(""); // TODO(ryan): implement summarization
    }



}
