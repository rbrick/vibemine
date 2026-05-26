package io.rcw.vibemine.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.ConversationStore;
import io.rcw.vibemine.ai.plugin.VibedPluginManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public final class VibeCommand implements BasicCommand {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final VibedPluginManager pluginManager;
    private final ConversationStore conversationStore;

    public VibeCommand(VibedPluginManager pluginManager, ConversationStore conversationStore) {
        this.pluginManager = pluginManager;
        this.conversationStore = conversationStore;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("vibemine.vibe.start")
                || sender.hasPermission("vibemine.vibe.continue")
                || sender.hasPermission("vibemine.vibe.sessions")
                || sender.hasPermission("vibemine.vibe.stop")
                || sender.hasPermission("vibemine.vibe.plugins");
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public java.util.Collection<String> suggest(CommandSourceStack source, String @NonNull [] args) {
        if (args.length == 0 || args.length == 1) {
            return List.of("start", "continue", "sessions", "stop", "plugins").stream()
                    .filter(command -> args.length == 0 || command.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("continue") && source.getSender() instanceof Player player) {
            return conversationStore.list(player.getUniqueId(), 10).stream()
                    .map(session -> session.sessionId().toString())
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack source, String @NonNull [] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            start(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> start(sender);
            case "continue" -> continueSession(sender, args);
            case "sessions" -> sessions(sender);
            case "stop", "end" -> stop(sender);
            case "plugins" -> plugins(sender);
            default -> help(sender);
        }
    }

    private void start(CommandSender sender) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "vibemine.vibe.start")) return;

        Player player = (Player) sender;
        saveActiveSession(player);
        Conversation conversation = Conversation.beginConversation(player);
        conversationStore.save(conversation);
        player.sendMessage(Component.text("Started vibe session " + shortId(conversation.getSessionId()) + ". Tell Viber what plugin to build.", NamedTextColor.GREEN));
    }

    private void continueSession(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "vibemine.vibe.continue")) return;

        Player player = (Player) sender;
        saveActiveSession(player);

        var conversation = args.length >= 2
                ? loadSession(player, args[1])
                : conversationStore.latest(player.getUniqueId());

        if (conversation.isEmpty()) {
            player.sendMessage(Component.text("No saved vibe session found.", NamedTextColor.RED));
            return;
        }

        Conversation.setConversation(player, conversation.get(), true);
        player.sendMessage(Component.text("Continued vibe session " + shortId(conversation.get().getSessionId()) + ".", NamedTextColor.GREEN));
    }

    private void sessions(CommandSender sender) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "vibemine.vibe.sessions")) return;

        Player player = (Player) sender;
        var sessions = conversationStore.list(player.getUniqueId(), 10);
        if (sessions.isEmpty()) {
            player.sendMessage(Component.text("You do not have any saved vibe sessions.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("Saved vibe sessions:", NamedTextColor.GOLD));
        sessions.forEach(session -> player.sendMessage(Component.text(
                "- " + session.sessionId() + " | " + session.messageCount() + " messages | " + DATE_FORMAT.format(Instant.ofEpochMilli(session.updatedAt())),
                NamedTextColor.GRAY
        )));
    }

    private void stop(CommandSender sender) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "vibemine.vibe.stop")) return;

        Player player = (Player) sender;
        saveActiveSession(player);
        Conversation.endConversation(player);
        player.sendMessage(Component.text("Stopped your vibe session.", NamedTextColor.YELLOW));
    }

    private void plugins(CommandSender sender) {
        if (!requirePermission(sender, "vibemine.vibe.plugins")) return;

        var plugins = pluginManager.plugins();
        if (plugins.isEmpty()) {
            sender.sendMessage(Component.text("No vibed plugins are enabled.", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("Enabled vibed plugins:", NamedTextColor.GOLD));
        plugins.forEach(vibedPlugin -> sender.sendMessage(Component.text(
                "- " + vibedPlugin.name() + " (" + vibedPlugin.commands().size() + " commands, " + vibedPlugin.events().size() + " events)",
                NamedTextColor.GRAY
        )));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("/vibe start", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe continue [session-id]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe sessions", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe stop", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe plugins", NamedTextColor.YELLOW));
    }

    private java.util.Optional<Conversation> loadSession(Player player, String rawSessionId) {
        try {
            return conversationStore.load(player.getUniqueId(), UUID.fromString(rawSessionId));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text("Invalid session id.", NamedTextColor.RED));
            return java.util.Optional.empty();
        }
    }

    private void saveActiveSession(Player player) {
        if (Conversation.isConversing(player)) {
            conversationStore.save(Conversation.forPlayer(player));
        }
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        sender.sendMessage(Component.text("Console can't vibe in a player session.", NamedTextColor.DARK_RED));
        return false;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(Component.text("You do not have permission: " + permission, NamedTextColor.RED));
        return false;
    }

    private String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }
}
