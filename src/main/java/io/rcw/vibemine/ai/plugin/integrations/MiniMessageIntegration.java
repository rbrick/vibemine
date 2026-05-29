package io.rcw.vibemine.ai.plugin.integrations;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage formatting helpers for generated JavaScript plugins.
 *
 * <p>Most Vibe runtime APIs currently accept legacy ampersand strings, so this
 * integration converts MiniMessage strings into those formats.</p>
 */
public final class MiniMessageIntegration implements VibeIntegration {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacy('&');
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

    private Component component(String message) {
        if (message == null) return Component.empty();
        return MINI_MESSAGE.deserialize(message);
    }
}
