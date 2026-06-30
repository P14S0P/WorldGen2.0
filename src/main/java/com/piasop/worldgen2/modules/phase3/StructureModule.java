package com.piasop.worldgen2.modules.phase3;

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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3 structure baseline: deterministic opportunity map + lightweight prototype grammar tags.
 */
public final class StructureModule implements WG2Module {
    private static final int REGION_SIZE = 32;
    private static final int SAMPLE_STEP = 8;

    private final TreeModule trees = new TreeModule();
    private final ConcurrentHashMap<Long, StructureRegionData> regions = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:structures";
    }

    @Override
    public int getPriority() {
        return 57;
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
                k -> generateRegionStructures(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()),
                k -> generateRegionStructures(ctx));
    }

    public StructureRegionData generateRegionStructures(RegionGenContext ctx) {
        int size = REGION_SIZE;
        float[] spawnChance = new float[size * size];
        byte[] prototypeIndex = new byte[size * size];
        StructurePrototype[] palette = defaultPalette();

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;
        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float treeDensity = trees.sampleTreeDensity(worldX, worldZ, ctx.worldSeed());
                double spacingNoise = (Phase1Noise.fbmOpenSimplex2S2D(worldX * 0.0022, worldZ * 0.0022,
                        ctx.worldSeed() + 16057L, 3, 2.0, 0.5) + 1.0) * 0.5;
                double rarityNoise = (Phase1Noise.value2D(worldX * 0.007, worldZ * 0.007,
                        ctx.worldSeed() + 18233L) + 1.0) * 0.5;

                spawnChance[idx] = (float) clamp((treeDensity * 0.35) + (spacingNoise * 0.45) + (rarityNoise * 0.20), 0.0, 1.0);
                prototypeIndex[idx] = pickPrototypeIndex(spawnChance[idx], worldX, worldZ, ctx.worldSeed(), palette.length);
            }
        }
        return new StructureRegionData(size, spawnChance, prototypeIndex, palette);
    }

    public float sampleStructureChance(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        StructureRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionStructures(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.spawnChance()[index(localChunkX, localChunkZ, data.size())];
    }

    public StructurePrototype samplePrototype(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        StructureRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionStructures(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());
        int paletteIdx = Byte.toUnsignedInt(data.prototypeIndex()[idx]);
        return data.palette()[Math.min(paletteIdx, data.palette().length - 1)];
    }

    public void applyStructureAnchorsToChunk(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int minBuildY = chunk.getMinBuildHeight();
        int maxBuildY = minBuildY + chunk.getHeight() - 1;
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState anchor = Blocks.COBBLESTONE.defaultBlockState();

        for (int localZ = 0; localZ < 16; localZ += SAMPLE_STEP) {
            for (int localX = 0; localX < 16; localX += SAMPLE_STEP) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                float chance = sampleStructureChance(worldX, worldZ, seed);
                double jitter = (Phase1Noise.value2D(worldX * 0.021, worldZ * 0.021, seed + 31847L) + 1.0) * 0.5;
                if (!shouldPlaceAnchor(chance, jitter)) {
                    continue;
                }

                int surfaceY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, localX, localZ) - 1;
                int placeY = clampInt(surfaceY + 1, minBuildY + 1, maxBuildY - 2);

                cursor.set(worldX, placeY, worldZ);
                if (!chunk.getBlockState(cursor).isAir()) {
                    continue;
                }
                chunk.setBlockState(cursor, anchor, false);

                if (chance > 0.96f) {
                    cursor.set(worldX, placeY + 1, worldZ);
                    if (chunk.getBlockState(cursor).isAir()) {
                        chunk.setBlockState(cursor, anchor, false);
                    }
                }
            }
        }
    }

    boolean shouldPlaceAnchor(float chance, double jitter) {
        return ((chance * 0.82) + (jitter * 0.18)) > 0.90;
    }

    private static byte pickPrototypeIndex(float chance, int worldX, int worldZ, long seed, int paletteSize) {
        double jitter = (Phase1Noise.value2D(worldX * 0.013, worldZ * 0.013, seed + 20389L) + 1.0) * 0.5;
        double selector = clamp((chance * 0.82) + (jitter * 0.18), 0.0, 0.999999);
        int raw = (int) Math.floor(selector * paletteSize);
        return (byte) Math.max(0, Math.min(raw, paletteSize - 1));
    }

    private static StructurePrototype[] defaultPalette() {
        return new StructurePrototype[]{
                new StructurePrototype("camp_small", "node-hub", 0.18f, 0.48f),
                new StructurePrototype("watchtower", "vertical-anchor", 0.32f, 0.66f),
                new StructurePrototype("village_cluster", "grid-branch", 0.55f, 0.92f),
                new StructurePrototype("ruin_scatter", "scatter-noise", 0.12f, 0.40f)
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

    public record StructurePrototype(String id, String grammarTag, float minDensity, float maxDensity) {
    }

    public record StructureRegionData(int size, float[] spawnChance, byte[] prototypeIndex, StructurePrototype[] palette) {
    }
}