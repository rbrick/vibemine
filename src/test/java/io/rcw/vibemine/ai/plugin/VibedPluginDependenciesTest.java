package io.rcw.vibemine.ai.plugin;

import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VibedPluginDependenciesTest {
    @Test
    void missingImportsReportsDeletedOrDisabledDependency() {
        VibedPluginSchema minigame = schema("minigame", List.of("rank"));

        assertEquals(List.of("rank"), VibedPluginDependencies.missingImports(minigame, Set.of()));
        assertEquals(List.of(), VibedPluginDependencies.missingImports(minigame, Set.of("rank")));
    }

    @Test
    void importsAreNormalizedAndDeduplicatedForMissingChecks() {
        VibedPluginSchema minigame = schema("minigame", List.of("Rank", "rank", "RANK"));

        assertEquals(List.of("rank"), VibedPluginDependencies.missingImports(minigame, Set.of()));
    }

    @Test
    void selfImportsAreDetectedSeparatelyFromMissingImports() {
        VibedPluginSchema rank = schema("rank", List.of("rank"));

        assertEquals(List.of("rank"), VibedPluginDependencies.selfImports(rank));
        assertEquals(List.of(), VibedPluginDependencies.missingImports(rank, Set.of()));
    }

    @Test
    void loadOrderPlacesProvidersBeforeConsumers() {
        VibedPluginSchema minigame = schema("minigame", List.of("rank"));
        VibedPluginSchema rank = schema("rank", List.of());

        List<String> orderedNames = VibedPluginDependencies.loadOrder(List.of(minigame, rank)).stream()
                .map(VibedPluginSchema::name)
                .toList();

        assertEquals(List.of("rank", "minigame"), orderedNames);
    }

    @Test
    void unresolvedDependenciesRemainAfterLoadablePlugins() {
        VibedPluginSchema minigame = schema("minigame", List.of("rank"));
        VibedPluginSchema chat = schema("chat", List.of());

        List<String> orderedNames = VibedPluginDependencies.loadOrder(List.of(minigame, chat)).stream()
                .map(VibedPluginSchema::name)
                .toList();

        assertEquals(List.of("chat", "minigame"), orderedNames);
    }

    private static VibedPluginSchema schema(String name, List<String> imports) {
        return new VibedPluginSchema(
                name,
                "test plugin",
                "(function() { return {}; })",
                1,
                imports,
                Map.of("ping", "(function(state) { return 'pong'; })"),
                Map.of(),
                List.of(),
                List.of()
        );
    }
}
