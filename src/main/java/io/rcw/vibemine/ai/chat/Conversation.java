package io.rcw.vibemine.ai.chat;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.events.conversation.ConversationStartEvent;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Conversation {

    public record Message(Sender sender, String message, long timestamp) {}

    // constants
    public static final int CHAT_HISTORY_LIMIT = 6;

    public static final Map<UUID, Conversation> conversing = new ConcurrentHashMap<>();

    // immutables fields
    private final List<Message> messages = new LinkedList<>();
    private final UUID playerId, sessionId;

    // mutable fields
    private String summary;

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
        return setConversation(player, new Conversation(player.getUniqueId()), true);
    }

    public static Conversation setConversation(final Player player, Conversation conversation, boolean fireStartEvent) {
        conversing.put(player.getUniqueId(), conversation);
        if (fireStartEvent) {
            Bukkit.getScheduler().runTaskAsynchronously(Vibemine.getInstance(), () -> {
                Bukkit.getPluginManager().callEvent(new ConversationStartEvent(player, conversation));
            });
        }
        return conversation;
    }

    public static void endConversation(final Player player) {
        conversing.remove(player.getUniqueId());
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

    public Player getPlayer() {
        return  Bukkit.getPlayer(playerId);
    }

    public List<Message> getMessages() {
        return this.messages;
    }

    public String formatChatHistory(int messageCount) {
        final var builder = new StringBuilder();

        this.messages.stream().sorted(Comparator.comparingLong(Message::timestamp)).limit(
                Math.min(messageCount, this.messages.size())
        ).map((msg) -> String.format("%s: %s%n", msg.sender().name(), msg.message()))
                .forEachOrdered(builder::append);

        return builder.toString();
    }

    public Optional<String> getSummary() {
        return Optional.ofNullable(this.summary);
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
