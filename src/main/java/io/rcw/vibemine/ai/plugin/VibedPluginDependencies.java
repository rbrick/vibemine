package io.rcw.vibemine.ai.plugin;

import io.rcw.vibemine.ai.plugin.schema.VibedPluginSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class VibedPluginDependencies {
    private VibedPluginDependencies() {}

    static List<String> importsOf(VibedPluginSchema schema) {
        return schema.imports() == null ? List.of() : schema.imports().stream().map(VibedPluginDependencies::normalizeName).toList();
    }

    static List<String> missingImports(VibedPluginSchema schema, Set<String> enabled) {
        String name = normalizeName(schema.name());
        return importsOf(schema).stream()
                .filter(imported -> !imported.equals(name))
                .filter(imported -> !enabled.contains(imported))
                .distinct()
                .toList();
    }

    static List<String> selfImports(VibedPluginSchema schema) {
        String name = normalizeName(schema.name());
        return importsOf(schema).stream().filter(name::equals).distinct().toList();
    }

    static List<VibedPluginSchema> loadOrder(List<VibedPluginSchema> schemas) {
        Map<String, VibedPluginSchema> pending = new LinkedHashMap<>();
        schemas.forEach(schema -> pending.put(normalizeName(schema.name()), schema));

        List<VibedPluginSchema> ordered = new ArrayList<>();
        Set<String> loaded = new HashSet<>();
        boolean progressed;
        do {
            progressed = false;
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                VibedPluginSchema schema = entry.getValue();
                if (!selfImports(schema).isEmpty()) continue;
                if (loaded.containsAll(importsOf(schema))) {
                    ordered.add(schema);
                    loaded.add(entry.getKey());
                    iterator.remove();
                    progressed = true;
                }
            }
        } while (progressed);

        ordered.addAll(pending.values());
        return ordered;
    }

    static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }
}
