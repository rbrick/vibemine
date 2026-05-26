package io.rcw.vibemine.handlers;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.agent.Agent;
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
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class AgentHandler implements Listener {
    private final Agent agent;
    private final VibedPluginManager vibedPluginManager;
    private final ConversationStore conversationStore;

    public AgentHandler(final Agent agent, VibedPluginManager vibedPluginManager, ConversationStore conversationStore) {
        this.agent = agent;
        this.vibedPluginManager = vibedPluginManager;
        this.conversationStore = conversationStore;
    }

    @EventHandler
    public void onConverse(final PlayerConverseEvent event) {
        agent.generateFromConversation(event.getConversation()).thenAccept(agentResponse -> {
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
                    String pluginJson = Vibemine.GSON.toJson(agentResponse.responseJson());
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

        if (agentResponse.kind() == ResponseType.ERROR) {
            sendAgentMessage(event, Component.text(agentResponse.responseText(), NamedTextColor.RED));
            return;
        }

        sendAgentMessage(event, Component.text(agentResponse.responseText()));
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
