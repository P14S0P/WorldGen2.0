package com.piasop.worldgen2.modules.phase3;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;
import com.piasop.worldgen2.core.WG2Registry;
import com.piasop.worldgen2.modules.phase1.ClimateModule;
import com.piasop.worldgen2.modules.phase1.Phase1Noise;
import com.piasop.worldgen2.modules.phase1.TerrainModule;
import com.piasop.worldgen2.modules.phase2.OceanModule;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3 tree baseline: deterministic tree potential + parametric prototype palette.
 */
public final class TreeModule implements WG2Module {
    private static final int REGION_SIZE = 32;
    private static final int SAMPLE_STEP = 8;
    private static final int CHUNK_EDGE_MARGIN = 4;
    private static final TreePrototype[] DEFAULT_PALETTE = new TreePrototype[]{
            new TreePrototype("oak_lowland", SpeciesStyle.OAK, 7, 11, 3, 5, 25.0, 4, 5),
            new TreePrototype("birch_temperate", SpeciesStyle.BIRCH, 8, 12, 2, 4, 18.0, 4, 5),
            new TreePrototype("pine_cool", SpeciesStyle.PINE, 10, 16, 2, 3, 22.0, 5, 6),
            new TreePrototype("acacia_dry", SpeciesStyle.ACACIA, 6, 9, 4, 6, 32.0, 3, 4),
            new TreePrototype("willow_riparian", SpeciesStyle.WILLOW, 7, 11, 4, 6, 28.0, 4, 5),
            new TreePrototype("jungle_humid", SpeciesStyle.JUNGLE, 11, 16, 4, 7, 24.0, 5, 6)
    };

    private final VegetationModule vegetation = new VegetationModule();
    private final LSystemRenderer renderer = new LSystemRenderer();
    private final ConcurrentHashMap<Long, TreeRegionData> regions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PackedRenderedTree> renderedPrototypeCache = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:trees";
    }

    @Override
    public int getPriority() {
        return 56;
    }

    @Override
    public GenerationPhase getPhase() {
        return GenerationPhase.FEATURES;
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
                k -> generateRegionTrees(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()),
                k -> generateRegionTrees(ctx));
    }

    public TreeRegionData generateRegionTrees(RegionGenContext ctx) {
        int size = REGION_SIZE;
        float[] treeDensity = new float[size * size];
        byte[] prototypeIndex = new byte[size * size];

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;
        TreePrototype[] palette = DEFAULT_PALETTE;

        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float vegetationDensity = vegetation.sampleVegetationDensity(worldX, worldZ, ctx.worldSeed());
                float vegetationDiversity = vegetation.sampleVegetationDiversity(worldX, worldZ, ctx.worldSeed());
                double localNoise = (Phase1Noise.fbmOpenSimplex2S2D(worldX * 0.004, worldZ * 0.004,
                        ctx.worldSeed() + 11009L, 2, 2.0, 0.5) + 1.0) * 0.5;

                treeDensity[idx] = (float) clamp((vegetationDensity * 0.72) + (localNoise * 0.28), 0.0, 1.0);
                prototypeIndex[idx] = pickPrototypeIndex(vegetationDiversity, worldX, worldZ, ctx.worldSeed(), palette.length);
            }
        }

        return new TreeRegionData(size, treeDensity, prototypeIndex, palette);
    }

    public float sampleTreeDensity(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        TreeRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionTrees(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.treeDensity()[index(localChunkX, localChunkZ, data.size())];
    }

    public TreePrototype samplePrototype(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        TreeRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionTrees(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());
        int paletteIdx = Byte.toUnsignedInt(data.prototypeIndex()[idx]);
        return data.palette()[Math.min(paletteIdx, data.palette().length - 1)];
    }

    public String generatePrototypePattern(TreePrototype prototype, float moisture, double windTiltDegrees) {
        return createLSystem(prototype, moisture).generate();
    }

    public LSystemRenderer.RenderedTree renderPrototype(TreePrototype prototype, float moisture, double windTiltDegrees) {
        String pattern = generatePrototypePattern(prototype, moisture, windTiltDegrees);
        return renderer.render(pattern, prototype, windTiltDegrees);
    }

    public void applyTreesToChunk(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;
        int minBuildY = chunk.getMinBuildHeight();
        int maxBuildY = minBuildY + chunk.getHeight() - 1;
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        int centerX = baseX + 8;
        int centerZ = baseZ + 8;
        TreeCellSample chunkSample = sampleTreeCellForChunk(chunkX, chunkZ, seed);
        float chunkDensity = chunkSample.density();
        if (chunkDensity < 0.64f) {
            return;
        }
        TreePrototype chunkPrototype = chunkSample.prototype();
        ClimateModule climate = resolveClimateModule();
        TerrainModule terrain = resolveTerrainModule();
        float chunkPrecipitation = climate != null ? climate.samplePrecipitation(centerX, centerZ, seed) : 500.0f;
        float chunkMoisture = (float) clamp((chunkPrecipitation - 180.0) / 900.0, 0.0, 1.0);
        double chunkElevation = terrain != null ? terrain.sampleHeight(centerX, centerZ, seed) : 90.0;
        double chunkWindTilt = clamp((chunkElevation - 100.0) / 220.0, 0.0, 1.0) * 8.0;
        OceanModule ocean = resolveOceanModule();
        PackedRenderedTree chunkLayout = renderPrototypeCached(chunkPrototype, chunkMoisture, chunkWindTilt);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localZ = 0; localZ < 16; localZ += SAMPLE_STEP) {
            for (int localX = 0; localX < 16; localX += SAMPLE_STEP) {
                if (localX < CHUNK_EDGE_MARGIN || localX > (15 - CHUNK_EDGE_MARGIN)
                        || localZ < CHUNK_EDGE_MARGIN || localZ > (15 - CHUNK_EDGE_MARGIN)) {
                    continue;
                }

                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                double jitter = (Phase1Noise.value2D(worldX * 0.033, worldZ * 0.033, seed + 41813L) + 1.0) * 0.5;
                if (((chunkDensity * 0.82) + (jitter * 0.18)) < 0.72) {
                    continue;
                }

                if (ocean != null && ocean.sampleOceanMask(worldX, worldZ, seed) >= 0.5f) {
                    continue;
                }

                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ) - 1;
                int oceanFloorY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, localX, localZ) - 1;
                // Skip submerged columns; on dry land WORLD_SURFACE and OCEAN_FLOOR are typically equal.
                if (surfaceY < oceanFloorY) {
                    continue;
                }

                cursor.set(worldX, surfaceY, worldZ);
                BlockState supportState = chunk.getBlockState(cursor);
                if (supportState.isAir() || !supportState.getFluidState().isEmpty()) {
                    continue;
                }
                if (!isNaturalTreeSupport(supportState)) {
                    continue;
                }

                int trunkBaseY = clampInt(surfaceY + 1, minBuildY + 1, maxBuildY - 8);
                placeRenderedTree(chunk, cursor, chunkLayout, chunkPrototype, baseX + localX, trunkBaseY, baseZ + localZ, minBuildY, maxBuildY);
            }
        }
    }

    private TreeCellSample sampleTreeCellForChunk(int chunkX, int chunkZ, long seed) {
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        TreeRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionTrees(new RegionGenContext(regionX, regionZ, seed)));

        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());
        float density = data.treeDensity()[idx];
        int paletteIdx = Byte.toUnsignedInt(data.prototypeIndex()[idx]);
        TreePrototype prototype = data.palette()[Math.min(paletteIdx, data.palette().length - 1)];
        return new TreeCellSample(density, prototype);
    }

    private TreeCellSample sampleTreeCell(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        return sampleTreeCellForChunk(chunkX, chunkZ, seed);
    }

    private PackedRenderedTree renderPrototypeCached(TreePrototype prototype, float moisture, double windTiltDegrees) {
        int moistureBucket = Math.max(0, Math.min(16, (int) Math.round(clamp(moisture, 0.0, 1.0) * 16.0)));
        int windBucket = Math.max(0, Math.min(16, (int) Math.round(clamp(windTiltDegrees / 8.0, 0.0, 1.0) * 16.0)));
        long key = (((long) prototype.style().ordinal()) << 32)
                | (((long) moistureBucket & 0xffffL) << 16)
                | ((long) windBucket & 0xffffL);
        return renderedPrototypeCache.computeIfAbsent(key, k -> {
            float moistureQuantized = moistureBucket / 16.0f;
            double windQuantized = (windBucket / 16.0) * 8.0;
            return packRenderedTree(renderPrototype(prototype, moistureQuantized, windQuantized));
        });
    }

    private void placeRenderedTree(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            PackedRenderedTree layout,
            TreePrototype prototype,
            int baseX,
            int baseY,
            int baseZ,
            int minBuildY,
            int maxBuildY) {
        BlockState logState = prototypeLogState(prototype);
        BlockState leavesState = prototypeLeavesState(prototype);
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int chunkMaxZ = chunkMinZ + 15;

        int[] logs = layout.logs();
        for (int i = 0; i < logs.length; i += 3) {
            int worldX = baseX + logs[i];
            int worldY = baseY + logs[i + 1];
            int worldZ = baseZ + logs[i + 2];
            if (worldX < chunkMinX || worldX > chunkMaxX || worldZ < chunkMinZ || worldZ > chunkMaxZ) {
                continue;
            }
            if (worldY < minBuildY + 1 || worldY > maxBuildY - 1) {
                continue;
            }
            cursor.set(worldX, worldY, worldZ);
            chunk.setBlockState(cursor, logState, false);
        }

        int[] leaves = layout.leaves();
        for (int i = 0; i < leaves.length; i += 3) {
            int worldX = baseX + leaves[i];
            int worldY = baseY + leaves[i + 1];
            int worldZ = baseZ + leaves[i + 2];
            if (worldX < chunkMinX || worldX > chunkMaxX || worldZ < chunkMinZ || worldZ > chunkMaxZ) {
                continue;
            }
            if (worldY < minBuildY + 1 || worldY > maxBuildY - 1) {
                continue;
            }
            cursor.set(worldX, worldY, worldZ);
            if (chunk.getBlockState(cursor).isAir()) {
                chunk.setBlockState(cursor, leavesState, false);
            }
        }
    }

    private LSystem createLSystem(TreePrototype prototype, float moisture) {
        return switch (prototype.style()) {
            case OAK -> new LSystem("F", Map.of('F', "FF+[+F-F-F]-[-F+F+F]"), prototype.iterationsForMoisture(moisture), 1024);
            case WILLOW -> new LSystem("F", Map.of('F', "F[&F][^F][/F][\\F]L"), prototype.iterationsForMoisture(moisture), 960);
            case PINE -> new LSystem("A", Map.of('A', "F[&BL]////[&BL]////[&BL]", 'B', "[&L]"), prototype.iterationsForMoisture(moisture), 1152);
            case BIRCH -> new LSystem("F", Map.of('F', "FF[+FL][-FL]"), prototype.iterationsForMoisture(moisture), 896);
            case ACACIA -> new LSystem("F", Map.of('F', "F[+F]F[-F]L"), prototype.iterationsForMoisture(moisture), 768);
            case JUNGLE -> new LSystem("F", Map.of('F', "FF[+FFL][-FFL]F"), prototype.iterationsForMoisture(moisture), 1280);
        };
    }

    private static byte pickPrototypeIndex(float vegetationDiversity, int worldX, int worldZ, long seed, int paletteSize) {
        double jitter = (Phase1Noise.value2D(worldX * 0.011, worldZ * 0.011, seed + 14143L) + 1.0) * 0.5;
        double selector = clamp((vegetationDiversity * 0.75) + (jitter * 0.25), 0.0, 0.999999);
        int raw = (int) Math.floor(selector * paletteSize);
        return (byte) Math.max(0, Math.min(raw, paletteSize - 1));
    }

    private ClimateModule resolveClimateModule() {
        return WG2Registry.get("wg2:climate")
                .filter(ClimateModule.class::isInstance)
                .map(ClimateModule.class::cast)
                .orElse(null);
    }

    private TerrainModule resolveTerrainModule() {
        return WG2Registry.get("wg2:terrain")
                .filter(TerrainModule.class::isInstance)
                .map(TerrainModule.class::cast)
                .orElse(null);
    }

    private OceanModule resolveOceanModule() {
        return WG2Registry.get("wg2:ocean")
                .filter(OceanModule.class::isInstance)
                .map(OceanModule.class::cast)
                .orElse(null);
    }

    private static boolean isNaturalTreeSupport(BlockState supportState) {
        return supportState.is(BlockTags.DIRT)
                || supportState.is(Blocks.GRASS_BLOCK)
                || supportState.is(Blocks.MYCELIUM)
                || supportState.is(Blocks.PODZOL)
                || supportState.is(Blocks.COARSE_DIRT)
                || supportState.is(Blocks.ROOTED_DIRT)
                || supportState.is(Blocks.MUD)
                || supportState.is(Blocks.SAND)
                || supportState.is(Blocks.RED_SAND);
    }

    private static BlockState prototypeLogState(TreePrototype prototype) {
        return switch (prototype.style()) {
            case BIRCH -> Blocks.BIRCH_LOG.defaultBlockState();
            case PINE -> Blocks.SPRUCE_LOG.defaultBlockState();
            case ACACIA -> Blocks.ACACIA_LOG.defaultBlockState();
            case JUNGLE -> Blocks.JUNGLE_LOG.defaultBlockState();
            case WILLOW, OAK -> Blocks.OAK_LOG.defaultBlockState();
        };
    }

    private static BlockState prototypeLeavesState(TreePrototype prototype) {
        return switch (prototype.style()) {
            case BIRCH -> Blocks.BIRCH_LEAVES.defaultBlockState();
            case PINE -> Blocks.SPRUCE_LEAVES.defaultBlockState();
            case ACACIA -> Blocks.ACACIA_LEAVES.defaultBlockState();
            case JUNGLE -> Blocks.JUNGLE_LEAVES.defaultBlockState();
            case WILLOW, OAK -> Blocks.OAK_LEAVES.defaultBlockState();
        };
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static PackedRenderedTree packRenderedTree(LSystemRenderer.RenderedTree rendered) {
        return new PackedRenderedTree(packVoxels(rendered.logs()), packVoxels(rendered.leaves()));
    }

    private static int[] packVoxels(Set<LSystemRenderer.Voxel> voxels) {
        int[] packed = new int[voxels.size() * 3];
        int i = 0;
        for (LSystemRenderer.Voxel voxel : voxels) {
            packed[i++] = voxel.x();
            packed[i++] = voxel.y();
            packed[i++] = voxel.z();
        }
        return packed;
    }

    public enum SpeciesStyle {
        OAK,
        WILLOW,
        PINE,
        BIRCH,
        ACACIA,
        JUNGLE
    }

    public record TreePrototype(
            String id,
            SpeciesStyle style,
            int minHeight,
            int maxHeight,
            int minCanopyRadius,
            int maxCanopyRadius,
            double branchAngleDegrees,
            int minIterations,
            int maxIterations) {
        int iterationsForMoisture(float moisture) {
            double t = clamp(moisture, 0.0, 1.0);
            return minIterations + (int) Math.round((maxIterations - minIterations) * t);
        }
    }

    public record TreeRegionData(int size, float[] treeDensity, byte[] prototypeIndex, TreePrototype[] palette) {
    }

    private record TreeCellSample(float density, TreePrototype prototype) {
    }

    private record PackedRenderedTree(int[] logs, int[] leaves) {
    }
}