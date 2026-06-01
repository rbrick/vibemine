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

    public static final int CHAT_HISTORY_LIMIT = 6;
    public static final Map<UUID, Conversation> conversing = new ConcurrentHashMap<>();

    private final List<Message> messages = new LinkedList<>();
    private final UUID playerId;
    private final UUID sessionId;

    private String summary;
    private int estimatedOutputTokens;
    private int estimatedToolTokens;

    public Conversation(UUID playerId, UUID sessionId, Collection<Message> messages, int estimatedOutputTokens, int estimatedToolTokens) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.messages.addAll(messages);
        this.estimatedOutputTokens = Math.max(0, estimatedOutputTokens);
        this.estimatedToolTokens = Math.max(0, estimatedToolTokens);
    }

    public Conversation(UUID playerId) {
        this.playerId = playerId;
        this.sessionId = UUID.randomUUID();
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
        var builder = new StringBuilder();
        messages.stream()
                .sorted(Comparator.comparingLong(Message::timestamp))
                .limit(Math.min(messageCount, messages.size()))
                .map(message -> "%s: %s%n".formatted(message.sender().name(), message.message()))
                .forEachOrdered(builder::append);
        return builder.toString();
    }

    public Optional<String> getSummary() {
        return Optional.ofNullable(this.summary);
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getEstimatedOutputTokens() {
        return estimatedOutputTokens;
    }

    public void addEstimatedOutputTokens(int tokens) {
        this.estimatedOutputTokens += Math.max(0, tokens);
    }

    public int getEstimatedToolTokens() {
        return estimatedToolTokens;
    }

    public void addEstimatedToolTokens(int tokens) {
        this.estimatedToolTokens += Math.max(0, tokens);
    }
}
