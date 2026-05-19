package io.rcw.vibemine.ai.chat;

import io.rcw.vibemine.ai.events.conversation.ConversationStartEvent;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Conversation {

    public static final int CHAT_HISTORY_LIMIT = 100;

    public record Message(Sender sender, String message, long timestamp) {}

    public static final Map<UUID, Conversation> conversing = new ConcurrentHashMap<>();

    private final List<Message> messages = new LinkedList<>();
    private final UUID playerId, sessionId;

    public Conversation(UUID playerId, UUID sessionId, Collection<Message> messages) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.messages.addAll(messages);
    }

    public Conversation(UUID playerId) {
        // create a new session id
        this.sessionId = UUID.randomUUID();
        this.playerId = playerId;
    }

    public static Conversation beginConversation(final Player player) {
        // start a conversation with the user
        final UUID conversationKey = player.getUniqueId();

        if (conversing.containsKey(conversationKey)) {
            return conversing.get(conversationKey);
        }

        var conversation = new Conversation(conversationKey);
        conversing.put(conversationKey, conversation);

        // fire a new event for the new conversation - note: only fires ONCE!
        Bukkit.getPluginManager().callEvent(new ConversationStartEvent(player, conversation));

        return conversation;
    }

    public static boolean isConversing(final Player player) {
        return conversing.containsKey(player.getUniqueId());
    }

    public static void clearConversing(Set<Audience> audiences) {
        audiences.removeIf((audience -> {
            if (!(audience instanceof Player player)) return false;
            return conversing.containsKey(player.getUniqueId());
        }));
    }

    public static Conversation forPlayer(final Player player) {
        return conversing.get(player.getUniqueId());
    }

    public void addMessage(final Message message) {
        this.messages.add(message);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public List<Message> getMessages() {
        return this.messages;
    }
}
