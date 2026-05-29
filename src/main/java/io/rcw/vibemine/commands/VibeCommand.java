package io.rcw.vibemine.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.ConversationStore;
import io.rcw.vibemine.ai.plugin.VibedPluginManager;
import io.rcw.vibemine.handlers.ConversationActionBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
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
    private final ConversationActionBar actionBar;

    public VibeCommand(VibedPluginManager pluginManager, ConversationStore conversationStore, ConversationActionBar actionBar) {
        this.pluginManager = pluginManager;
        this.conversationStore = conversationStore;
        this.actionBar = actionBar;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("vibemine.vibe.start")
                || sender.hasPermission("vibemine.vibe.continue")
                || sender.hasPermission("vibemine.vibe.sessions")
                || sender.hasPermission("vibemine.vibe.stop")
                || sender.hasPermission("vibemine.vibe.plugins")
                || sender.hasPermission("vibemine.vibe.plugins.enable")
                || sender.hasPermission("vibemine.vibe.plugins.disable")
                || sender.hasPermission("vibemine.vibe.plugins.delete");
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public java.util.Collection<String> suggest(CommandSourceStack source, String @NonNull [] args) {
        if (args.length == 0 || args.length == 1) {
            return List.of("start", "continue", "sessions", "stop", "plugins", "enable", "disable", "delete").stream()
                    .filter(command -> args.length == 0 || command.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("continue") && source.getSender() instanceof Player player) {
            return conversationStore.list(player.getUniqueId(), 10).stream()
                    .map(session -> session.sessionId().toString())
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && List.of("enable", "disable", "delete").contains(args[0].toLowerCase())) {
            return pluginManager.pluginNames().stream()
                    .filter(name -> name.startsWith(args[1].toLowerCase()))
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
            case "enable" -> enablePlugin(sender, args);
            case "disable" -> disablePlugin(sender, args);
            case "delete", "remove" -> deletePlugin(sender, args);
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
        actionBar.start(player);
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
        actionBar.start(player);
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
        actionBar.stop(player);
        player.sendMessage(Component.text("Stopped your vibe session.", NamedTextColor.YELLOW));
    }

    private void plugins(CommandSender sender) {
        if (!requirePermission(sender, "vibemine.vibe.plugins")) return;

        var persisted = pluginManager.pluginNames();
        if (persisted.isEmpty()) {
            sender.sendMessage(Component.text("No vibed plugins are saved.", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("Vibed plugins:", NamedTextColor.GOLD));
        persisted.forEach(name -> {


            var enabled = pluginManager.isEnabled(name);
            var ableAction = ClickEvent.runCommand(String.format("/vibe %s %s", enabled ? "disable" : "enable", name));
            var ableButton = Component.text(enabled ? "[Disable]" : "[Enable]", enabled ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .clickEvent(ableAction);
            var deleteButton = Component.text("[Delete]", NamedTextColor.DARK_RED)
                    .clickEvent(ClickEvent.runCommand("/vibe delete " + name));
            var component = Component.empty().append(
                    Component.text("-", NamedTextColor.DARK_GRAY)
                    ).appendSpace().append(Component.text(name, enabled ? NamedTextColor.GREEN :  NamedTextColor.GRAY))
                    .appendSpace()
                    .append(ableButton)
                    .appendSpace()
                    .append(deleteButton);

            sender.sendMessage(component);
        });
    }

    private void enablePlugin(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vibemine.vibe.plugins.enable")) return;
        if (!requirePluginName(sender, args, "enable")) return;

        try {
            var plugin = pluginManager.enablePlugin(args[1]);
            sender.sendMessage(Component.text("Enabled vibed plugin '" + plugin.name() + "'.", NamedTextColor.GREEN));
        } catch (IOException exception) {
            sender.sendMessage(Component.text("Could not enable vibed plugin '" + args[1] + "': " + exception.getMessage(), NamedTextColor.RED));
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text("Could not enable vibed plugin '" + args[1] + "': " + exception.getMessage(), NamedTextColor.RED));
        }
    }

    private void disablePlugin(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vibemine.vibe.plugins.disable")) return;
        if (!requirePluginName(sender, args, "disable")) return;

        if (pluginManager.disablePlugin(args[1])) {
            sender.sendMessage(Component.text("Disabled vibed plugin '" + args[1] + "'.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Vibed plugin '" + args[1] + "' is not enabled.", NamedTextColor.RED));
        }
    }

    private void deletePlugin(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vibemine.vibe.plugins.delete")) return;
        if (!requirePluginName(sender, args, "delete")) return;

        try {
            if (pluginManager.deletePlugin(args[1])) {
                sender.sendMessage(Component.text("Deleted vibed plugin '" + args[1] + "'.", NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text("No vibed plugin named '" + args[1] + "' exists.", NamedTextColor.RED));
            }
        } catch (IOException exception) {
            sender.sendMessage(Component.text("Could not delete vibed plugin '" + args[1] + "': " + exception.getMessage(), NamedTextColor.RED));
        }
    }

    private boolean requirePluginName(CommandSender sender, String[] args, String command) {
        if (args.length >= 2 && !args[1].isBlank()) return true;
        sender.sendMessage(Component.text("Usage: /vibe " + command + " <plugin>", NamedTextColor.YELLOW));
        return false;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("/vibe start", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe continue [session-id]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe sessions", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe stop", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe plugins", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe enable <plugin>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe disable <plugin>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe delete <plugin>", NamedTextColor.YELLOW));
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
