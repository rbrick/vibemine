package io.rcw.vibemine.handlers;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import io.rcw.vibemine.ai.events.agent.AsyncAgentResponseEvent;
import io.rcw.vibemine.ai.events.conversation.PlayerConverseEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class AgentHandler implements Listener {
    private final Agent agent;

    public AgentHandler(final Agent agent) {
        this.agent = agent;
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
        var llmResponse = JSONComponentSerializer.json().deserialize(event.getAgentResponse().rawMessage());

        event.getConversation().addMessage(new Conversation.Message(Sender.AGENT,
                PlainTextComponentSerializer.plainText().serialize(llmResponse), System.currentTimeMillis()));

        var component = Component.empty()
                .append(Component.text("Viber").style(Style.style(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)))
                .appendSpace()
                .append(Component.text(">").style(Style.style(NamedTextColor.RED)))
                .append(Component.text(" ").style(Style.empty())).appendSpace()
                .append(llmResponse);



        System.out.println(

                Vibemine.GSON.toJson(event.getConversation().getMessages())
        );

        event.getPlayer().sendMessage(component);
    }
}
