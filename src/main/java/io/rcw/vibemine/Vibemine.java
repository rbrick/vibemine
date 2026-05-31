package io.rcw.vibemine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.rcw.vibemine.ai.agent.Agent;
import io.rcw.vibemine.ai.agent.impl.OpenAIAgent;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.ConversationStore;
import io.rcw.vibemine.ai.chat.adapters.ConversationAdapter;
import io.rcw.vibemine.ai.chat.adapters.MessageAdapter;
import io.rcw.vibemine.ai.plugin.VibedPluginManager;
import io.rcw.vibemine.ai.tools.api.ApiReferenceTool;
import io.rcw.vibemine.ai.tools.command.CommandTool;
import io.rcw.vibemine.ai.tools.io.FileEditTool;
import io.rcw.vibemine.ai.tools.io.FileReadTool;
import io.rcw.vibemine.ai.tools.io.FileWriteTool;
import io.rcw.vibemine.ai.tools.message.SendMessageTool;
import io.rcw.vibemine.ai.tools.plugin.PluginContextTool;
import io.rcw.vibemine.ai.tools.raytrace.RayTraceTool;
import io.rcw.vibemine.ai.tools.sound.SoundSearchTool;
import io.rcw.vibemine.ai.tools.spawn.SpawnTool;
import io.rcw.vibemine.ai.tools.syntax.SyntaxHighlightTool;
import io.rcw.vibemine.commands.VibeCommand;
import io.rcw.vibemine.handlers.AgentHandler;
import io.rcw.vibemine.handlers.ChatHandler;
import io.rcw.vibemine.handlers.ConversationActionBar;
import io.rcw.vibemine.serialization.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.UUID;

public final class Vibemine extends JavaPlugin {

    public static Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(Conversation.class, new ConversationAdapter())
            .registerTypeHierarchyAdapter(Conversation.Message.class, new MessageAdapter())
            .registerTypeHierarchyAdapter(UUID.class, new UUIDSerializer())
            .registerTypeHierarchyAdapter(Location.class, new LocationSerializer())
            .registerTypeHierarchyAdapter(Block.class, new BlockSerializer())
            .registerTypeHierarchyAdapter(Entity.class, new EntitySerializer())
            .registerTypeHierarchyAdapter(EntityType.class, new EnumSerializer<EntityType>())
            .setPrettyPrinting().create();

    private static Vibemine instance;
    private VibedPluginManager vibedPluginManager;
    private ConversationStore conversationStore;
    private Path databasePath;



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

        this.registerTools(agent);

        try {
            this.databasePath = getDataFolder().toPath().resolve(config.getString("database.file", "vibe.db"));
            this.conversationStore = new ConversationStore(databasePath);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not open conversation database", exception);
        }



        var conversationActionBar = new ConversationActionBar();

        // register our chat handler
        Bukkit.getPluginManager().registerEvents(new ChatHandler(conversationStore), this);


        this.vibedPluginManager = new VibedPluginManager(this);
        this.vibedPluginManager.enable();

        // register our agent handler
        Bukkit.getPluginManager().registerEvents(new AgentHandler(agent, vibedPluginManager, conversationStore, conversationActionBar), this);

        this.registerCommand("vibe", new VibeCommand(vibedPluginManager, conversationStore, conversationActionBar));
    }

    @Override
    public void onDisable() {
        if (conversationStore != null) {
            Conversation.conversing.values().forEach(conversation -> conversationStore.save(conversation));
        }
        if (vibedPluginManager != null) {
            vibedPluginManager.disable();
        }
        if (conversationStore != null) {
            try {
                conversationStore.close();
            } catch (Exception exception) {
                getLogger().warning("Could not close conversation database: " + exception.getMessage());
            }
        }
    }

    public void registerTools(Agent agent) {
        agent.registerTool(new RayTraceTool());
        agent.registerTool(new CommandTool());
        agent.registerTool(new SpawnTool());
        agent.registerTool(new SyntaxHighlightTool());
        agent.registerTool(new SendMessageTool());
        agent.registerTool(new ApiReferenceTool());
        agent.registerTool(new SoundSearchTool());
        agent.registerTool(new PluginContextTool());
        agent.registerTool(new FileWriteTool(this));
        agent.registerTool(new FileReadTool(this));
        agent.registerTool(new FileEditTool(this));
    }


    public VibedPluginManager getVibedPluginManager() {
        return vibedPluginManager;
    }

    public ConversationStore getConversationStore() {
        return conversationStore;
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public static Vibemine getInstance() {
        return Vibemine.instance;
    }

 }
