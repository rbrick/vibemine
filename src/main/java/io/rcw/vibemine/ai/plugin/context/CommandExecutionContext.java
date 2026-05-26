package io.rcw.vibemine.ai.plugin.context;

import io.rcw.vibemine.ai.plugin.ExecutionContext;
import io.rcw.vibemine.ai.plugin.runtime.VibeRuntimeCache;
import io.rcw.vibemine.ai.plugin.runtime.VibeSender;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public final class CommandExecutionContext implements ExecutionContext {
    private final VibeSender sender;
    private final List<String> args;

    public CommandExecutionContext(CommandSender sender, String[] args) {
        this.sender = VibeRuntimeCache.sender(sender);
        this.args = List.copyOf(Arrays.asList(args));
    }

    public VibeSender getSender() {
        return sender;
    }

    public List<String> getArgs() {
        return args;
    }
}
