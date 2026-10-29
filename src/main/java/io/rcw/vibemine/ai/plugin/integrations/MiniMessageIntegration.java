package io.rcw.vibemine.ai.plugin.integrations;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;

/**
 * MiniMessage formatting helpers for generated JavaScript plugins.
 *
 * <p>Most Vibe runtime APIs currently accept legacy ampersand strings, but some
 * APIs and tools accept Adventure components/component JSON. This integration
 * exposes MiniMessage conversions for both cases.</p>
 */
public final class MiniMessageIntegration implements VibeIntegration {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacy('&');
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Override
    public String bindingName() {
        return "minimessage";
    }

    /**
     * Converts MiniMessage markup to a legacy ampersand-colored string accepted by Vibe APIs.
     */
    public String legacy(String message) {
        return LEGACY_AMPERSAND.serialize(component(message));
    }

    /**
     * Converts MiniMessage markup to an Adventure component.
     */
    public Component component(String message) {
        if (message == null) return Component.empty();
        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * Alias for {@link #component(String)}.
     */
    public Component toComponent(String message) {
        return component(message);
    }

    /**
     * Converts MiniMessage markup to serialized Adventure JSON component data.
     */
    public String componentJson(String message) {
        return GSON.serialize(component(message));
    }

    /**
     * Alias for {@link #componentJson(String)}.
     */
    public String toComponentJson(String message) {
        return componentJson(message);
    }

    public void broadcastMiniMessage(String message) {
        Bukkit.getServer().broadcast(MINI_MESSAGE.deserialize(message));
    }

    /**
     * Alias for {@link #legacy(String)}.
     */
    public String toLegacy(String message) {
        return legacy(message);
    }

    /**
     * Converts MiniMessage markup to plain text with formatting removed.
     */
    public String plain(String message) {
        return PLAIN.serialize(component(message));
    }

    /**
     * Alias for {@link #plain(String)}.
     */
    public String strip(String message) {
        return plain(message);
    }

}
