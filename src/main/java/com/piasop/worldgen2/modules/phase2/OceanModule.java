package com.piasop.worldgen2.modules.phase2;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;
import com.piasop.worldgen2.modules.phase1.Phase1Noise;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 2 ocean prototype: continental shelf, abyssal plains, ridges and trenches.
 */
public final class OceanModule implements WG2Module {
    private static final int REGION_SIZE = 32;
    private static final int DEFAULT_SEA_LEVEL = 63;

    private final ConcurrentHashMap<Long, OceanRegionData> regions = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:ocean";
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    public GenerationPhase getPhase() {
        return GenerationPhase.MACRO;
    }

    @Override
    public boolean canRunAsync() {
        return true;
    }

    @Override
    public void initialize(WG2Config config, WG2DataCache cache) {
    }

    @Override
    public void onChunkGenerate(ChunkGenContext ctx) {
        int regionX = Math.floorDiv(ctx.chunkX(), REGION_SIZE);
        int regionZ = Math.floorDiv(ctx.chunkZ(), REGION_SIZE);
        regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionOcean(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()), k -> generateRegionOcean(ctx));
    }

    public OceanRegionData generateRegionOcean(RegionGenContext ctx) {
        int size = REGION_SIZE;
        int count = size * size;
        float[] floorY = new float[count];
        float[] oceanMask = new float[count];

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;

        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = idx(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float continentalness = sampleContinentalness(worldX, worldZ, ctx.worldSeed());
                boolean ocean = continentalness < 0.46f;
                oceanMask[idx] = ocean ? 1.0f : 0.0f;
                floorY[idx] = ocean ? (float) computeOceanFloor(worldX, worldZ, ctx.worldSeed(), continentalness)
                        : DEFAULT_SEA_LEVEL;
            }
        }

        applyCoastalShelfSmoothing(floorY, oceanMask, size, ctx.worldSeed(), baseChunkX, baseChunkZ);
        return new OceanRegionData(size, floorY, oceanMask);
    }

    public float sampleOceanMask(int worldX, int worldZ, long seed) {
        OceanRegionData data = regionForWorld(worldX, worldZ, seed);
        int[] local = localIndex(worldX, worldZ, data.size());
        return data.oceanMask()[idx(local[0], local[1], data.size())];
    }

    public float sampleOceanFloorY(int worldX, int worldZ, long seed) {
        OceanRegionData data = regionForWorld(worldX, worldZ, seed);
        int[] local = localIndex(worldX, worldZ, data.size());
        return data.floorY()[idx(local[0], local[1], data.size())];
    }

    private OceanRegionData regionForWorld(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        return regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionOcean(new RegionGenContext(regionX, regionZ, seed)));
    }

    private static float sampleContinentalness(int worldX, int worldZ, long seed) {
        double base = (Phase1Noise.fbm2D(worldX * 0.0007, worldZ * 0.0007, seed + 1501L, 4, 2.0, 0.5) + 1.0) * 0.5;
        double detail = (Phase1Noise.value2D(worldX * 0.0020, worldZ * 0.0020, seed + 1931L) + 1.0) * 0.5;
        return (float) clamp((base * 0.82) + (detail * 0.18), 0.0, 1.0);
    }

    private static double computeOceanFloor(int worldX, int worldZ, long seed, float continentalness) {
        double shelfT = clamp((continentalness - 0.20) / 0.26, 0.0, 1.0);
        double shelfDepth = lerp(DEFAULT_SEA_LEVEL - 5.0, DEFAULT_SEA_LEVEL - 42.0, shelfT);

        double abyssNoise = (Phase1Noise.fbm2D(worldX * 0.0012, worldZ * 0.0012, seed + 2111L, 4, 2.0, 0.5) + 1.0) * 0.5;
        double abyssDepth = DEFAULT_SEA_LEVEL - 62.0 - (abyssNoise * 20.0);

        double ridge = 1.0 - Phase1Noise.ridgedFbm2D(worldX * 0.0028, worldZ * 0.0028, seed + 2713L, 3, 2.0, 0.5);
        double ridgeBoost = ridge * 10.0;

        double trenchNoise = (Phase1Noise.value2D(worldX * 0.0019, worldZ * 0.0019, seed + 3331L) + 1.0) * 0.5;
        double trench = trenchNoise > 0.82 ? -14.0 * ((trenchNoise - 0.82) / 0.18) : 0.0;

        double blend = smoothstep(0.15, 0.75, shelfT);
        return lerp(shelfDepth, abyssDepth, blend) + ridgeBoost + trench;
    }

    private static void applyCoastalShelfSmoothing(
            float[] floorY,
            float[] oceanMask,
            int size,
            long seed,
            int baseChunkX,
            int baseChunkZ) {
        float[] copy = Arrays.copyOf(floorY, floorY.length);
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dz = {-1, -1, -1, 0, 0, 1, 1, 1};

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int i = idx(x, z, size);
                if (oceanMask[i] < 0.5f) {
                    continue;
                }

                boolean nearLand = false;
                for (int n = 0; n < dx.length; n++) {
                    int nx = x + dx[n];
                    int nz = z + dz[n];
                    if (nx < 0 || nz < 0 || nx >= size || nz >= size) {
                        continue;
                    }
                    if (oceanMask[idx(nx, nz, size)] < 0.5f) {
                        nearLand = true;
                        break;
                    }
                }

                if (!nearLand) {
                    continue;
                }

                int worldX = ((baseChunkX + x) << 4) + 8;
                int worldZ = ((baseChunkZ + z) << 4) + 8;
                double shelfWobble = (Phase1Noise.value2D(worldX * 0.005, worldZ * 0.005, seed + 4099L) + 1.0) * 0.5;
                double target = lerp(DEFAULT_SEA_LEVEL - 8.0, DEFAULT_SEA_LEVEL - 28.0, shelfWobble);
                floorY[i] = (float) lerp(copy[i], target, 0.45);
            }
        }
    }

    private static int[] localIndex(int worldX, int worldZ, int size) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        return new int[]{Math.floorMod(chunkX, size), Math.floorMod(chunkZ, size)};
    }

    private static long regionKey(int rx, int rz) {
        return (((long) rx) << 32) ^ (rz & 0xffffffffL);
    }

    private static int idx(int x, int z, int size) {
        return (z * size) + x;
    }

    private static double lerp(double a, double b, double t) {
        return a + ((b - a) * t);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - (2.0 * t));
    }

    public record OceanRegionData(int size, float[] floorY, float[] oceanMask) {
    }
}
