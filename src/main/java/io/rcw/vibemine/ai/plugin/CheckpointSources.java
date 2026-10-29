package io.rcw.vibemine.ai.plugin;

import com.google.gson.Gson;
import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;

import java.io.IOException;
import java.util.Map;
import java.util.function.BiFunction;

final class CheckpointSources {
    private CheckpointSources() {}

    static VibedPluginSchema read(String name, Map<String,String> files) throws IOException {
        try {
            String manifest = files.get("plugin.json");
            if (manifest == null) throw new IllegalArgumentException("Checkpoint has no plugin.json");
            VibedPluginSchema schema = new Gson().fromJson(manifest, VibedPluginSchema.class);
            if (schema == null || !name.equals(schema.name())) throw new IllegalArgumentException("Manifest name must match plugin name");
            return materialize(schema, (inline, path) -> {
                if (path == null || path.isBlank()) return inline;
                String source = files.get(PluginStateStore.sourcePath(path));
                if (source == null) throw new IllegalArgumentException("Missing source file: " + path);
                return source;
            });
        } catch (RuntimeException exception) { throw new IOException("Invalid checkpoint: " + exception.getMessage(), exception); }
    }

    static VibedPluginSchema materialize(VibedPluginSchema schema, BiFunction<String,String,String> source) {
        var commands = schema.commands() == null ? null : schema.commands().stream()
                .map(command -> new io.rcw.vibemine.ai.plugin.schema.Command(command.label(), command.permission(),
                        source.apply(command.code(), command.path()), command.path())).toList();
        var events = schema.events() == null ? null : schema.events().stream()
                .map(event -> new io.rcw.vibemine.ai.plugin.schema.Event(event.event(),
                        source.apply(event.code(), event.path()), event.path())).toList();
        return new VibedPluginSchema(schema.name(), schema.description(), source.apply(schema.globals(), schema.globalsPath()),
                schema.globalsPath(), schema.version(), schema.imports(), schema.exports(), schema.pluginEvents(), commands, events);
    }
}
