package com.piasop.worldgen2.modules.phase2;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;
import com.piasop.worldgen2.core.WG2Registry;
import com.piasop.worldgen2.modules.phase1.Phase1Noise;
import com.piasop.worldgen2.modules.phase1.TerrainModule;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 2 river prototype: D8 routing and accumulation on macro regions.
 */
public final class RiverModule implements WG2Module {
    private static final int REGION_SIZE = 32;

    private final ConcurrentHashMap<Long, RiverRegionData> regions = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:rivers";
    }

    @Override
    public int getPriority() {
        return 25;
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
        long key = regionKey(regionX, regionZ);
        regions.computeIfAbsent(key, k -> generateRegionFlow(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()), key -> generateRegionFlow(ctx));
    }

    public RiverRegionData generateRegionFlow(RegionGenContext ctx) {
        int size = REGION_SIZE;
        double[] heights = new double[size * size];
        int[] downstream = new int[size * size];
        Arrays.fill(downstream, -1);
        double[] accumulation = new double[size * size];
        Arrays.fill(accumulation, 1.0);

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;
        Optional<TerrainModule> terrain = WG2Registry.get("wg2:terrain").filter(TerrainModule.class::isInstance).map(TerrainModule.class::cast);

        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;
                heights[idx] = terrain.map(t -> t.sampleHeight(worldX, worldZ, ctx.worldSeed()))
                        .orElseGet(() -> fallbackHeight(worldX, worldZ, ctx.worldSeed()));
            }
        }

        int[] nDx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] nDz = {-1, -1, -1, 0, 0, 1, 1, 1};
        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                double current = heights[idx];
                int best = -1;
                double bestDrop = 0.0;
                for (int i = 0; i < nDx.length; i++) {
                    int nx = rx + nDx[i];
                    int nz = rz + nDz[i];
                    if (nx < 0 || nz < 0 || nx >= size || nz >= size) {
                        continue;
                    }
                    int nIdx = index(nx, nz, size);
                    double drop = current - heights[nIdx];
                    if (drop > bestDrop) {
                        bestDrop = drop;
                        best = nIdx;
                    }
                }
                downstream[idx] = best;
            }
        }

        Integer[] order = new Integer[size * size];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> heights[i]).reversed());

        for (int idx : order) {
            int to = downstream[idx];
            if (to >= 0) {
                accumulation[to] += accumulation[idx];
            }
        }

        return new RiverRegionData(size, heights, downstream, accumulation);
    }

    public double sampleRiverMask(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        RiverRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                key -> generateRegionFlow(new RegionGenContext(regionX, regionZ, seed)));

        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());

        double acc = data.accumulation()[idx];
        double normalized = Math.min(1.0, acc / 18.0);
        double meander = (Phase1Noise.value2D(worldX * 0.006, worldZ * 0.006, seed + 2209L) + 1.0) * 0.5;
        double shaped = normalized * (0.72 + (meander * 0.28));
        return clamp(shaped, 0.0, 1.0);
    }

    public RiverRegionData getRegionData(int regionX, int regionZ, long seed) {
        return regions.computeIfAbsent(regionKey(regionX, regionZ),
                key -> generateRegionFlow(new RegionGenContext(regionX, regionZ, seed)));
    }

    private static double fallbackHeight(int worldX, int worldZ, long seed) {
        double macro = Phase1Noise.fbm2D(worldX * 0.0018, worldZ * 0.0018, seed + 701L, 4, 2.0, 0.5);
        double detail = Phase1Noise.ridgedFbm2D(worldX * 0.0027, worldZ * 0.0027, seed + 1777L, 3, 2.0, 0.5);
        return 72.0 + (macro * 24.0) + ((detail - 0.5) * 22.0);
    }

    private static int index(int x, int z, int size) {
        return (z * size) + x;
    }

    private static long regionKey(int rx, int rz) {
        return (((long) rx) << 32) ^ (rz & 0xffffffffL);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record RiverRegionData(int size, double[] heights, int[] downstream, double[] accumulation) {
    }
}
