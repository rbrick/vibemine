package io.rcw.vibemine.ai.plugin.context;

import io.rcw.vibemine.ai.plugin.ExecutionContext;
import io.rcw.vibemine.ai.plugin.runtime.VibeRuntimeCache;
import io.rcw.vibemine.ai.plugin.runtime.VibePlayer;
import io.rcw.vibemine.ai.plugin.runtime.VibeSender;
import io.rcw.vibemine.ai.plugin.runtime.VibeWorld;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public final class CommandExecutionContext implements ExecutionContext {
    private final VibeSender sender;
    private final VibePlayer player;
    private final List<String> args;
    private final VibeWorld world;

    public CommandExecutionContext(CommandSender sender, String[] args) {
        this.sender = VibeRuntimeCache.sender(sender);
        this.player = sender instanceof Player player ? VibeRuntimeCache.player(player) : null;
        this.args = List.copyOf(Arrays.asList(args));
        this.world = sender instanceof Player player ? VibeRuntimeCache.world(player.getWorld()) : null;
    }

    public VibeSender getSender() {
        return sender;
    }

    public VibePlayer getPlayer() {
        return player;
    }

    public List<String> getArgs() {
        return args;
    }

    public VibeWorld getWorld() {
        return world;
    }
}
