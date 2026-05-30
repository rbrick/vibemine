package io.rcw.vibemine.ai.tools.io;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;

final class PluginFileToolSupport {
    private PluginFileToolSupport() {}

    static Path resolve(Plugin plugin, String pluginName, String file) throws IOException {
        if (pluginName == null || pluginName.isBlank()) throw new IllegalArgumentException("Missing plugin name");
        if (file == null || file.isBlank()) throw new IllegalArgumentException("Missing file path");
        String safePlugin = normalizePluginName(pluginName);
        Path pluginRoot = plugin.getDataFolder().toPath().resolve("vibed-plugins").resolve(safePlugin).toAbsolutePath().normalize();
        Path resolved = pluginRoot.resolve(file).toAbsolutePath().normalize();
        if (!resolved.startsWith(pluginRoot)) throw new SecurityException("Path escapes plugin directory");
        return resolved;
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
