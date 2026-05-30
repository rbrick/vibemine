package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.Value;

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
     * Sends a message only to players listed in a JavaScript object/array.
     * For objects like state.players, member names are treated as player names.
     * For arrays, each element is treated as a player name.
     */
    public void broadcastPlayers(Value players, String message) {
        if (players == null || players.isNull()) return;
        if (players.hasArrayElements()) {
            for (long i = 0; i < players.getArraySize(); i++) sendToPlayerName(players.getArrayElement(i), message);
            return;
        }
        if (players.hasMembers()) {
            for (String name : players.getMemberKeys()) sendToPlayerName(name, message);
        }
    }

    /**
     * Alias for {@link #broadcastPlayers(Value, String)}.
     */
    public void broadcastToPlayers(Value players, String message) {
        broadcastPlayers(players, message);
    }

    private void sendToPlayerName(Value name, String message) {
        if (name != null && !name.isNull()) sendToPlayerName(name.asString(), message);
    }

    private void sendToPlayerName(String name, String message) {
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) player.sendMessage(VibeText.component(message));
    }

    /**
     * JavaScript binding for {@code getPlayer}.
     */
    public VibePlayer getPlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        return VibeRuntimeWrappers.player(player);
    }

    /**
     * JavaScript binding for {@code getOnlinePlayers}.
     */
    public List<VibePlayer> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream().map(VibeRuntimeWrappers::player).toList();
    }

    /**
     * JavaScript binding for {@code getWorld}.
     */
    public VibeWorld getWorld(String name) {
        World world = Bukkit.getWorld(name);
        return VibeRuntimeWrappers.world(world);
    }

    /**
     * JavaScript binding for {@code getWorlds}.
     */
    public List<VibeWorld> getWorlds() {
        return Bukkit.getWorlds().stream().map(VibeRuntimeWrappers::world).toList();
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
