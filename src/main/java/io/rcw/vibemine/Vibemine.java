package io.rcw.vibemine;

import io.github.treesitter.jtreesitter.Language;
import io.github.treesitter.jtreesitter.Parser;
import io.github.treesitter.jtreesitter.internal.TreeSitter;
import io.rcw.vibemine.commands.VibeCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class Vibemine extends JavaPlugin {
    private static Vibemine instance;

    Vibemine() {
        instance = this;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic

        // vibe command -> opens book -> type prompt -> feed to llm/coding agent to create code -> code compiles to jvm (or we use a scripting language like groovy/javascript for this)

        this.registerCommand("vibe", new VibeCommand());


        // TODO(ryan): syntax highlighting
        this.registerCommand("syntax_highlight", (source, args) -> {
            CommandSender sender = source.getSender();

        });
    }

    @Override
    public void onDisable() {
    }


    public static Vibemine getInstance() {
        return Vibemine.instance;
    }

 }
