package io.rcw.vibemine.handlers;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.TokenEstimator;
import io.rcw.vibemine.ai.agent.SystemPrompt;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConversationActionBar {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String FUN_MESSAGE_GRADIENT = "red:gold:yellow:green:aqua:blue:light_purple:red";
    private static final int FUN_MESSAGE_DURATION_TICKS = 240;
    private static final double FUN_GRADIENT_CYCLE_TICKS = 240.0D;
    private static final List<String> THINKING_MESSAGES = List.of(
            "vibing...", "crafting...", "exploding...", "brewing ideas...",
            "summoning code...", "polishing pixels...", "wrangling creepers...", "dreaming in blocks..."
    );

    private final ConcurrentMap<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> thinking = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> tokenTextCache = new ConcurrentHashMap<>();

    public void start(Player player) {
        if (tasks.containsKey(player.getUniqueId())) return;
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Vibemine.getInstance(), () -> render(player, tick[0]++), 0L, 1L);
        tasks.put(player.getUniqueId(), task);
    }

    public void stop(Player player) {
        thinking.remove(player.getUniqueId());
        tokenTextCache.remove(player.getUniqueId());
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        Bukkit.getScheduler().runTask(Vibemine.getInstance(), () -> player.sendActionBar(Component.empty()));
    }

    public void setThinking(Player player, boolean value) {
        start(player);
        if (value) thinking.put(player.getUniqueId(), true);
        else thinking.remove(player.getUniqueId());
    }

    private void render(Player player, int tick) {
        if (!player.isOnline() || !Conversation.isConversing(player)) {
            stop(player);
            return;
        }

        Conversation conversation = Conversation.forPlayer(player);
        UUID playerId = player.getUniqueId();
        if (tick % 20 == 0 || !tokenTextCache.containsKey(playerId)) {
            tokenTextCache.put(playerId, "~" + TokenEstimator.compact(estimatePromptTokens(conversation)) + " prompt tokens");
        }
        String tokenText = tokenTextCache.get(playerId);
        if (thinking.containsKey(player.getUniqueId())) {
            String message = THINKING_MESSAGES.get((tick / FUN_MESSAGE_DURATION_TICKS) % THINKING_MESSAGES.size());
            double gradientPhase = Math.sin((tick / FUN_GRADIENT_CYCLE_TICKS) * Math.PI * 2.0D);
            player.sendActionBar(MINI_MESSAGE.deserialize("<bold><gradient:" + FUN_MESSAGE_GRADIENT + ":" + gradientPhase + ">" + message + "</gradient></bold> <gray>(" + tokenText + ")</gray>"));
            return;
        }

        double gradientPhase = Math.sin((tick / FUN_GRADIENT_CYCLE_TICKS) * Math.PI * 2.0D);
        player.sendActionBar(MINI_MESSAGE.deserialize("<bold><gradient:" + FUN_MESSAGE_GRADIENT + ":" + gradientPhase + ">Vibe session</gradient></bold> <dark_gray>•</dark_gray> <gray>" + tokenText + "</gray>"));
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
}
