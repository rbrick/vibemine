package io.rcw.vibemine.ai.plugin;

import com.google.gson.Gson;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VibedPluginSchemaTest {
    @Test
    void parsesImportsExportsAndPluginEvents() {
        String json = """
                {
                  "name":"minigame",
                  "description":"test",
                  "version":1,
                  "imports":["rank"],
                  "exports":{"join":"(function(playerId, state) { return true; })"},
                  "pluginEvents":{"rankChanged":"(function(payload, sourcePlugin, state) { state.last = payload; })"},
                  "commands":[],
                  "events":[]
                }
                """;

        VibedPluginSchema schema = new Gson().fromJson(json, VibedPluginSchema.class);

        assertEquals(List.of("rank"), schema.imports());
        assertTrue(schema.exports().containsKey("join"));
        assertTrue(schema.pluginEvents().containsKey("rankChanged"));
    }
}
