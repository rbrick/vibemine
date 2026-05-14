package io.rcw.vibemine.ai.chat;

import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Conversation {

    public static final Map<UUID, Conversation> conversing = new ConcurrentHashMap<>();

    // Key = sender
    // Value = Set of Messages
    private Map<String, List<String>> messages = new ConcurrentHashMap<>();

    public Conversation() {}

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

    public List<String> messagesForSender(final String sender) {
        return messages.getOrDefault(sender, new ArrayList<>());
    }

}
