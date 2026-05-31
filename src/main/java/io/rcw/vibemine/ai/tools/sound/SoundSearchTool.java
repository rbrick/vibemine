package io.rcw.vibemine.ai.tools.sound;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Named("sound_search")
public final class SoundSearchTool implements Tool<SoundSearchTool.SoundSearchInput, SoundSearchTool.SoundSearchOutput> {

    @Override
    public Class<SoundSearchInput> inputClass() {
        return SoundSearchInput.class;
    }

    @Override
    public Class<SoundSearchOutput> outputClass() {
        return SoundSearchOutput.class;
    }

    @Override
    public SoundSearchOutput execute(Player player, SoundSearchInput input) {
        int limit = Math.clamp(input == null ? 50 : input.limitOrDefault(), 1, 200);
        String query = input == null || input.query == null ? "" : input.query.trim().toLowerCase(Locale.ROOT);
        try {
            List<String> sounds = soundsFromBukkitRegistry();
            List<String> filtered = sounds.stream()
                    .filter(sound -> matches(sound, query))
                    .sorted(Comparator.comparingInt(sound -> score(sound, query)))
                    .sorted(String::compareToIgnoreCase)
                    .limit(limit)
                    .toList();
            return new SoundSearchOutput(true, filtered, sounds.size(), "");
        } catch (Exception exception) {
            return new SoundSearchOutput(false, List.of(), 0, exception.getMessage());
        }
    }

    @Override
    public String usage() {
        return """
                Search the server sound registry and return sound keys safe to pass to player/world playSound helpers.
                Input: {"query":"note_block", "limit":25}. Use an empty query to list the first registry entries.
                Prefer returned names such as minecraft:block.note_block.pling or block.note_block.pling over invented sound constants.
                """;
    }

    private List<String> soundsFromBukkitRegistry() throws Exception {
        Class<?> registryClass = Class.forName("org.bukkit.Registry");
        Object soundRegistry = null;
        for (Field field : registryClass.getFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (name.equals("sounds") || name.equals("sound_event") || name.equals("sound_events") || name.contains("sound")) {
                Object value = field.get(null);
                if (value instanceof Iterable<?>) {
                    soundRegistry = value;
                    break;
                }
            }
        }
        if (!(soundRegistry instanceof Iterable<?> iterable)) {
            throw new IllegalStateException("Could not find an iterable sound registry on org.bukkit.Registry");
        }

        List<String> sounds = new ArrayList<>();
        for (Object entry : iterable) {
            String key = keyOf(entry);
            if (key != null && !key.isBlank()) sounds.add(key);
        }
        sounds.sort(String::compareTo);
        return sounds;
    }

    private String keyOf(Object entry) {
        for (String methodName : List.of("getKey", "key")) {
            try {
                Object key = entry.getClass().getMethod(methodName).invoke(entry);
                if (key != null) return key.toString();
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return entry == null ? null : entry.toString();
    }

    private boolean matches(String sound, String query) {
        if (query == null || query.isBlank()) return true;
        String normalizedSound = normalize(sound);
        String normalizedQuery = normalize(query);
        for (String token : normalizedQuery.split(" ")) {
            if (!token.isBlank() && !normalizedSound.contains(token)) return false;
        }
        return true;
    }

    private int score(String sound, String query) {
        if (query == null || query.isBlank()) return 0;
        String normalizedSound = normalize(sound);
        String normalizedQuery = normalize(query);
        if (normalizedSound.equals(normalizedQuery)) return 0;
        if (normalizedSound.endsWith(normalizedQuery)) return 1;
        if (normalizedSound.contains(normalizedQuery)) return 2;
        return 3;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("minecraft:", "")
                .replace('_', ' ')
                .replace('.', ' ')
                .replace('-', ' ');
    }

    public record SoundSearchInput(String query, Integer limit) {
        int limitOrDefault() {
            return limit == null ? 50 : limit;
        }
    }

    public record SoundSearchOutput(boolean success, List<String> sounds, int total, String error) {}
}
