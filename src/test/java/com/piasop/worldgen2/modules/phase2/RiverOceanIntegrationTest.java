package com.piasop.worldgen2.modules.phase2;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RiverOceanIntegrationTest {
    private final RiverModule river = new RiverModule();
    private final OceanModule ocean = new OceanModule();

    @Test
    void deterministicCrossModuleSamplingForSameSeed() {
        long seed = 13579L;
        int x = 384;
        int z = -912;

        double riverA = river.sampleRiverMask(x, z, seed);
        double riverB = river.sampleRiverMask(x, z, seed);
        float oceanA = ocean.sampleOceanMask(x, z, seed);
        float oceanB = ocean.sampleOceanMask(x, z, seed);

        assertEquals(riverA, riverB, 1.0e-12);
        assertEquals(oceanA, oceanB, 1.0e-6f);
    }

    @Test
    void majorRiverHotspotsCanFindNearbyOceanBasins() {
        long seed = 424242L;
        List<int[]> hotspots = new ArrayList<>();

        for (int x = -3072; x <= 3072; x += 64) {
            for (int z = -3072; z <= 3072; z += 64) {
                double riverMask = river.sampleRiverMask(x, z, seed);
                if (riverMask >= 0.70) {
                    hotspots.add(new int[]{x, z});
                }
            }
        }

        assertTrue(hotspots.size() >= 20, "Expected enough major-river hotspots for integration sampling");

        int connected = 0;
        int evaluated = Math.min(30, hotspots.size());
        for (int i = 0; i < evaluated; i++) {
            int[] p = hotspots.get(i);
            if (hasOceanNearby(p[0], p[1], seed, 896, 32)) {
                connected++;
            }
        }

        assertTrue(connected >= Math.max(12, (int) (evaluated * 0.45)),
                "Expected a meaningful subset of river hotspots to connect to oceanic basins");
    }

    @Test
    void coastAdjacencyShowsHydrologyTransition() {
        long seed = 987654L;
        int checks = 0;
        double maxDelta = 0.0;

        for (int x = -2048; x <= 2048; x += 64) {
            for (int z = -2048; z <= 2048; z += 64) {
                float oceanMask = ocean.sampleOceanMask(x, z, seed);
                if (oceanMask < 0.35f || oceanMask > 0.65f) {
                    continue;
                }

                checks++;
                double riverCenter = river.sampleRiverMask(x, z, seed);
                double riverOffset = river.sampleRiverMask(x + 96, z + 96, seed);
                maxDelta = Math.max(maxDelta, Math.abs(riverCenter - riverOffset));
            }
        }

        assumeTrue(checks > 0, "No coast-adjacent samples for this deterministic seed/grid");
        assertTrue(maxDelta > 0.005,
                "Expected river intensity transitions around coastline blend areas");
    }

    private boolean hasOceanNearby(int worldX, int worldZ, long seed, int radius, int step) {
        for (int r = 0; r <= radius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                int dz = r;
                if (ocean.sampleOceanMask(worldX + dx, worldZ + dz, seed) >= 0.5f) {
                    return true;
                }
                if (ocean.sampleOceanMask(worldX + dx, worldZ - dz, seed) >= 0.5f) {
                    return true;
                }
            }
            for (int dz = -r + step; dz <= r - step; dz += step) {
                int dx = r;
                if (ocean.sampleOceanMask(worldX + dx, worldZ + dz, seed) >= 0.5f) {
                    return true;
                }
                if (ocean.sampleOceanMask(worldX - dx, worldZ + dz, seed) >= 0.5f) {
                    return true;
                }
            }
        }
        return false;
    }
}
