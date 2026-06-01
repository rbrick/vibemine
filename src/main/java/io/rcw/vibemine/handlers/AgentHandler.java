package io.rcw.vibemine.handlers;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.TokenEstimator;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.AgentResponse;
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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class AgentHandler implements Listener {
    private final Agent agent;
    private final VibedPluginManager plugins;
    private final ConversationStore conversations;
    private final ConversationActionBar actionBar;

    public AgentHandler(Agent agent, VibedPluginManager plugins, ConversationStore conversations, ConversationActionBar actionBar) {
        this.agent = agent;
        this.plugins = plugins;
        this.conversations = conversations;
        this.actionBar = actionBar;
    }

    @EventHandler
    public void onConverse(PlayerConverseEvent event) {
        actionBar.setThinking(event.getPlayer(), true);
        agent.generateFromConversation(event.getConversation()).whenComplete((response, throwable) -> {
            actionBar.setThinking(event.getPlayer(), false);
            if (throwable != null) {
                event.getPlayer().sendMessage(Component.text("Viber tripped while thinking: " + describe(throwable), NamedTextColor.RED));
                return;
            }
            if (response != null) Bukkit.getPluginManager().callEvent(new AsyncAgentResponseEvent(event.getPlayer(), event.getConversation(), response));
        });
    }

    @EventHandler
    public void onAgentResponse(AsyncAgentResponseEvent event) {
        var response = event.getAgentResponse();
        event.getConversation().addEstimatedOutputTokens(TokenEstimator.estimate(response.rawMessage()));

        if (response.kind() == ResponseType.CODE) {
            runSync(() -> loadGeneratedPlugin(event, response));
        } else if (response.kind() == ResponseType.CODE_PATCH) {
            runSync(() -> patchGeneratedPlugin(event, response));
        } else if (response.kind() == ResponseType.ERROR) {
            sendAgentMessage(event, Component.text(response.responseText(), NamedTextColor.RED));
        } else {
            sendAgentMessage(event, MiniMessage.miniMessage().deserialize(response.responseText()));
        }
    }

    private void loadGeneratedPlugin(AsyncAgentResponseEvent event, AgentResponse response) {
        try {
            var json = response.responseJson();
            if (json == null || json.isJsonNull()) {
                fail(event, "The model returned malformed plugin JSON. Nothing was loaded; please ask it to regenerate or simplify the plugin.");
                remember(event, "I returned malformed plugin JSON, so Vibemine did not load it. Please regenerate a smaller, valid plugin JSON response.");
                return;
            }

            var pluginJson = Vibemine.GSON.toJson(json);
            previewGeneratedPlugin(event, pluginJson);
            var loaded = plugins.saveAndLoad(pluginJson);
            success(event, "Loaded vibed plugin: " + loaded.name());
            remember(event, "Loaded vibed plugin: " + loaded.name());
        } catch (Exception exception) {
            fail(event, "Could not load the generated plugin: " + exception.getMessage());
        }
    }

    private void patchGeneratedPlugin(AsyncAgentResponseEvent event, AgentResponse response) {
        try {
            var json = response.responseJson();
            if (json == null || !json.isJsonObject()) {
                fail(event, "The model returned a malformed plugin patch. Nothing was loaded.");
                return;
            }

            var patch = json.getAsJsonObject();
            var pluginName = patch.has("name") ? patch.get("name").getAsString() : "";
            var before = plugins.existingPluginJson(pluginName);
            var after = plugins.patchedJson(patch);

            event.getPlayer().sendMessage(Component.text("VibePlugin patch diff:", NamedTextColor.GOLD));
            event.getPlayer().sendMessage(Highlighting.diff(before, after));

            var loaded = plugins.saveAndLoad(after);
            success(event, "Patched vibed plugin: " + loaded.name());
            remember(event, "Patched vibed plugin: " + loaded.name());
        } catch (Exception exception) {
            fail(event, "Could not patch the generated plugin: " + exception.getMessage());
        }
    }

    private void previewGeneratedPlugin(AsyncAgentResponseEvent event, String pluginJson) {
        event.getPlayer().sendMessage(Component.text("Generated VibePlugin JSON:", NamedTextColor.GOLD));
        event.getPlayer().sendMessage(Highlighting.json(pluginJson));

        var schema = Vibemine.GSON.fromJson(pluginJson, VibedPluginSchema.class);
        if (schema == null) return;

        previewFile(event, schema.globalsPath() == null || schema.globalsPath().isBlank() ? "globals.js" : schema.globalsPath(), schema.globals());
        if (schema.commands() != null) schema.commands().forEach(command -> previewFile(event, "command/" + command.label() + ".js", command.code()));
        if (schema.events() != null) schema.events().forEach(e -> previewFile(event, "event/" + e.event() + ".js", e.code()));
    }

    private void previewFile(AsyncAgentResponseEvent event, String name, String source) {
        if (source == null || source.isBlank()) return;
        event.getPlayer().sendMessage(Component.text(name, NamedTextColor.GOLD));
        event.getPlayer().sendMessage(Highlighting.javascript(source));
    }

    private void sendAgentMessage(AsyncAgentResponseEvent event, Component message) {
        remember(event, PlainTextComponentSerializer.plainText().serialize(message));
        event.getPlayer().sendMessage(Component.empty()
                .append(Component.text("Viber").style(Style.style(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)))
                .appendSpace()
                .append(Component.text(">", NamedTextColor.RED))
                .appendSpace()
                .append(message));
    }

    private void remember(AsyncAgentResponseEvent event, String message) {
        event.getConversation().addMessage(new Conversation.Message(Sender.AGENT, message, System.currentTimeMillis()));
        conversations.save(event.getConversation());
    }

    private void success(AsyncAgentResponseEvent event, String message) {
        event.getPlayer().sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private void fail(AsyncAgentResponseEvent event, String message) {
        event.getPlayer().sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(Vibemine.getInstance(), task);
    }

    private String describe(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && (current instanceof CompletionException || current instanceof ExecutionException)) {
            current = current.getCause();
        }
        var message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
