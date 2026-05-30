package io.rcw.vibemine.ai.agent.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.chat.completions.*;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.AgentResponse;
import io.rcw.vibemine.ai.agent.SystemPrompt;
import io.rcw.vibemine.ai.TokenEstimator;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.tools.Tool;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static io.rcw.vibemine.ai.agent.SystemPrompt.SYSTEM_PROMPT;

public final class OpenAIAgent extends Agent {
    private static final String OPEN_AI_SUMMARIZE_MODEL = "gpt-5.4-nano";
    private static final int MAX_TOOL_STEPS = 16;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

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
        if (completion.choices().isEmpty()) {
            return CompletableFuture.completedFuture(AgentResponse.parse(errorResponse("The model returned no choices. This can happen during a provider-side failure; please try again.")));
        }

        var choice = completion.choices().getFirst();
        var message = choice.message();

        if (message.toolCalls().isPresent() && step < MAX_TOOL_STEPS) {
            messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));

            message.toolCalls().get().forEach(toolCall -> {
                // always going to be a function in our case
                var function = toolCall.asFunction();

                if (function.isValid()) {
                    // name of the tool
                    var functionName = function.function().name();
                    // args
                    var functionArgs = function.function().arguments();

                    notifyToolCall(conversation.getPlayer(), functionName, functionArgs);
                    var toolResult = this.callTool(conversation, functionName, functionArgs);
                    notifyToolResult(conversation.getPlayer(), functionName, functionArgs, toolResult);
                    conversation.addEstimatedToolTokens(estimateToolCallTokens(functionName, functionArgs, toolResult));

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

        return CompletableFuture.completedFuture(AgentResponse.parse(finalMessageOrError(choice, step)));
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

    private void notifyToolCall(Player player, String functionName, String functionArgs) {
        String message = switch (functionName) {
            case "api_reference" -> "&8&olooking at api for " + arg(functionArgs, "type", "available types");
            case "file_read" -> "&8&oreading file " + fileDescription(functionArgs);
            case "file_edit" -> "&8&oediting file " + fileDescription(functionArgs);
            case "file_write" -> "&8&owriting file " + fileDescription(functionArgs);
            case "plugin_context" -> "&8&ochecking existing vibed plugins";
            case "syntax_highlight" -> "&8&opreviewing generated code";
            case "command" -> "&8&orunning command " + arg(functionArgs, "command", "");
            case "ray_trace" -> "&8&olooking where you are pointing";
            case "spawn" -> "&8&ospawning entity";
            case "send_message" -> "&8&osending message";
            default -> "&8&ousing tool " + functionName;
        };
        sendToolMessage(player, message);
    }

    private void notifyToolResult(Player player, String functionName, String functionArgs, String toolResult) {
        if (!toolSucceeded(toolResult)) return;
        String message = switch (functionName) {
            case "file_write" -> "&8&ocreated file " + fileDescription(functionArgs);
            case "file_edit" -> "&8&oedited file " + fileDescription(functionArgs);
            default -> null;
        };
        if (message != null) sendToolMessage(player, message);
    }

    private void sendToolMessage(Player player, String legacyMessage) {
        if (player == null) return;
        Runnable send = () -> {
            if (player.isOnline()) player.sendMessage(LEGACY.deserialize(legacyMessage));
        };
        if (Bukkit.isPrimaryThread()) send.run();
        else Bukkit.getScheduler().runTask(Vibemine.getInstance(), send);
    }

    private String fileDescription(String functionArgs) {
        String file = arg(functionArgs, "file", "unknown");
        return file.replace("commands/", "command/").replace("events/", "event/");
    }

    private String arg(String functionArgs, String key, String fallback) {
        try {
            JsonObject object = JsonParser.parseString(functionArgs == null ? "{}" : functionArgs).getAsJsonObject();
            if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
            String value = object.get(key).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean toolSucceeded(String toolResult) {
        try {
            JsonObject object = JsonParser.parseString(toolResult == null ? "{}" : toolResult).getAsJsonObject();
            return !object.has("success") || object.get("success").getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String finalMessageOrError(ChatCompletion.Choice choice, int step) {
        var message = choice.message();
        var content = message.content();
        if (content.isPresent() && !content.get().isBlank()) return content.get();

        var refusal = message.refusal();
        if (refusal.isPresent() && !refusal.get().isBlank()) {
            return errorResponse("The model refused the request: " + refusal.get());
        }

        String finishReason = String.valueOf(choice.finishReason());
        if (message.toolCalls().isPresent()) {
            return errorResponse("The model was still making tool calls after " + step + " rounds, so I stopped it before it could finish. Try asking for a smaller first version, or ask me to continue/extend the plugin in steps.");
        }
        if (finishReason.toLowerCase(Locale.ROOT).contains("length")) {
            return errorResponse("The model hit its output limit before returning a final response. Try asking for a smaller first version, then add features incrementally.");
        }
        if (finishReason.toLowerCase(Locale.ROOT).contains("content_filter")) {
            return errorResponse("The provider filtered the response before any content was returned. Try rephrasing the request with safer wording.");
        }
        return errorResponse("The model returned an empty message. Finish reason: " + finishReason + ". Please try again or break the request into smaller steps.");
    }

    private String errorResponse(String message) {
        var object = new com.google.gson.JsonObject();
        object.addProperty("type", "ERROR");
        object.addProperty("response", message);
        return Vibemine.GSON.toJson(object);
    }

    private int estimateToolCallTokens(String functionName, String functionArgs, String toolResult) {
        // Tool call arguments and tool results are both appended to the chat transcript and
        // included in follow-up requests, so count them as input-context tokens.
        return TokenEstimator.estimate(functionName) + TokenEstimator.estimate(functionArgs) + TokenEstimator.estimate(toolResult);
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
