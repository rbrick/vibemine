package io.rcw.vibemine.ai.plugin;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CheckpointSourcesTest {
    @Test void resolvesOnlyFilesInTheImmutableCheckpoint() throws IOException {
        var schema = CheckpointSources.read("demo", Map.of(
                "plugin.json", """
                {"name":"demo","globalsPath":"globals.js",
                 "commands":[{"label":"hello","path":"commands/hello.js"}],
                 "events":[{"event":"player_join","path":"events/join.js"}]}
                """,
                "globals.js", "globals", "commands/hello.js", "command", "events/join.js", "event"));
        assertEquals("globals", schema.globals());
        assertEquals("command", schema.commands().getFirst().code());
        assertEquals("event", schema.events().getFirst().code());
    }

    @Test void rejectsMissingSourcesWrongNamesAndEscapesBeforeActivation() {
        assertThrows(IOException.class, () -> CheckpointSources.read("demo", Map.of()));
        assertThrows(IOException.class, () -> CheckpointSources.read("demo", Map.of("plugin.json", "{\"name\":\"other\"}")));
        assertThrows(IOException.class, () -> CheckpointSources.read("demo", Map.of("plugin.json", "{\"name\":\"demo\",\"globalsPath\":\"globals.js\"}")));
        assertThrows(IOException.class, () -> CheckpointSources.read("demo", Map.of("plugin.json", "{\"name\":\"demo\",\"globalsPath\":\"../globals.js\"}")));
        assertThrows(IOException.class, () -> CheckpointSources.read("demo", Map.of("plugin.json", "broken JSON")));
    }

    @Test void preservesLegacyInlineSources() throws IOException {
        assertEquals("(function(){return {};})", CheckpointSources.read("demo", Map.of("plugin.json",
                "{\"name\":\"demo\",\"globals\":\"(function(){return {};})\"}")).globals());
    }
}
