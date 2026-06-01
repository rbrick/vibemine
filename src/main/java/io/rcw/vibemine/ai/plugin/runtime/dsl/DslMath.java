package io.rcw.vibemine.ai.plugin.runtime.dsl;

public final class DslMath {
    private DslMath() {}
    public static double mod(double a, double b) { return ((a % b) + b) % b; }
    public static double noise2(double x, double z, double seed) { return VibeNoise.simplex2(x, z, seed); }
    public static double perlin2(double x, double z, double seed) { return VibeNoise.perlin2(x, z, seed); }
    public static double simplex2(double x, double z, double seed) { return VibeNoise.simplex2(x, z, seed); }
    public static double fbm2(double x, double z, double seed, double octaves, double lacunarity, double gain) { return VibeNoise.fbm2(x, z, seed, octaves, lacunarity, gain); }
    public static double ridged2(double x, double z, double seed, double octaves, double lacunarity, double gain) { return VibeNoise.ridged2(x, z, seed, octaves, lacunarity, gain); }
    public static double billow2(double x, double z, double seed, double octaves, double lacunarity, double gain) { return VibeNoise.billow2(x, z, seed, octaves, lacunarity, gain); }
    public static double warpX(double x, double z, double seed, double frequency, double strength) { return VibeNoise.domainWarpX(x, z, seed, frequency, strength); }
    public static double warpZ(double x, double z, double seed, double frequency, double strength) { return VibeNoise.domainWarpZ(x, z, seed, frequency, strength); }
    public static double terrace(double value, double steps) { return VibeNoise.terrace(value, steps); }
    public static double smoothstep(double edge0, double edge1, double x) { return VibeNoise.smoothstep(edge0, edge1, x); }
    public static double clamp(double value, double min, double max) { return VibeNoise.clamp(value, min, max); }
    public static double lerp(double a, double b, double t) { return VibeNoise.lerp(a, b, t); }
    public static double truth(boolean value) { return value ? 1.0 : 0.0; }
}
