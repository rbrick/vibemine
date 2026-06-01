package io.rcw.vibemine.ai.tools.io;

import io.rcw.vibemine.Vibemine;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

final class PluginFileToolSupport {
    private PluginFileToolSupport() {}

    static Path resolve(Plugin plugin, String pluginName, String file) throws IOException {
        if (pluginName == null || pluginName.isBlank()) throw new IllegalArgumentException("Missing plugin name");
        if (file == null || file.isBlank()) throw new IllegalArgumentException("Missing file path");

        Path root = pluginRoot(plugin, pluginName);
        Path resolved = root.resolve(file).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("Path escapes plugin directory");
        return resolved;
    }

    static String reloadIfManifest(Plugin plugin, String pluginName, String file) {
        if (!file.replace('\\', '/').equals("plugin.json")) return null;
        return reload(plugin, pluginName);
    }

    private static Path pluginRoot(Plugin plugin, String pluginName) {
        return plugin.getDataFolder().toPath()
                .resolve("vibed-plugins")
                .resolve(normalizePluginName(pluginName))
                .toAbsolutePath()
                .normalize();
    }

    private static String reload(Plugin plugin, String pluginName) {
        if (!(plugin instanceof Vibemine vibemine) || vibemine.getVibedPluginManager() == null) return null;
        try {
            if (Bukkit.isPrimaryThread()) vibemine.getVibedPluginManager().enablePlugin(pluginName);
            else runSync(vibemine, () -> reload(plugin, pluginName));
            return null;
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return cause.getMessage();
        }
    }

    private static void runSync(Vibemine plugin, Runnable task) throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        future.get();
    }

    private static String normalizePluginName(String pluginName) {
        String normalizedSeparators = pluginName.replace('\\', '/');
        if (normalizedSeparators.startsWith("vibed-plugins/")) {
            normalizedSeparators = normalizedSeparators.substring("vibed-plugins/".length());
        }
        if (normalizedSeparators.contains("/")) {
            normalizedSeparators = normalizedSeparators.substring(normalizedSeparators.lastIndexOf('/') + 1);
        }
        return normalizedSeparators.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }
}
