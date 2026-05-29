package io.rcw.vibemine.ai.plugin.integrations;

import org.graalvm.polyglot.Value;

import java.util.List;

/**
 * Registry for JavaScript-safe integrations exposed to vibed plugins.
 */
public final class VibeIntegrations {
    private static final List<VibeIntegration> INTEGRATIONS = List.of(
            new MiniMessageIntegration()
    );

    private VibeIntegrations() {}

    public static void install(Value bindings) {
        INTEGRATIONS.forEach(integration -> bindings.putMember(integration.bindingName(), integration));
    }
}
