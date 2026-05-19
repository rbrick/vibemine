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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static io.rcw.vibemine.ai.agent.SystemPrompt.BASIC_SYSTEM_PROMPT;

public class OpenAIAgent extends Agent {
    private static final String OPEN_AI_SUMMARIZE_MODEL = "gpt-5.4-nano";

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

        conversations.sort(Comparator.comparingLong(Conversation.Message::timestamp));
        var latestUserMessage = conversations.stream()
                .filter(message -> message.sender() == Sender.USER)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("No user message found"));

        System.out.println(latestUserMessage.message() + " " + latestUserMessage.timestamp());

        return this.summarize(conversation).thenCompose(
                summary -> {
                    // add the system prompts
                    // the system prompt
                    messages.add(
                            ChatCompletionMessageParam.ofSystem(
                                    toSystemMessage(BASIC_SYSTEM_PROMPT)
                            )
                    );

                    // chat summary
                    messages.add(
                            ChatCompletionMessageParam.ofSystem(
                                    toSystemMessage(
                                            String.format("""
                                                    Prior chat summary for context only.
                                                    Do not answer the summary.
                                                    Only answer the latest user message.
                                                    
                                                    %s
                                                    """, summary))
                            )
                    );

                    // add the latest user message
                    messages.add(
                            ChatCompletionMessageParam.ofUser(
                                    toUserMessage(latestUserMessage.message())));

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
        );
    }

    @Override
    public CompletableFuture<String> summarize(Conversation conversation) {
        var chatHistory = conversation.formatChatHistory(Conversation.CHAT_HISTORY_LIMIT);
        var lastSummary = conversation.getSummary().orElse("unavailable");
        var systemMessage = ChatCompletionSystemMessageParam.builder()
                .content(SystemPrompt.SUMMARIZE_PROMPT.formatted(lastSummary, chatHistory)).build();

        return openAIClient.chat()
                .completions()
                .create(ChatCompletionCreateParams.builder()
                        .model(OPEN_AI_SUMMARIZE_MODEL)
                        .messages(List.of(ChatCompletionMessageParam.ofSystem(systemMessage))).build())
                .thenApply((chatCompletion) -> chatCompletion
                        .choices()
                        .getFirst()
                        .message()
                        .content()
                        .orElse("failed to get response"));
    }

    private ChatCompletionSystemMessageParam toSystemMessage(final String content) {
        return ChatCompletionSystemMessageParam.builder().content(content).build();
    }

    private ChatCompletionUserMessageParam toUserMessage(final String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private ChatCompletionAssistantMessageParam toAssistantMessage(final String content) {
        return ChatCompletionAssistantMessageParam.builder().content(content).build();
    }



}
