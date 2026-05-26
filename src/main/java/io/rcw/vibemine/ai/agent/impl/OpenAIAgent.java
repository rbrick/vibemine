package io.rcw.vibemine.ai.agent.impl;

import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.chat.completions.*;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.AgentResponse;
import io.rcw.vibemine.ai.agent.SystemPrompt;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.tools.Tool;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static io.rcw.vibemine.ai.agent.SystemPrompt.SYSTEM_PROMPT;

public final class OpenAIAgent extends Agent {
    private static final String OPEN_AI_SUMMARIZE_MODEL = "gpt-5.4-nano";

    private final OpenAIClientAsync  openAIClient;


    private final Map<String, OpenAITool> tools = new LinkedHashMap<>();

    public OpenAIAgent(String model, String apiKey) {
        super(model, apiKey);
        this.openAIClient = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).build();

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

        return this.summarize(conversation).thenCompose(
                summary -> {

                    System.out.println(summary);
                    // add the system prompts
                    // the system prompt
                    messages.add(
                            ChatCompletionMessageParam.ofSystem(
                                    toSystemMessage(SYSTEM_PROMPT)
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
                            .create(createParams(messages))
                            .thenCompose((chat) -> this.processChat(conversation, messages, chat, 0));
                }
        );
    }

    private CompletableFuture<AgentResponse> processChat(Conversation conversation, List<ChatCompletionMessageParam> messages, ChatCompletion completion, int step) {
        var message = completion.choices().getFirst().message();

        if (message.toolCalls().isPresent() && step < 8) {
            messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));

            message.toolCalls().get().forEach(toolCall -> {
                // always going to be a function in our case
                var function = toolCall.asFunction();

                if (function.isValid()) {
                    // name of the tool
                    var functionName = function.function().name();
                    // args
                    var functionArgs = function.function().arguments();

                    var toolResult = this.callTool(conversation, functionName, functionArgs);

                    System.out.println("Tool Result: " + toolResult);
                    messages.add(ChatCompletionMessageParam.ofTool(
                            ChatCompletionToolMessageParam.builder()
                                    .toolCallId(function.id())
                                    .content(toolResult)
                                    .build()
                    ));
                }
            });

            return openAIClient.chat()
                    .completions()
                    .create(createParams(messages))
                    .thenCompose((chat) -> this.processChat(conversation, messages, chat, step + 1));
        }

        return CompletableFuture.completedFuture(AgentResponse.parse(
                message.content().orElse("failed to get response")
        ));
    }

    @Override
    public CompletableFuture<String> summarize(Conversation conversation) {
        var chatHistory = conversation.formatChatHistory(Conversation.CHAT_HISTORY_LIMIT);
        var lastSummary = conversation.getSummary().orElse("unavailable");
        var messages = List.of(
                ChatCompletionMessageParam.ofSystem(
                        toSystemMessage("You summarize conversation history for an AI agent. Output only the updated summary.")
                ),
                ChatCompletionMessageParam.ofUser(
                        toUserMessage(SystemPrompt.SUMMARIZE_PROMPT.formatted(lastSummary, chatHistory))
                )
        );
        return openAIClient.chat()
                .completions()
                .create(ChatCompletionCreateParams.builder()
                        .model(OPEN_AI_SUMMARIZE_MODEL)
                        .messages(messages).build())
                .thenApply((chatCompletion) -> chatCompletion
                        .choices()
                        .getFirst()
                        .message()
                        .content()
                        .orElse("failed to get response"));
    }

    @Override
    public void registerTool(Tool<?, ?> tool) {
        var openAITool = new OpenAITool(tool);
        this.tools.put(openAITool.name(), openAITool);
    }

    private ChatCompletionCreateParams createParams(List<ChatCompletionMessageParam> messages) {
        var builder = ChatCompletionCreateParams.builder()
                .model(this.getModel())
                .messages(messages);

        this.tools.values().forEach(tool -> builder.addTool(tool.openAITool()));

        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String callTool(Conversation conversation, String functionName, String functionArgs) {
        var openAITool = this.tools.get(functionName);
        if (openAITool == null) {
            return "Unknown tool: " + functionName;
        }

        try {
            var tool = (Tool) openAITool.tool();
            Object input = null;
            if (tool.inputClass() != Void.class) {
                input = Vibemine.GSON.fromJson(functionArgs, tool.inputClass());
            }

            var result = tool.execute(conversation.getPlayer(), input);
            System.out.println(Vibemine.GSON.toJson(result));
            return Vibemine.GSON.toJson(result);
        } catch (Exception exception) {
            return "Tool failed: " + exception.getMessage();
        }
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
