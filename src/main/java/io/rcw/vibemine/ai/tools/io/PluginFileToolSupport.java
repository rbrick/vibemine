package io.rcw.vibemine.ai.tools.io;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.plugin.PluginStateStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class PluginFileToolSupport {
    private PluginFileToolSupport() {}

    static PluginStateStore store(Plugin plugin) {
        return ((Vibemine) plugin).getVibedPluginManager().stateStore();
    }

    static String owner(Player player) { return player.getUniqueId().toString(); }

    static long version(Long expected) {
        if (expected == null) throw new IllegalArgumentException("expectedVersion is required; read the workspace first");
        return expected;
    }
}
