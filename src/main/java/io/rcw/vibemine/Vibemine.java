package io.rcw.vibemine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.impl.OpenAIAgent;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.adapters.ConversationAdapter;
import io.rcw.vibemine.ai.chat.adapters.MessageAdapter;
import io.rcw.vibemine.commands.VibeCommand;
import io.rcw.vibemine.handlers.AgentHandler;
import io.rcw.vibemine.handlers.ChatHandler;
import org.bukkit.Bukkit;
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
        var config = this.getConfig();

        // create our agent
        var agent = new OpenAIAgent(
                config.getString("ai.model"),
                config.getString("ai.api_key")
        );


        // register our chat handler
        Bukkit.getPluginManager().registerEvents(new ChatHandler(), this);

        // register our agent handler
        Bukkit.getPluginManager().registerEvents(new AgentHandler(agent), this);

        this.registerCommand("vibe", new VibeCommand());
    }

    @Override
    public void onDisable() {
    }


    public static Vibemine getInstance() {
        return Vibemine.instance;
    }

 }
