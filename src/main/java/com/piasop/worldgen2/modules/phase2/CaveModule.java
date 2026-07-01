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
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Phase 2 cave prototype: domain-warped worm field with vertical cave bands.
 */
public final class CaveModule implements WG2Module {
    private static final double CARVE_THRESHOLD = 0.74;
    private static final int MAX_CARVE_Y = 56;
    private static final int SEA_LEVEL = 63;
    private static final int SURFACE_ROOF_THICKNESS = 10;
    private static final int Y_STEP = 2;
    private static final double COLUMN_GATE_THRESHOLD = 0.43;

    private record ColumnCaveProfile(double shape, double detail, double tube, int worldX, int worldZ, long seed) {}

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

        int yCount = ((carveMaxY - carveMinY) / Y_STEP) + 1;
        boolean[] initial = new boolean[16 * 16 * yCount];
        boolean[] smooth = new boolean[initial.length];

        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;

                if (sampleColumnPotential(worldX, worldZ, seed) < COLUMN_GATE_THRESHOLD) {
                    continue;
                }

                ColumnCaveProfile profile = buildColumnProfile(worldX, worldZ, seed);

                for (int yi = 0; yi < yCount; yi++) {
                    int y = carveMinY + (yi * Y_STEP);
                    if (shouldCarveAt(profile, y)) {
                        initial[voxelIndex(localX, localZ, yi, yCount)] = true;
                    }
                }
            }
        }

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int yi = 0; yi < yCount; yi++) {
                    int idx = voxelIndex(localX, localZ, yi, yCount);
                    int neighbors = openNeighborCount(initial, localX, localZ, yi, yCount);
                    if (initial[idx]) {
                        smooth[idx] = neighbors >= 2;
                    } else {
                        smooth[idx] = neighbors >= 4;
                    }
                }
            }
        }

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                int solidSurfaceY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, localX, localZ) - 1;
                solidSurfaceY = clampInt(solidSurfaceY, minBuildY, maxBuildY);
                int worldSurfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ) - 1;
                worldSurfaceY = clampInt(worldSurfaceY, minBuildY, maxBuildY);
                boolean submergedColumn = worldSurfaceY > solidSurfaceY;
                for (int yi = 0; yi < yCount; yi++) {
                    if (!smooth[voxelIndex(localX, localZ, yi, yCount)]) {
                        continue;
                    }

                    int y = carveMinY + (yi * Y_STEP);
                    if (y > solidSurfaceY - SURFACE_ROOF_THICKNESS) {
                        continue;
                    }

                    carveAt(chunk, cursor, air, water, worldX, worldZ, y - 1, solidSurfaceY, submergedColumn, minBuildY);
                    carveAt(chunk, cursor, air, water, worldX, worldZ, y, solidSurfaceY, submergedColumn, minBuildY);
                    carveAt(chunk, cursor, air, water, worldX, worldZ, y + 1, solidSurfaceY, submergedColumn, minBuildY);
                }
            }
        }
    }

    private static void carveAt(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            BlockState air,
            BlockState water,
            int worldX,
            int worldZ,
            int y,
            int solidSurfaceY,
            boolean submergedColumn,
            int minBuildY) {
        if (y <= minBuildY || y > solidSurfaceY - SURFACE_ROOF_THICKNESS) {
            return;
        }
        BlockState carveState = (submergedColumn && y <= SEA_LEVEL) ? water : air;
        carveVoxel(chunk, cursor, carveState, worldX, y, worldZ);
    }

    boolean shouldCarveAt(int worldX, int worldY, int worldZ, long seed) {
        ColumnCaveProfile profile = buildColumnProfile(worldX, worldZ, seed);
        return shouldCarveAt(profile, worldY);
    }

    private static boolean shouldCarveAt(ColumnCaveProfile profile, int worldY) {
        double likelihood = sampleCaveCarveLikelihood(profile, worldY);
        if (likelihood < CARVE_THRESHOLD) {
            return false;
        }

        double verticalNoise = (Phase1Noise.value2D(
                profile.worldX() * 0.021,
                (worldY * 0.021) + (profile.worldZ() * 0.007),
                profile.seed() + 2647L
        ) + 1.0) * 0.5;
        return (profile.tube() * 0.55) + (verticalNoise * 0.45) > 0.48;
    }

    public double sampleCaveCarveLikelihood(int worldX, int worldY, int worldZ, long seed) {
        ColumnCaveProfile profile = buildColumnProfile(worldX, worldZ, seed);
        return sampleCaveCarveLikelihood(profile, worldY);
    }

    private static double sampleCaveCarveLikelihood(ColumnCaveProfile profile, int worldY) {
        double vertical = verticalBand(worldY);
        double abyssal = abyssalBand(worldY);
        return clamp((profile.shape() * 0.45) + (profile.detail() * 0.28) + (vertical * 0.17) + (abyssal * 0.10), 0.0, 1.0);
    }

    private static ColumnCaveProfile buildColumnProfile(int worldX, int worldZ, long seed) {
        double warpX = Phase1Noise.fbm2D(worldX * 0.007, worldZ * 0.007, seed + 401L, 3, 2.0, 0.5) * 18.0;
        double warpZ = Phase1Noise.fbm2D(worldX * 0.007, worldZ * 0.007, seed + 809L, 3, 2.0, 0.5) * 18.0;

        double wx = worldX + warpX;
        double wz = worldZ + warpZ;

        double worm = Math.sin(wx * 0.045) + Math.cos(wz * 0.041);
        double shape = 1.0 - Math.abs(worm * 0.5);

        double detail = (Phase1Noise.fbm2D(wx * 0.012, wz * 0.012, seed + 1237L, 4, 2.1, 0.48) + 1.0) * 0.5;
        double tube = (Phase1Noise.fbm2D(worldX * 0.030, worldZ * 0.030, seed + 1993L, 2, 2.0, 0.5) + 1.0) * 0.5;
        return new ColumnCaveProfile(shape, detail, tube, worldX, worldZ, seed);
    }

    private static double abyssalBand(int y) {
        if (y > -20) {
            return 0.0;
        }
        double t = clamp(((-20.0 - y) / 44.0), 0.0, 1.0);
        return t * t;
    }

    double sampleAbyssalContribution(int worldY) {
        return abyssalBand(worldY);
    }

    private static double sampleColumnPotential(int worldX, int worldZ, long seed) {
        return (Phase1Noise.fbm2D(worldX * 0.018, worldZ * 0.018, seed + 3121L, 2, 2.0, 0.5) + 1.0) * 0.5;
    }

    private static int voxelIndex(int x, int z, int yIndex, int yCount) {
        return (((z * 16) + x) * yCount) + yIndex;
    }

    private static int openNeighborCount(boolean[] grid, int x, int z, int yi, int yCount) {
        int count = 0;
        if (x > 0 && grid[voxelIndex(x - 1, z, yi, yCount)]) {
            count++;
        }
        if (x < 15 && grid[voxelIndex(x + 1, z, yi, yCount)]) {
            count++;
        }
        if (z > 0 && grid[voxelIndex(x, z - 1, yi, yCount)]) {
            count++;
        }
        if (z < 15 && grid[voxelIndex(x, z + 1, yi, yCount)]) {
            count++;
        }
        if (yi > 0 && grid[voxelIndex(x, z, yi - 1, yCount)]) {
            count++;
        }
        if (yi + 1 < yCount && grid[voxelIndex(x, z, yi + 1, yCount)]) {
            count++;
        }
        return count;
    }

    private static void carveVoxel(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            BlockState carveState,
            int worldX,
            int y,
            int worldZ) {
        cursor.set(worldX, y, worldZ);
        BlockState current = chunk.getBlockState(cursor);
        boolean targetIsAir = carveState.is(Blocks.AIR);
        if (targetIsAir && (current.isAir() || current.getFluidState().isSource())) {
            return;
        }
        if (!targetIsAir && current.getFluidState().isSource()) {
            return;
        }
        chunk.setBlockState(cursor, carveState, false);
    }

    private static double verticalBand(int y) {
        double normalized = (y + 64.0) / 192.0;
        double centerBias = 1.0 - Math.abs(normalized - 0.45) * 2.2;
        return clamp(centerBias, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
