package io.rcw.vibemine.context;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import javax.annotation.Nullable;

public interface VibeContext {
    /**
     * @return The current world the context is being executed within
     */
    World world();


    /**
     * @return The current player - might be null
     */
    @Nullable
    Player player();

    /**
     * @return The plugin manager
     */
    PluginManager pluginManager();
}
