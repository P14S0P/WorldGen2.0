package com.piasop.worldgen2.modules.phase2;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;
import com.piasop.worldgen2.modules.phase1.Phase1Noise;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Phase 2 cave prototype: domain-warped worm field with vertical cave bands.
 */
public final class CaveModule implements WG2Module {
    private static final double CARVE_THRESHOLD = 0.74;
    private static final int MAX_CARVE_Y = 84;

    private WG2DataCache cache;

    @Override
    public String getId() {
        return "wg2:caves";
    }

    @Override
    public int getPriority() {
        return 40;
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
        this.cache = cache;
    }

    @Override
    public void onChunkGenerate(ChunkGenContext ctx) {
        double[] cave = cache.workspace().caveBuffer();
        int idx = 0;
        int sampleY = 32;
        for (int localZ = 0; localZ < 16 && idx < cave.length; localZ++) {
            for (int localX = 0; localX < 16 && idx < cave.length; localX++) {
                int worldX = (ctx.chunkX() << 4) + localX;
                int worldZ = (ctx.chunkZ() << 4) + localZ;
                cave[idx++] = sampleCaveCarveLikelihood(worldX, sampleY, worldZ, ctx.worldSeed());
            }
        }
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        // Region preprocessing for caves is added in a later iteration.
    }

    public void carveChunkCaves(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int minBuildY = chunk.getMinBuildHeight();
        int maxBuildY = minBuildY + chunk.getHeight() - 1;
        int carveMinY = Math.max(minBuildY + 8, -56);
        int carveMaxY = Math.min(maxBuildY - 6, MAX_CARVE_Y);
        if (carveMaxY <= carveMinY) {
            return;
        }

        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;

                for (int y = carveMinY; y <= carveMaxY; y++) {
                    if (!shouldCarveAt(worldX, y, worldZ, seed)) {
                        continue;
                    }

                    cursor.set(worldX, y, worldZ);
                    BlockState current = chunk.getBlockState(cursor);
                    if (current.isAir() || current.getFluidState().isSource()) {
                        continue;
                    }
                    chunk.setBlockState(cursor, air, false);
                }
            }
        }
    }

    boolean shouldCarveAt(int worldX, int worldY, int worldZ, long seed) {
        double likelihood = sampleCaveCarveLikelihood(worldX, worldY, worldZ, seed);
        if (likelihood < CARVE_THRESHOLD) {
            return false;
        }

        double tube = (Phase1Noise.fbm2D(worldX * 0.030, worldZ * 0.030, seed + 1993L, 2, 2.0, 0.5) + 1.0) * 0.5;
        double verticalNoise = (Phase1Noise.value2D(worldX * 0.021, (worldY * 0.021) + (worldZ * 0.007), seed + 2647L) + 1.0) * 0.5;
        return (tube * 0.55) + (verticalNoise * 0.45) > 0.48;
    }

    public double sampleCaveCarveLikelihood(int worldX, int worldY, int worldZ, long seed) {
        double warpX = Phase1Noise.fbm2D(worldX * 0.007, worldZ * 0.007, seed + 401L, 3, 2.0, 0.5) * 18.0;
        double warpZ = Phase1Noise.fbm2D(worldX * 0.007, worldZ * 0.007, seed + 809L, 3, 2.0, 0.5) * 18.0;

        double wx = worldX + warpX;
        double wz = worldZ + warpZ;

        double worm = Math.sin(wx * 0.045) + Math.cos(wz * 0.041);
        double shape = 1.0 - Math.abs(worm * 0.5);

        double detail = (Phase1Noise.fbm2D(wx * 0.012, wz * 0.012, seed + 1237L, 4, 2.1, 0.48) + 1.0) * 0.5;
        double vertical = verticalBand(worldY);
        return clamp((shape * 0.55) + (detail * 0.30) + (vertical * 0.15), 0.0, 1.0);
    }

    private static double verticalBand(int y) {
        double normalized = (y + 64.0) / 192.0;
        double centerBias = 1.0 - Math.abs(normalized - 0.45) * 2.2;
        return clamp(centerBias, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
