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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3 tree baseline: deterministic tree potential + parametric prototype palette.
 */
public final class TreeModule implements WG2Module {
    private static final int REGION_SIZE = 32;
    private static final int SAMPLE_STEP = 8;

    private final VegetationModule vegetation = new VegetationModule();
    private final LSystemRenderer renderer = new LSystemRenderer();
    private final ConcurrentHashMap<Long, TreeRegionData> regions = new ConcurrentHashMap<>();

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
        TreePrototype[] palette = defaultPalette();

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
        int minBuildY = chunk.getMinBuildHeight();
        int maxBuildY = minBuildY + chunk.getHeight() - 1;
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        Optional<ClimateModule> climate = WG2Registry.get("wg2:climate")
                .filter(ClimateModule.class::isInstance)
                .map(ClimateModule.class::cast);
        Optional<TerrainModule> terrain = WG2Registry.get("wg2:terrain")
                .filter(TerrainModule.class::isInstance)
                .map(TerrainModule.class::cast);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localZ = 0; localZ < 16; localZ += SAMPLE_STEP) {
            for (int localX = 0; localX < 16; localX += SAMPLE_STEP) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                float density = sampleTreeDensity(worldX, worldZ, seed);
                if (density < 0.64f) {
                    continue;
                }

                double jitter = (Phase1Noise.value2D(worldX * 0.033, worldZ * 0.033, seed + 41813L) + 1.0) * 0.5;
                if (((density * 0.82) + (jitter * 0.18)) < 0.72) {
                    continue;
                }

                TreePrototype prototype = samplePrototype(worldX, worldZ, seed);
                float precipitation = climate.map(c -> c.samplePrecipitation(worldX, worldZ, seed)).orElse(500.0f);
                float moisture = (float) clamp((precipitation - 180.0) / 900.0, 0.0, 1.0);
                double elevation = terrain.map(t -> t.sampleHeight(worldX, worldZ, seed)).orElse(90.0);
                double windTilt = clamp((elevation - 100.0) / 220.0, 0.0, 1.0) * 8.0;
                LSystemRenderer.RenderedTree layout = renderPrototype(prototype, moisture, windTilt);

                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ) - 1;
                int trunkBaseY = clampInt(surfaceY + 1, minBuildY + 1, maxBuildY - 8);
                placeRenderedTree(chunk, cursor, layout, prototype, baseX + localX, trunkBaseY, baseZ + localZ, minBuildY, maxBuildY);
            }
        }
    }

    private void placeRenderedTree(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            LSystemRenderer.RenderedTree layout,
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

        for (LSystemRenderer.Voxel voxel : layout.logs()) {
            int worldX = baseX + voxel.x();
            int worldY = baseY + voxel.y();
            int worldZ = baseZ + voxel.z();
            if (worldX < chunkMinX || worldX > chunkMaxX || worldZ < chunkMinZ || worldZ > chunkMaxZ) {
                continue;
            }
            if (worldY < minBuildY + 1 || worldY > maxBuildY - 1) {
                continue;
            }
            cursor.set(worldX, worldY, worldZ);
            chunk.setBlockState(cursor, logState, false);
        }

        for (LSystemRenderer.Voxel voxel : layout.leaves()) {
            int worldX = baseX + voxel.x();
            int worldY = baseY + voxel.y();
            int worldZ = baseZ + voxel.z();
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

    private static TreePrototype[] defaultPalette() {
        return new TreePrototype[]{
                new TreePrototype("oak_lowland", SpeciesStyle.OAK, 7, 11, 3, 5, 25.0, 4, 5),
                new TreePrototype("birch_temperate", SpeciesStyle.BIRCH, 8, 12, 2, 4, 18.0, 4, 5),
                new TreePrototype("pine_cool", SpeciesStyle.PINE, 10, 16, 2, 3, 22.0, 5, 6),
                new TreePrototype("acacia_dry", SpeciesStyle.ACACIA, 6, 9, 4, 6, 32.0, 3, 4),
                new TreePrototype("willow_riparian", SpeciesStyle.WILLOW, 7, 11, 4, 6, 28.0, 4, 5),
                new TreePrototype("jungle_humid", SpeciesStyle.JUNGLE, 11, 16, 4, 7, 24.0, 5, 6)
        };
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
}