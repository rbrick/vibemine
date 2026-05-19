package io.rcw.vibemine.ai.agent.impl;

import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.chat.completions.*;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.AgentResponse;
import io.rcw.vibemine.ai.agent.ResponseType;
import io.rcw.vibemine.ai.agent.SystemPrompt;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static io.rcw.vibemine.ai.agent.SystemPrompt.BASIC_SYSTEM_PROMPT;

public class OpenAIAgent extends Agent {
    private final OpenAIClientAsync  openAIClient;

    private final ChatCompletionSystemMessageParam systemPrompt;

    private final ChatCompletionSystemMessageParam basicSystemPrompt = ChatCompletionSystemMessageParam.builder().content(BASIC_SYSTEM_PROMPT).build();

    public OpenAIAgent(String model, String apiKey) {
        super(model, apiKey);
        this.openAIClient = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).build();

        this.systemPrompt =
                ChatCompletionSystemMessageParam.builder().content(SystemPrompt.SYSTEM_PROMPT).build();
    }

    @Override
    public CompletableFuture<AgentResponse> generateFromConversation(Conversation conversation) {
        final var messages = new ArrayList<ChatCompletionMessageParam>();
        final var conversations = new ArrayList<>(conversation.getMessages());
        // always add system prompt first
//        messages.addFirst(ChatCompletionMessageParam.ofSystem(systemPrompt));
        messages.addFirst(ChatCompletionMessageParam.ofSystem(
                basicSystemPrompt
        ));

        // add chat history
        conversations.stream().limit(Conversation.CHAT_HISTORY_LIMIT).map(message -> {
            if (message.sender() ==  Sender.AGENT) {
                return ChatCompletionMessageParam.ofAssistant(toAssistantMessage(message.message()));
            }
            return ChatCompletionMessageParam.ofUser(toUserMessage(message.message()));
        }).forEach(messages::add);

        return openAIClient.chat()
                        .completions()
                        .create(ChatCompletionCreateParams.builder()
                                .model(this.getModel())
                        .messages(messages).build())
                        .thenApply((chatCompletion) -> new AgentResponse(ResponseType.DEBUG, chatCompletion
                                .choices()
                                .getFirst()
                                .message()
                                .content()
                                .orElse("failed to get response")));
    }

    private ChatCompletionUserMessageParam toUserMessage(final String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private ChatCompletionAssistantMessageParam toAssistantMessage(final String content) {
        return ChatCompletionAssistantMessageParam.builder().content(content).build();
    }



}
