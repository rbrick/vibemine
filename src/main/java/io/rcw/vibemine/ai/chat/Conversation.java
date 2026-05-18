package io.rcw.vibemine.ai.chat;

import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Conversation {

    public static final int CHAT_HISTORY_LIMIT = 10;

    public record Message(Sender sender, String message, long timestamp) {}

    public static final Map<UUID, Conversation> conversing = new ConcurrentHashMap<>();

    private final Set<Message> messages = new HashSet<>();
    private final UUID sessionId;


    public Conversation(UUID sessionId, Collection<Message> messages) {
        this.sessionId = sessionId;
        this.messages.addAll(messages);
    }

    public Conversation() {
        // create a new session id
        this.sessionId = UUID.randomUUID();
    }

    public static Conversation beginConversation(final Player player) {
        // start a conversation with the user
        return conversing.computeIfAbsent(player.getUniqueId(), (key) -> new Conversation());
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

    public Set<Message> getMessages() {
        return this.messages;
    }
}
