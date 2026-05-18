package io.rcw.vibemine.ai.agent.impl;

import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.chat.completions.*;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.SystemPrompt;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;

import java.util.ArrayList;
import java.util.Collections;

public class OpenAIAgent extends Agent {
    private final OpenAIClientAsync  openAIClient;

    private final ChatCompletionSystemMessageParam systemPrompt;

    public OpenAIAgent(String model, String apiKey) {
        super(model, apiKey);
        this.openAIClient = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).build();

        this.systemPrompt =
                ChatCompletionSystemMessageParam.builder().content(SystemPrompt.SYSTEM_PROMPT).build();
    }

    @Override
    public VibedPluginSchema generateFromConversation(Conversation conversation) {
        final var messages = new ArrayList<ChatCompletionMessageParam>();
        final var conversations = new ArrayList<>(conversation.getMessages()).reversed();
        // always add system prompt first
        messages.addFirst(ChatCompletionMessageParam.ofSystem(systemPrompt));

        // add chat history
        conversations.stream().limit(Conversation.CHAT_HISTORY_LIMIT).map(message -> {
            if (message.sender() ==  Sender.SYSTEM) {
                return ChatCompletionMessageParam.ofSystem(toSystemMessage(message.message()));
            }
            return ChatCompletionMessageParam.ofUser(toUserMessage(message.message()));
        }).forEach(messages::add);


        openAIClient.chat().completions().create(ChatCompletionCreateParams.builder().model(this.getModel()).messages(messages).build());

        return null;
    }

    private ChatCompletionUserMessageParam toUserMessage(final String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private ChatCompletionSystemMessageParam toSystemMessage(final String content) {
        return ChatCompletionSystemMessageParam.builder().content(content).build();
    }



}
