package io.rcw.vibemine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.adapters.ConversationAdapter;
import io.rcw.vibemine.ai.chat.adapters.MessageAdapter;
import io.rcw.vibemine.commands.VibeCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class Vibemine extends JavaPlugin {
    public static Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Conversation.class, new ConversationAdapter())
            .registerTypeAdapter(Conversation.Message.class, new MessageAdapter())
            .setPrettyPrinting().create();

    private static Vibemine instance;



    Vibemine() {
        instance = this;
    }


    @Override
    public void onEnable() {
        // Plugin startup logic

        // save the default config
        this.saveDefaultConfig();

        // vibe command -> opens book -> type prompt -> feed to llm/coding agent to create code -> code compiles to jvm (or we use a scripting language like groovy/javascript for this)

        this.registerCommand("vibe", new VibeCommand());

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
