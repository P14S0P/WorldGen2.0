package com.piasop.worldgen2.modules.phase3;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;
import com.piasop.worldgen2.modules.phase1.Phase1Noise;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3 tree baseline: deterministic tree potential + parametric prototype palette.
 */
public final class TreeModule implements WG2Module {
    private static final int REGION_SIZE = 32;

    private final VegetationModule vegetation = new VegetationModule();
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

    private static byte pickPrototypeIndex(float vegetationDiversity, int worldX, int worldZ, long seed, int paletteSize) {
        double jitter = (Phase1Noise.value2D(worldX * 0.011, worldZ * 0.011, seed + 14143L) + 1.0) * 0.5;
        double selector = clamp((vegetationDiversity * 0.75) + (jitter * 0.25), 0.0, 0.999999);
        int raw = (int) Math.floor(selector * paletteSize);
        return (byte) Math.max(0, Math.min(raw, paletteSize - 1));
    }

    private static TreePrototype[] defaultPalette() {
        return new TreePrototype[]{
                new TreePrototype("oak_lowland", 6, 10, 3, 5),
                new TreePrototype("birch_temperate", 7, 11, 2, 4),
                new TreePrototype("pine_cool", 9, 15, 2, 3),
                new TreePrototype("acacia_dry", 5, 8, 4, 6)
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

    public record TreePrototype(String id, int minHeight, int maxHeight, int minCanopyRadius, int maxCanopyRadius) {
    }

    public record TreeRegionData(int size, float[] treeDensity, byte[] prototypeIndex, TreePrototype[] palette) {
    }
}