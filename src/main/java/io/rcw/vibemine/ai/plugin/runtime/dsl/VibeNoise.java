package io.rcw.vibemine.ai.plugin.runtime.dsl;

import org.bukkit.util.noise.PerlinNoiseGenerator;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.Random;

public final class VibeNoise {
    private VibeNoise() {}

    public static double perlin2(double x, double z, double seed) {
        return new PerlinNoiseGenerator(new Random(Double.doubleToLongBits(seed))).noise(x, z);
    }

    public static double simplex2(double x, double z, double seed) {
        return new SimplexNoiseGenerator(new Random(Double.doubleToLongBits(seed))).noise(x, z);
    }

    public static double fbm2(double x, double z, double seed, double octaves, double lacunarity, double gain) {
        int count = Math.max(1, Math.min(12, (int) Math.round(octaves)));
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        for (int i = 0; i < count; i++) {
            sum += simplex2(x * frequency, z * frequency, seed + i * 1013.0) * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return norm == 0.0 ? 0.0 : sum / norm;
    }

    public static double ridged2(double x, double z, double seed, double octaves, double lacunarity, double gain) {
        int count = Math.max(1, Math.min(12, (int) Math.round(octaves)));
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        for (int i = 0; i < count; i++) {
            double n = simplex2(x * frequency, z * frequency, seed + i * 1619.0);
            n = 1.0 - Math.abs(n);
            n = n * n * 2.0 - 1.0;
            sum += n * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return norm == 0.0 ? 0.0 : sum / norm;
    }

    public static double billow2(double x, double z, double seed, double octaves, double lacunarity, double gain) {
        int count = Math.max(1, Math.min(12, (int) Math.round(octaves)));
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        for (int i = 0; i < count; i++) {
            double n = Math.abs(simplex2(x * frequency, z * frequency, seed + i * 2027.0)) * 2.0 - 1.0;
            sum += n * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return norm == 0.0 ? 0.0 : sum / norm;
    }

    public static double domainWarpX(double x, double z, double seed, double frequency, double strength) {
        return x + simplex2(x * frequency, z * frequency, seed + 37.0) * strength;
    }

    public static double domainWarpZ(double x, double z, double seed, double frequency, double strength) {
        return z + simplex2(x * frequency, z * frequency, seed + 73.0) * strength;
    }

    public static double terrace(double value, double steps) {
        if (steps <= 1.0) return value;
        return Math.floor(value * steps) / steps;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
