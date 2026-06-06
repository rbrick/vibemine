package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Particle;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * JavaScript-safe particle helper exposed to generated plugins as {@code particles}.
 */
public final class VibeParticles {
    /** JavaScript binding for {@code names}. */
    public List<String> names() {
        return Arrays.stream(Particle.values()).map(particle -> particle.name().toLowerCase(Locale.ROOT)).toList();
    }

    /** JavaScript binding for {@code exists}. */
    public boolean exists(String particle) {
        try {
            parse(particle);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** JavaScript binding for {@code requiresData}. */
    public boolean requiresData(String particle) {
        return parse(particle).getDataType() != Void.class;
    }

    static Particle parse(String particle) {
        if (particle == null || particle.isBlank()) throw new IllegalArgumentException("Particle cannot be blank");
        String normalized = particle.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) normalized = normalized.substring("MINECRAFT:".length());
        normalized = normalized.replace('.', '_').replace('-', '_');
        return Particle.valueOf(normalized);
    }

    static void requireNoData(Particle particle) {
        if (particle.getDataType() != Void.class) {
            throw new IllegalArgumentException("Particle " + particle.name().toLowerCase(Locale.ROOT) + " requires typed data; use a specific helper such as spawnDustParticle for colored dust");
        }
    }
}
