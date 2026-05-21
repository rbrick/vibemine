package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * JavaScript-safe wrapper/binding for Vibe Server functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeServer {
    /**
     * JavaScript binding for {@code broadcast}.
     */
    public void broadcast(String message) {
        Bukkit.broadcast(VibeText.component(message));
    }

    /**
     * JavaScript binding for {@code getPlayer}.
     */
    public VibePlayer getPlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        return VibeRuntimeCache.player(player);
    }

    /**
     * JavaScript binding for {@code getOnlinePlayers}.
     */
    public List<VibePlayer> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream().map(VibeRuntimeCache::player).toList();
    }

    /**
     * JavaScript binding for {@code getWorld}.
     */
    public VibeWorld getWorld(String name) {
        World world = Bukkit.getWorld(name);
        return VibeRuntimeCache.world(world);
    }

    /**
     * JavaScript binding for {@code getWorlds}.
     */
    public List<VibeWorld> getWorlds() {
        return Bukkit.getWorlds().stream().map(VibeRuntimeCache::world).toList();
    }

    /**
     * JavaScript binding for {@code getMaxPlayers}.
     */
    public int getMaxPlayers() { return Bukkit.getMaxPlayers(); }
    /**
     * JavaScript binding for {@code getVersion}.
     */
    public String getVersion() { return Bukkit.getVersion(); }
}
