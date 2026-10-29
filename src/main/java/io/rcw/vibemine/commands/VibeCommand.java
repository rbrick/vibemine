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
        return sender.hasPermission(SourceEditor.PERMISSION)
                || sender.hasPermission("vibemine.vibe.start")
                || sender.hasPermission("vibemine.vibe.continue")
                || sender.hasPermission("vibemine.vibe.sessions")
                || sender.hasPermission("vibemine.vibe.stop")
                || sender.hasPermission("vibemine.vibe.plugins")
                || sender.hasPermission("vibemine.vibe.plugins.enable")
                || sender.hasPermission("vibemine.vibe.plugins.disable")
                || sender.hasPermission("vibemine.vibe.plugins.delete")
                || sender.hasPermission("vibemine.vibe.plugins.translate")
                || sender.hasPermission("vibemine.vibe.plugins.revisions")
                || sender.hasPermission("vibemine.vibe.plugins.revert");
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public java.util.Collection<String> suggest(CommandSourceStack source, String @NonNull [] args) {
        if (args.length == 0 || args.length == 1) {
            return List.of("auth", "ide", "start", "continue", "sessions", "stop", "plugins", "enable", "disable", "delete", "translate", "revisions", "revert", "state", "checkpoint", "branch", "checkout", "activate").stream()
                    .filter(command -> args.length == 0 || command.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("continue") && source.getSender() instanceof Player player) {
            return conversationStore.list(player.getUniqueId(), 10).stream()
                    .map(session -> session.sessionId().toString())
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("translate")) {
            return pluginManager.oldPluginNames().stream()
                    .filter(name -> name.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && List.of("enable", "disable", "delete", "revisions", "revert", "state", "checkpoint", "branch", "checkout", "activate").contains(args[0].toLowerCase())) {
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
            case "auth" -> auth(sender, args);
            case "ide" -> {
                if (requirePlayer(sender) && requirePermission(sender, SourceEditor.PERMISSION)) {
                    new SourceEditor(pluginManager).open((Player) sender);
                }
            }
            case "start" -> start(sender);
            case "continue" -> continueSession(sender, args);
            case "sessions" -> sessions(sender);
            case "stop", "end" -> stop(sender);
            case "plugins" -> plugins(sender);
            case "enable" -> enablePlugin(sender, args);
            case "disable" -> disablePlugin(sender, args);
            case "delete", "remove" -> deletePlugin(sender, args);
            case "translate" -> translatePlugin(sender, args);
            case "revisions", "history" -> revisions(sender, args);
            case "revert", "activate" -> activate(sender, args);
            case "state", "checkpoint", "branch", "checkout" -> stateCommand(sender, args);
            default -> help(sender);
        }
    }

    private void auth(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(Component.text("ChatGPT sign-in is server-wide and managed from the local server console, not Minecraft chat.", NamedTextColor.RED));
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: vibe auth <login|host|import|status|models|logout>", NamedTextColor.YELLOW));
            return;
        }
        stateTask(sender, () -> {
            var plugin = io.rcw.vibemine.Vibemine.getInstance();
            var credentials = plugin.getSubscriptionCredentials();
            if (credentials == null) throw new IllegalStateException("Auth storage unavailable. Check POSIX file permissions and restart the server.");
            return switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                case "login", "host" -> "Server host ID: " + credentials.hostId()
                        + "\nRun the local ChatGptLogin helper with this host ID. See docs/chatgpt-sign-in.md. No callback port is opened on this server.";
                case "import" -> {
                    credentials.importCredentials();
                    yield "ChatGPT credentials imported for the entire server. " + credentials.status();
                }
                case "status" -> credentials.status() + " | subscription enabled: " + plugin.getConfig().getBoolean("ai.subscription.enabled", true)
                        + " | API fallback (billable): " + plugin.getConfig().getBoolean("ai.subscription.api_fallback", true);
                case "models" -> credentials.models();
                case "logout" -> credentials.logout();
                default -> throw new IllegalArgumentException("Unknown auth action");
            };
        });
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

    private void revisions(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vibemine.vibe.plugins.revisions")) return;
        if (!requirePluginName(sender, args, "revisions")) return;

        stateTask(sender, () -> pluginManager.revisions(args[1], 100).stream()
                .map(revision -> revision.display()).collect(java.util.stream.Collectors.joining("\n")));
    }

    private void activate(CommandSender sender, String[] args) {
        if (!(sender.hasPermission(SourceEditor.PERMISSION) || sender.hasPermission("vibemine.vibe.plugins.enable")
                || sender.hasPermission("vibemine.vibe.plugins.revert"))) {
            requirePermission(sender, "vibemine.vibe.plugins.enable");
            return;
        }
        if (args.length != 3) {
            sender.sendMessage(Component.text("Usage: /vibe activate <plugin> <checkpoint>", NamedTextColor.YELLOW));
            return;
        }
        stateTask(sender, () -> {
            String expected = pluginManager.stateStore().active(args[1]);
            pluginManager.activateAsync(sender, args[1], args[2], expected).join();
            return "Activated checkpoint " + args[2] + ". Workspace edits and branch history were preserved.";
        });
    }

    private void stateCommand(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, SourceEditor.PERMISSION)) return;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /vibe " + args[0] + " <plugin> [message | branch [checkpoint]]", NamedTextColor.YELLOW));
            return;
        }
        String owner = ((Player) sender).getUniqueId().toString();
        stateTask(sender, () -> {
            var store = pluginManager.stateStore();
            var workspace = store.workspace(args[1], owner);
            return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "checkpoint" -> {
                    if (args.length < 3) throw new IllegalArgumentException("Supply a checkpoint message");
                    yield "Checkpoint: " + store.checkpoint(args[1], owner, workspace.version(), String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                }
                case "branch" -> {
                    if (args.length == 3) yield "Workspace forked onto " + store.fork(args[1], owner, workspace.version(), args[2]).branch();
                    if (args.length != 4) throw new IllegalArgumentException("Usage: /vibe branch <plugin> <branch> [checkpoint]");
                    store.branch(args[1], args[2], args[3]);
                    yield "Branch created. Use /vibe checkout " + args[1] + " " + args[2] + " to switch.";
                }
                case "checkout" -> {
                    if (args.length != 3) throw new IllegalArgumentException("Usage: /vibe checkout <plugin> <branch>");
                    yield "Workspace is now on " + store.checkout(args[1], owner, workspace.version(), args[2]).branch();
                }
                default -> "Branch: " + workspace.branch() + " | Version: " + workspace.version() + " | Dirty: " + workspace.dirty()
                        + "\nActive: " + store.active(args[1]) + "\nFiles: " + workspace.files().keySet() + "\nBranches: " + store.branches(args[1]);
            };
        });
    }

    private void stateTask(CommandSender sender, java.util.concurrent.Callable<String> action) {
        var plugin = io.rcw.vibemine.Vibemine.getInstance();
        io.rcw.vibemine.FoliaScheduler.async(() -> {
            Component message;
            try {
                String result = action.call();
                message = Component.text(result.isEmpty() ? "No checkpoints found." : result, NamedTextColor.GREEN);
            } catch (Exception exception) {
                Throwable cause = exception;
                while (cause.getCause() != null) cause = cause.getCause();
                message = Component.text("State: " + cause.getMessage(), NamedTextColor.RED);
            }
            Component response = message;
            io.rcw.vibemine.FoliaScheduler.run(() -> sender.sendMessage(response));
        });
    }

    private void translatePlugin(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vibemine.vibe.plugins.translate")) return;
        if (!requirePluginName(sender, args, "translate")) return;

        try {
            var plugin = pluginManager.translateOldPlugin(args[1]);
            sender.sendMessage(Component.text("Translated and enabled vibed plugin '" + plugin.name() + "'.", NamedTextColor.GREEN));
        } catch (IOException exception) {
            sender.sendMessage(Component.text("Could not translate vibed plugin '" + args[1] + "': " + exception.getMessage(), NamedTextColor.RED));
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text("Could not translate vibed plugin '" + args[1] + "': " + exception.getMessage(), NamedTextColor.RED));
        }
    }

    private boolean requirePluginName(CommandSender sender, String[] args, String command) {
        if (args.length >= 2 && !args[1].isBlank()) return true;
        sender.sendMessage(Component.text("Usage: /vibe " + command + " <plugin>", NamedTextColor.YELLOW));
        return false;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("/vibe auth <login|host|import|status|models|logout> (local server console only)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe ide", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe state <plugin> | checkpoint <plugin> <message>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe branch <plugin> <branch> <checkpoint> | checkout <plugin> <branch>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe activate <plugin> <checkpoint>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe start", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe continue [session-id]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe sessions", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe stop", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe plugins", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe enable <plugin>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe disable <plugin>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe delete <plugin>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe translate <old-plugin>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe revisions <plugin>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibe revert <plugin> <revision>", NamedTextColor.YELLOW));
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
