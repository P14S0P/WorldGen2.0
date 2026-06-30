package com.piasop.worldgen2.modules.phase1;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;

class Phase1BenchmarkTest {
    @Test
    void compareClimateLookupAgainstVanillaLikeSearch() {
        BiomeModule module = new BiomeModule();
        List<BiomePoint> vanillaCandidates = createVanillaLikeCandidates(320, 1337L);

        int samples = 300_000;
        float[] temp = new float[samples];
        float[] precip = new float[samples];
        Random random = new Random(2026L);
        for (int i = 0; i < samples; i++) {
            temp[i] = -20.0f + (random.nextFloat() * 55.0f);
            precip[i] = 50.0f + (random.nextFloat() * 1200.0f);
        }

        // Warm-up to reduce JIT skew in the measured loops.
        for (int i = 0; i < 50_000; i++) {
            module.chooseBiome(temp[i], precip[i]);
            vanillaLikeNearest(temp[i], precip[i], vanillaCandidates);
        }

        long t0 = System.nanoTime();
        String lastFast = "";
        for (int i = 0; i < samples; i++) {
            lastFast = module.chooseBiome(temp[i], precip[i]);
        }
        long fastNs = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        String lastVanilla = "";
        for (int i = 0; i < samples; i++) {
            lastVanilla = vanillaLikeNearest(temp[i], precip[i], vanillaCandidates);
        }
        long vanillaNs = System.nanoTime() - t1;

        double fastPerLookup = fastNs / (double) samples;
        double vanillaPerLookup = vanillaNs / (double) samples;
        double speedup = vanillaPerLookup / fastPerLookup;

        System.out.printf("WG2 lookup: %.1f ns/op, vanilla-like: %.1f ns/op, speedup x%.2f%n",
                fastPerLookup, vanillaPerLookup, speedup);

        assertFalse(lastFast.isEmpty());
        assertFalse(lastVanilla.isEmpty());
    }

    private static String vanillaLikeNearest(float temp, float precip, List<BiomePoint> points) {
        double bestDist = Double.MAX_VALUE;
        String best = "minecraft:plains";
        for (BiomePoint point : points) {
            double dt = Math.abs(temp - point.temp);
            double dp = Math.abs(precip - point.precip) / 20.0;
            double chebyshev = Math.max(dt, dp);
            if (chebyshev < bestDist) {
                bestDist = chebyshev;
                best = point.biomeId;
            }
        }
        return best;
    }

    private static List<BiomePoint> createVanillaLikeCandidates(int count, long seed) {
        Random random = new Random(seed);
        String[] names = {
                "minecraft:plains", "minecraft:forest", "minecraft:desert", "minecraft:taiga",
                "minecraft:jungle", "minecraft:savanna", "minecraft:badlands", "minecraft:swamp"
        };
        List<BiomePoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new BiomePoint(
                    -20.0f + (random.nextFloat() * 55.0f),
                    50.0f + (random.nextFloat() * 1200.0f),
                    names[i % names.length]
            ));
        }
        return points;
    }

    private record BiomePoint(float temp, float precip, String biomeId) {
    }
}
