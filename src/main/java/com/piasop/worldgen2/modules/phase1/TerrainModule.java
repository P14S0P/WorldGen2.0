package com.piasop.worldgen2.modules.phase1;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;

/**
 * Phase 1 terrain prototype based on domain warping + ridged fractal detail.
 */
public final class TerrainModule implements WG2Module {
    private WG2Config config;
    private WG2DataCache cache;

    @Override
    public String getId() {
        return "wg2:terrain";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public GenerationPhase getPhase() {
        return GenerationPhase.NOISE;
    }

    @Override
    public boolean canRunAsync() {
        return true;
    }

    @Override
    public void initialize(WG2Config config, WG2DataCache cache) {
        this.config = config;
        this.cache = cache;
    }

    @Override
    public void onChunkGenerate(ChunkGenContext ctx) {
        // Stores a deterministic terrain preview in the thread-local workspace for future hooks.
        double[] density = cache.workspace().densityBuffer();
        int idx = 0;
        for (int localZ = 0; localZ < 16 && idx < density.length; localZ++) {
            for (int localX = 0; localX < 16 && idx < density.length; localX++) {
                int worldX = (ctx.chunkX() << 4) + localX;
                int worldZ = (ctx.chunkZ() << 4) + localZ;
                density[idx++] = sampleHeight(worldX, worldZ, ctx.worldSeed());
            }
        }
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        cache.getRegionData(ctx.regionX(), ctx.regionZ(),
                () -> com.piasop.worldgen2.core.cache.RegionData.empty(ctx.regionX(), ctx.regionZ(), ctx.worldSeed()));
    }

    public double sampleHeight(int worldX, int worldZ, long worldSeed) {
        double scale = config.getFloat("terrain.base_scale", 0.0025F);
        double warpScale = config.getFloat("terrain.warp_scale", 0.0065F);
        double warpStrength = config.getFloat("terrain.warp_strength", 42.0F);
        double verticalScale = config.getFloat("terrain.vertical_scale", 78.0F);

        double qx = Phase1Noise.fbm2D(worldX * warpScale, worldZ * warpScale, worldSeed + 17L, 3, 2.0, 0.5);
        double qz = Phase1Noise.fbm2D(worldX * warpScale, worldZ * warpScale, worldSeed + 29L, 3, 2.0, 0.5);

        double warpedX = worldX + (qx * warpStrength);
        double warpedZ = worldZ + (qz * warpStrength);

        double macro = Phase1Noise.fbm2D(worldX * scale * 0.6, worldZ * scale * 0.6, worldSeed + 101L, 4, 2.0, 0.5);
        double ridged = Phase1Noise.ridgedFbm2D(warpedX * scale, warpedZ * scale, worldSeed + 31337L, 4, 2.1, 0.45);

        double base = 72.0 + (macro * 24.0);
        double detail = (ridged - 0.5) * verticalScale;
        return clamp(base + detail, -64.0, 320.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
