package io.rcw.vibemine.ai.agent.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.TokenEstimator;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.AgentResponse;
import io.rcw.vibemine.ai.agent.SystemPrompt;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import io.rcw.vibemine.ai.tools.Tool;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.rcw.vibemine.ai.agent.SystemPrompt.SYSTEM_PROMPT;

public final class OpenAIAgent extends Agent {
    private static final String SUMMARIZE_MODEL = "gpt-5.4-nano";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final OpenAIClientAsync client;
    private final Map<String, OpenAITool> tools = new LinkedHashMap<>();

    public OpenAIAgent(String model, String apiKey) {
        super(model);
        this.client = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).build();
    }

    @Override
    public CompletableFuture<AgentResponse> generateFromConversation(Conversation conversation) {
        var latestUserMessage = latestUserMessage(conversation);
        return summarize(conversation).thenCompose(summary -> {
            var messages = initialMessages(summary, latestUserMessage.message());
            return complete(messages).thenCompose(chat -> processChat(conversation, messages, chat, 0));
        });
    }

    @Override
    public CompletableFuture<String> summarize(Conversation conversation) {
        var messages = List.of(
                system("You summarize conversation history for an AI agent. Output only the updated summary."),
                user(SystemPrompt.SUMMARIZE_PROMPT.formatted(
                        conversation.getSummary().orElse("unavailable"),
                        conversation.formatChatHistory(Conversation.CHAT_HISTORY_LIMIT)
                ))
        );

        return client.chat().completions()
                .create(ChatCompletionCreateParams.builder().model(SUMMARIZE_MODEL).messages(messages).build())
                .thenApply(chat -> chat.choices().getFirst().message().content().orElse("failed to get response"));
    }

    @Override
    public void registerTool(Tool<?, ?> tool) {
        var openAITool = new OpenAITool(tool);
        tools.put(openAITool.name(), openAITool);
    }

    private CompletableFuture<AgentResponse> processChat(Conversation conversation, List<ChatCompletionMessageParam> messages, ChatCompletion chat, int step) {
        if (chat.choices().isEmpty()) {
            return completedError("The model returned no choices. Please try again.");
        }

        var choice = chat.choices().getFirst();
        var message = choice.message();
        if (message.toolCalls().isEmpty()) {
            return CompletableFuture.completedFuture(AgentResponse.parse(finalMessageOrError(choice, step)));
        }

        messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));
        message.toolCalls().get().forEach(toolCall -> {
            var function = toolCall.asFunction();
            if (!function.isValid()) return;

            var name = function.function().name();
            var args = function.function().arguments();
            var player = conversation.getPlayer();

            notifyToolCall(player, name, args);
            var result = callTool(conversation, name, args);
            notifyToolResult(player, name, args, result);
            conversation.addEstimatedToolTokens(estimateToolCallTokens(name, args, result));

            messages.add(ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
                    .toolCallId(function.id())
                    .content(result)
                    .build()));
        });

        return complete(messages).thenCompose(next -> processChat(conversation, messages, next, step + 1));
    }

    private CompletableFuture<ChatCompletion> complete(List<ChatCompletionMessageParam> messages) {
        return client.chat().completions().create(createParams(messages));
    }

    private ChatCompletionCreateParams createParams(List<ChatCompletionMessageParam> messages) {
        var builder = ChatCompletionCreateParams.builder().model(getModel()).messages(messages);
        tools.values().forEach(tool -> builder.addTool(tool.openAITool()));
        return builder.build();
    }

    private List<ChatCompletionMessageParam> initialMessages(String summary, String latestUserMessage) {
        return new ArrayList<>(List.of(
                system(SYSTEM_PROMPT),
                system("Prior chat summary for context only. Do not answer the summary. Only answer the latest user message.\n\n" + summary),
                user(latestUserMessage)
        ));
    }

    private Conversation.Message latestUserMessage(Conversation conversation) {
        return conversation.getMessages().stream()
                .filter(message -> message.sender() == Sender.USER)
                .max(Comparator.comparingLong(Conversation.Message::timestamp))
                .orElseThrow(() -> new IllegalStateException("No user message found"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String callTool(Conversation conversation, String name, String args) {
        var openAITool = tools.get(name);
        if (openAITool == null) return "Unknown tool: " + name;

        try {
            var tool = (Tool) openAITool.tool();
            var input = tool.inputClass() == Void.class ? null : Vibemine.GSON.fromJson(args, tool.inputClass());
            return Vibemine.GSON.toJson(tool.execute(conversation.getPlayer(), input));
        } catch (Exception exception) {
            return "Tool failed: " + exception.getMessage();
        }
    }

    private void notifyToolCall(Player player, String name, String args) {
        sendToolMessage(player, switch (name) {
            case "api_reference" -> "&8&olooking at api for " + jsonArg(args, "type", "available types");
            case "file_read" -> "&8&oreading file " + fileDescription(args);
            case "file_edit" -> "&8&oediting file " + fileDescription(args);
            case "file_write" -> "&8&owriting file " + fileDescription(args);
            case "plugin_context" -> "&8&ochecking existing vibed plugins";
            case "syntax_highlight" -> "&8&opreviewing generated code";
            case "command" -> "&8&orunning command " + jsonArg(args, "command", "");
            case "ray_trace" -> "&8&olooking where you are pointing";
            case "spawn" -> "&8&ospawning entity";
            case "send_message" -> "&8&osending message";
            default -> "&8&ousing tool " + name;
        });
    }

    private void notifyToolResult(Player player, String name, String args, String result) {
        if (!toolSucceeded(result)) return;
        if (name.equals("file_write")) sendToolMessage(player, "&8&ocreated file " + fileDescription(args));
        if (name.equals("file_edit")) sendToolMessage(player, "&8&oedited file " + fileDescription(args));
    }

    private void sendToolMessage(Player player, String legacyMessage) {
        if (player == null) return;
        Runnable send = () -> {
            if (player.isOnline()) player.sendMessage(LEGACY.deserialize(legacyMessage));
        };
        if (Bukkit.isPrimaryThread()) send.run();
        else Bukkit.getScheduler().runTask(Vibemine.getInstance(), send);
    }

    private String finalMessageOrError(ChatCompletion.Choice choice, int step) {
        var message = choice.message();
        var content = message.content();
        if (content.isPresent() && !content.get().isBlank()) return content.get();

        var refusal = message.refusal();
        if (refusal.isPresent() && !refusal.get().isBlank()) return errorResponse("The model refused the request: " + refusal.get());

        var finishReason = String.valueOf(choice.finishReason());
        var lowerReason = finishReason.toLowerCase(Locale.ROOT);
        if (message.toolCalls().isPresent()) return errorResponse("The model returned tool calls after " + step + " rounds, but tool execution could not continue. Try a smaller request.");
        if (lowerReason.contains("length")) return errorResponse("The model hit its output limit. Try a smaller first version, then add features incrementally.");
        if (lowerReason.contains("content_filter")) return errorResponse("The provider filtered the response. Try rephrasing the request.");
        return errorResponse("The model returned an empty message. Finish reason: " + finishReason + ".");
    }

    private CompletableFuture<AgentResponse> completedError(String message) {
        return CompletableFuture.completedFuture(AgentResponse.parse(errorResponse(message)));
    }

    private String errorResponse(String message) {
        var object = new JsonObject();
        object.addProperty("type", "ERROR");
        object.addProperty("response", message);
        return Vibemine.GSON.toJson(object);
    }

    private boolean toolSucceeded(String result) {
        try {
            var object = JsonParser.parseString(result == null ? "{}" : result).getAsJsonObject();
            return !object.has("success") || object.get("success").getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String fileDescription(String args) {
        return jsonArg(args, "file", "unknown").replace("commands/", "command/").replace("events/", "event/");
    }

    private String jsonArg(String json, String key, String fallback) {
        try {
            var object = JsonParser.parseString(json == null ? "{}" : json).getAsJsonObject();
            if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
            var value = object.get(key).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int estimateToolCallTokens(String name, String args, String result) {
        return TokenEstimator.estimate(name) + TokenEstimator.estimate(args) + TokenEstimator.estimate(result);
    }

    private ChatCompletionMessageParam system(String content) {
        return ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder().content(content).build());
    }

    private ChatCompletionMessageParam user(String content) {
        return ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(content).build());
    }
}
