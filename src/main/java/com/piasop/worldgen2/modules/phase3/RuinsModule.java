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
 * Phase 3 ruins baseline: deterministic ruin opportunity + degradation profile.
 */
public final class RuinsModule implements WG2Module {
    private static final int REGION_SIZE = 32;

    private final StructureModule structures = new StructureModule();
    private final ConcurrentHashMap<Long, RuinsRegionData> regions = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:ruins";
    }

    @Override
    public int getPriority() {
        return 58;
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
                k -> generateRegionRuins(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()),
                k -> generateRegionRuins(ctx));
    }

    public RuinsRegionData generateRegionRuins(RegionGenContext ctx) {
        int size = REGION_SIZE;
        float[] ruinChance = new float[size * size];
        float[] degradation = new float[size * size];
        byte[] archetypeIndex = new byte[size * size];
        RuinArchetype[] palette = defaultPalette();

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;
        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float structureChance = structures.sampleStructureChance(worldX, worldZ, ctx.worldSeed());
                double ageNoise = (Phase1Noise.fbmOpenSimplex2S2D(worldX * 0.0019, worldZ * 0.0019,
                        ctx.worldSeed() + 22411L, 3, 2.0, 0.5) + 1.0) * 0.5;
                double fractureNoise = (Phase1Noise.value2D(worldX * 0.010, worldZ * 0.010,
                        ctx.worldSeed() + 23773L) + 1.0) * 0.5;

                ruinChance[idx] = (float) clamp((structureChance * 0.55) + (ageNoise * 0.30) + (fractureNoise * 0.15), 0.0, 1.0);
                degradation[idx] = (float) clamp((ageNoise * 0.60) + (fractureNoise * 0.40), 0.0, 1.0);
                archetypeIndex[idx] = pickArchetypeIndex(ruinChance[idx], degradation[idx], worldX, worldZ, ctx.worldSeed(), palette.length);
            }
        }
        return new RuinsRegionData(size, ruinChance, degradation, archetypeIndex, palette);
    }

    public float sampleRuinChance(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        RuinsRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionRuins(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.ruinChance()[index(localChunkX, localChunkZ, data.size())];
    }

    public float sampleDegradation(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        RuinsRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionRuins(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.degradation()[index(localChunkX, localChunkZ, data.size())];
    }

    public RuinArchetype sampleArchetype(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        RuinsRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionRuins(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());
        int paletteIdx = Byte.toUnsignedInt(data.archetypeIndex()[idx]);
        return data.palette()[Math.min(paletteIdx, data.palette().length - 1)];
    }

    private static byte pickArchetypeIndex(float ruinChance, float degradation, int worldX, int worldZ, long seed, int paletteSize) {
        double jitter = (Phase1Noise.value2D(worldX * 0.014, worldZ * 0.014, seed + 25117L) + 1.0) * 0.5;
        double selector = clamp((ruinChance * 0.60) + (degradation * 0.25) + (jitter * 0.15), 0.0, 0.999999);
        int raw = (int) Math.floor(selector * paletteSize);
        return (byte) Math.max(0, Math.min(raw, paletteSize - 1));
    }

    private static RuinArchetype[] defaultPalette() {
        return new RuinArchetype[]{
                new RuinArchetype("collapsed_hut", 0.20f, 0.55f, 0.45f),
                new RuinArchetype("cracked_tower", 0.35f, 0.80f, 0.70f),
                new RuinArchetype("sunken_foundation", 0.30f, 0.95f, 0.85f),
                new RuinArchetype("scattered_wall_ring", 0.10f, 0.65f, 0.60f)
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

    public record RuinArchetype(String id, float minDegradation, float maxDegradation, float collapseBias) {
    }

    public record RuinsRegionData(int size, float[] ruinChance, float[] degradation, byte[] archetypeIndex,
                                  RuinArchetype[] palette) {
    }
}