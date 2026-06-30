package com.piasop.worldgen2.modules.phase1;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight deterministic noise helpers used by early WG2 modules.
 */
public final class Phase1Noise {
    private static final long PRIME_X = 0x9E3779B185EBCA87L;
    private static final long PRIME_Z = 0xC2B2AE3D27D4EB4FL;
    private static final ConcurrentHashMap<Long, OpenSimplex2S> SIMPLEX_CACHE = new ConcurrentHashMap<>();

    private Phase1Noise() {
    }

    public static double value2D(double x, double z, long seed) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double tx = x - x0;
        double tz = z - z0;
        double sx = smoothstep(tx);
        double sz = smoothstep(tz);

        double n00 = hashToUnit(x0, z0, seed);
        double n10 = hashToUnit(x1, z0, seed);
        double n01 = hashToUnit(x0, z1, seed);
        double n11 = hashToUnit(x1, z1, seed);

        double ix0 = lerp(n00, n10, sx);
        double ix1 = lerp(n01, n11, sx);
        return lerp(ix0, ix1, sz);
    }

    public static double fbm2D(double x, double z, long seed, int octaves, double lacunarity, double gain) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;

        for (int i = 0; i < octaves; i++) {
            sum += value2D(x * frequency, z * frequency, seed + (i * 1013L)) * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }

        return norm == 0.0 ? 0.0 : (sum / norm);
    }

    public static double ridgedFbm2D(double x, double z, long seed, int octaves, double lacunarity, double gain) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;

        for (int i = 0; i < octaves; i++) {
            double signal = 1.0 - Math.abs(value2D(x * frequency, z * frequency, seed + (i * 4099L)));
            signal = signal * signal;
            sum += signal * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }

        return norm == 0.0 ? 0.0 : (sum / norm);
    }

    public static double openSimplex2S2D(double x, double z, long seed) {
        return SIMPLEX_CACHE.computeIfAbsent(seed, OpenSimplex2S::new).eval(x, z);
    }

    public static double fbmOpenSimplex2S2D(double x, double z, long seed, int octaves, double lacunarity, double gain) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;

        for (int i = 0; i < octaves; i++) {
            sum += openSimplex2S2D(x * frequency, z * frequency, seed + (i * 1327L)) * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }

        return norm == 0.0 ? 0.0 : (sum / norm);
    }

    public static double ridgedFbmOpenSimplex2S2D(double x, double z, long seed, int octaves, double lacunarity, double gain) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;

        for (int i = 0; i < octaves; i++) {
            double signal = 1.0 - Math.abs(openSimplex2S2D(x * frequency, z * frequency, seed + (i * 4099L)));
            signal = signal * signal;
            sum += signal * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }

        return norm == 0.0 ? 0.0 : (sum / norm);
    }

    private static double hashToUnit(int x, int z, long seed) {
        long h = seed;
        h ^= x * PRIME_X;
        h ^= z * PRIME_Z;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdl;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53l;
        h ^= (h >>> 33);
        return ((h >>> 11) * (1.0 / (1L << 53))) * 2.0 - 1.0;
    }

    private static int fastFloor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - (2.0 * t));
    }

    private static double lerp(double a, double b, double t) {
        return a + ((b - a) * t);
    }
}
