package io.rcw.vibemine.ai.plugin.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class VibeText {
    private VibeText() {}

    static Component component(String message) {



        return LegacyComponentSerializer.legacy('&').deserialize(message);
    }
}
