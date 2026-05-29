package io.rcw.vibemine.handlers;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.TokenEstimator;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.SystemPrompt;
import io.rcw.vibemine.ai.agent.ResponseType;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.ConversationStore;
import io.rcw.vibemine.ai.chat.Sender;
import io.rcw.vibemine.ai.events.agent.AsyncAgentResponseEvent;
import io.rcw.vibemine.ai.events.conversation.PlayerConverseEvent;
import io.rcw.vibemine.ai.plugin.VibedPluginManager;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;
import io.rcw.vibemine.code.Highlighting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AgentHandler implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String FUN_MESSAGE_GRADIENT = "red:gold:yellow:green:aqua:blue:light_purple:red";
    private static final int FUN_MESSAGE_DURATION_TICKS = 240; // 12 seconds at 20 TPS
    private static final double FUN_GRADIENT_CYCLE_TICKS = 240.0D;
    private static final List<String> THINKING_MESSAGES = List.of(
            "vibing...",
            "crafting...",
            "exploding...",
            "brewing ideas...",
            "summoning code...",
            "polishing pixels...",
            "wrangling creepers...",
            "dreaming in blocks..."
    );

    private final Agent agent;
    private final VibedPluginManager vibedPluginManager;
    private final ConversationStore conversationStore;
    private final ConcurrentMap<UUID, BukkitTask> thinkingActionBars = new ConcurrentHashMap<>();

    public AgentHandler(final Agent agent, VibedPluginManager vibedPluginManager, ConversationStore conversationStore) {
        this.agent = agent;
        this.vibedPluginManager = vibedPluginManager;
        this.conversationStore = conversationStore;
    }

    @EventHandler
    public void onConverse(final PlayerConverseEvent event) {
        startThinkingActionBar(event.getPlayer(), event.getConversation());
        agent.generateFromConversation(event.getConversation()).whenComplete((agentResponse, throwable) -> {
            stopThinkingActionBar(event.getPlayer());
            if (throwable != null) {
                event.getPlayer().sendMessage(Component.text("Viber tripped while thinking: " + throwable.getMessage(), NamedTextColor.RED));
                return;
            }
            if (agentResponse != null) {
                Bukkit.getPluginManager().callEvent(new AsyncAgentResponseEvent(
                        event.getPlayer(),
                        event.getConversation(),
                        agentResponse
                ));
            }
        });
    }

    @EventHandler
    public void onAgentResponse(final AsyncAgentResponseEvent event) {
        var agentResponse = event.getAgentResponse();

        if (agentResponse.kind() == ResponseType.CODE) {
            Bukkit.getScheduler().runTask(Vibemine.getInstance(), () -> {
                try {
                    var responseJson = agentResponse.responseJson();
                    if (responseJson == null || responseJson.isJsonNull()) {
                        event.getPlayer().sendMessage(Component.text("The model returned malformed plugin JSON. Nothing was loaded; please ask it to regenerate or simplify the plugin.", NamedTextColor.RED));
                        event.getConversation().addMessage(new Conversation.Message(Sender.AGENT,
                                "I returned malformed plugin JSON, so Vibemine did not load it. Please regenerate a smaller, valid plugin JSON response.", System.currentTimeMillis()));
                        conversationStore.save(event.getConversation());
                        return;
                    }
                    String pluginJson = Vibemine.GSON.toJson(responseJson);
                    sendGeneratedCodePreview(event, pluginJson);
                    var loaded = vibedPluginManager.saveAndLoad(pluginJson);
                    event.getPlayer().sendMessage(Component.text("Loaded vibed plugin: " + loaded.name(), NamedTextColor.GREEN));
                    event.getConversation().addMessage(new Conversation.Message(Sender.AGENT,
                            "Loaded vibed plugin: " + loaded.name(), System.currentTimeMillis()));
                    conversationStore.save(event.getConversation());
                } catch (Exception exception) {
                    event.getPlayer().sendMessage(Component.text("Could not load the generated plugin: " + exception.getMessage(), NamedTextColor.RED));
                }
            });
            return;
        }

        if (agentResponse.kind() == ResponseType.CODE_PATCH) {
            Bukkit.getScheduler().runTask(Vibemine.getInstance(), () -> {
                try {
                    var responseJson = agentResponse.responseJson();
                    if (responseJson == null || !responseJson.isJsonObject()) {
                        event.getPlayer().sendMessage(Component.text("The model returned a malformed plugin patch. Nothing was loaded.", NamedTextColor.RED));
                        return;
                    }
                    var patch = responseJson.getAsJsonObject();
                    String pluginName = patch.has("name") ? patch.get("name").getAsString() : "";
                    String before = vibedPluginManager.existingPluginJson(pluginName);
                    String after = vibedPluginManager.patchedJson(patch);
                    event.getPlayer().sendMessage(Component.text("VibePlugin patch diff:", NamedTextColor.GOLD));
                    event.getPlayer().sendMessage(Highlighting.diff(before, after));
                    var loaded = vibedPluginManager.saveAndLoad(after);
                    event.getPlayer().sendMessage(Component.text("Patched vibed plugin: " + loaded.name(), NamedTextColor.GREEN));
                    event.getConversation().addMessage(new Conversation.Message(Sender.AGENT,
                            "Patched vibed plugin: " + loaded.name(), System.currentTimeMillis()));
                    conversationStore.save(event.getConversation());
                } catch (Exception exception) {
                    event.getPlayer().sendMessage(Component.text("Could not patch the generated plugin: " + exception.getMessage(), NamedTextColor.RED));
                }
            });
            return;
        }

        if (agentResponse.kind() == ResponseType.ERROR) {
            sendAgentMessage(event, Component.text(agentResponse.responseText(), NamedTextColor.RED));
            return;
        }

        sendAgentMessage(event, Component.text(agentResponse.responseText()));
    }

    private void startThinkingActionBar(Player player, Conversation conversation) {
        stopThinkingActionBar(player);
        int estimatedPromptTokens = estimatePromptTokens(conversation);
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Vibemine.getInstance(), () -> {
            if (!player.isOnline()) {
                stopThinkingActionBar(player);
                return;
            }

            int currentTick = tick[0]++;
            String message = THINKING_MESSAGES.get((currentTick / FUN_MESSAGE_DURATION_TICKS) % THINKING_MESSAGES.size());
            String tokenText = "~" + TokenEstimator.compact(estimatedPromptTokens) + " prompt tokens";
            double gradientPhase = Math.sin((currentTick / FUN_GRADIENT_CYCLE_TICKS) * Math.PI * 2.0D);
            player.sendActionBar(MINI_MESSAGE.deserialize("<bold><gradient:" + FUN_MESSAGE_GRADIENT + ":" + gradientPhase + ">" + message + "</gradient></bold> <gray>(" + tokenText + ")</gray>"));
        }, 0L, 1L);
        thinkingActionBars.put(player.getUniqueId(), task);
    }

    private int estimatePromptTokens(Conversation conversation) {
        StringBuilder prompt = new StringBuilder(SystemPrompt.SYSTEM_PROMPT).append('\n');
        conversation.getSummary().ifPresent(summary -> prompt.append(summary).append('\n'));
        prompt.append(conversation.formatChatHistory(Conversation.CHAT_HISTORY_LIMIT)).append('\n');
        conversation.getMessages().stream()
                .filter(message -> message.sender() == Sender.USER)
                .reduce((first, second) -> second)
                .ifPresent(message -> prompt.append(message.message()));
        return TokenEstimator.estimate(prompt.toString());
    }

    private void stopThinkingActionBar(Player player) {
        BukkitTask task = thinkingActionBars.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        Bukkit.getScheduler().runTask(Vibemine.getInstance(), () -> player.sendActionBar(Component.empty()));
    }

    private void sendGeneratedCodePreview(AsyncAgentResponseEvent event, String pluginJson) {
        event.getPlayer().sendMessage(Component.text("Generated VibePlugin JSON:", NamedTextColor.GOLD));
        event.getPlayer().sendMessage(Highlighting.json(pluginJson));

        VibedPluginSchema schema = Vibemine.GSON.fromJson(pluginJson, VibedPluginSchema.class);
        if (schema == null) return;

        if (schema.globals() != null && !schema.globals().isBlank()) {
            event.getPlayer().sendMessage(Component.text("globals.js", NamedTextColor.GOLD));
            event.getPlayer().sendMessage(Highlighting.javascript(schema.globals()));
        }
        if (schema.commands() != null) {
            schema.commands().forEach(command -> {
                event.getPlayer().sendMessage(Component.text("command/" + command.label() + ".js", NamedTextColor.GOLD));
                event.getPlayer().sendMessage(Highlighting.javascript(command.code()));
            });
        }
        if (schema.events() != null) {
            schema.events().forEach(eventSchema -> {
                event.getPlayer().sendMessage(Component.text("event/" + eventSchema.event() + ".js", NamedTextColor.GOLD));
                event.getPlayer().sendMessage(Highlighting.javascript(eventSchema.code()));
            });
        }
    }

    private void sendAgentMessage(AsyncAgentResponseEvent event, Component message) {
        event.getConversation().addMessage(new Conversation.Message(Sender.AGENT,
                PlainTextComponentSerializer.plainText().serialize(message), System.currentTimeMillis()));
        conversationStore.save(event.getConversation());

        var component = Component.empty()
                .append(Component.text("Viber").style(Style.style(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)))
                .appendSpace()
                .append(Component.text(">").style(Style.style(NamedTextColor.RED)))
                .append(Component.text(" ").style(Style.empty())).appendSpace()
                .append(message);

        event.getPlayer().sendMessage(component);
    }
}
