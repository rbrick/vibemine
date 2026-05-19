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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static io.rcw.vibemine.ai.agent.SystemPrompt.BASIC_SYSTEM_PROMPT;

public class OpenAIAgent extends Agent {
    private static final String OPEN_AI_SUMMARIZE_MODEL = "gpt-5.4-nano";

    private static final String SUMMARIZE_PROMPT = """
                    You are maintaining memory for a Minecraft server AI agent.
            
                    Existing summary:
                    %s
            
                    New conversation turns:
                    %s
            
                    Update the summary.
            
                    Rules:
                    - Keep it concise.
                    - Preserve facts useful for future replies.
                    - Preserve user instructions and preferences.
                    - Preserve active tasks and unresolved bugs.
                    - Remove greetings, repetition, and one-off chatter.
                    - Do not answer the user.
                    - Do not invent facts.
            
                    Output only the updated summary.
            """;

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

        final int start = Math.max(0, conversations.size() - Conversation.CHAT_HISTORY_LIMIT);

        conversations.subList(start, conversations.size())
                .forEach(message -> {
                    switch (message.sender()) {
                        case USER -> messages.add(ChatCompletionMessageParam.ofUser(
                                toUserMessage(message.message())
                        ));

                        case AGENT -> messages.add(ChatCompletionMessageParam.ofAssistant(
                                toAssistantMessage(message.message())
                        ));
                    }
                });



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

    @Override
    public CompletableFuture<String> summarize(Conversation conversation) {
        return null;
    }

    private ChatCompletionUserMessageParam toUserMessage(final String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private ChatCompletionAssistantMessageParam toAssistantMessage(final String content) {
        return ChatCompletionAssistantMessageParam.builder().content(content).build();
    }



}
