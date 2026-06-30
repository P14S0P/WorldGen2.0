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
 * Phase 3 mineral baseline: deterministic stratigraphy signals and ore richness potential.
 */
public final class MineralModule implements WG2Module {
    private static final int REGION_SIZE = 32;

    private final RuinsModule ruins = new RuinsModule();
    private final ConcurrentHashMap<Long, MineralRegionData> regions = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:minerals";
    }

    @Override
    public int getPriority() {
        return 59;
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
    }

    @Override
    public void onChunkGenerate(ChunkGenContext ctx) {
        int regionX = Math.floorDiv(ctx.chunkX(), REGION_SIZE);
        int regionZ = Math.floorDiv(ctx.chunkZ(), REGION_SIZE);
        regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()),
                k -> generateRegionMinerals(ctx));
    }

    public MineralRegionData generateRegionMinerals(RegionGenContext ctx) {
        int size = REGION_SIZE;
        float[] richness = new float[size * size];
        float[] deepBandBias = new float[size * size];
        byte[] layerProfile = new byte[size * size];
        MineralProfile[] palette = defaultProfiles();

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;
        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float ruinDegradation = ruins.sampleDegradation(worldX, worldZ, ctx.worldSeed());
                double baseNoise = (Phase1Noise.fbmOpenSimplex2S2D(worldX * 0.0028, worldZ * 0.0028,
                        ctx.worldSeed() + 26417L, 3, 2.0, 0.5) + 1.0) * 0.5;
                double fractureNoise = (Phase1Noise.value2D(worldX * 0.009, worldZ * 0.009,
                        ctx.worldSeed() + 27803L) + 1.0) * 0.5;

                richness[idx] = (float) clamp((baseNoise * 0.58) + (fractureNoise * 0.27) + (ruinDegradation * 0.15), 0.0, 1.0);
                deepBandBias[idx] = (float) clamp((fractureNoise * 0.62) + (baseNoise * 0.38), 0.0, 1.0);
                layerProfile[idx] = pickLayerProfile(richness[idx], deepBandBias[idx], worldX, worldZ, ctx.worldSeed(), palette.length);
            }
        }
        return new MineralRegionData(size, richness, deepBandBias, layerProfile, palette);
    }

    public float sampleRichness(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.richness()[index(localChunkX, localChunkZ, data.size())];
    }

    public float sampleDeepBandBias(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.deepBandBias()[index(localChunkX, localChunkZ, data.size())];
    }

    public MineralProfile sampleProfile(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());
        int paletteIdx = Byte.toUnsignedInt(data.layerProfile()[idx]);
        return data.palette()[Math.min(paletteIdx, data.palette().length - 1)];
    }

    private static byte pickLayerProfile(float richness, float deepBias, int worldX, int worldZ, long seed, int paletteSize) {
        double jitter = (Phase1Noise.value2D(worldX * 0.016, worldZ * 0.016, seed + 29191L) + 1.0) * 0.5;
        double selector = clamp((richness * 0.57) + (deepBias * 0.30) + (jitter * 0.13), 0.0, 0.999999);
        int raw = (int) Math.floor(selector * paletteSize);
        return (byte) Math.max(0, Math.min(raw, paletteSize - 1));
    }

    private static MineralProfile[] defaultProfiles() {
        return new MineralProfile[]{
                new MineralProfile("sedimentary_iron", -8, 64, -32, 32),
                new MineralProfile("metamorphic_copper", -24, 48, -48, 16),
                new MineralProfile("igneous_gold", -40, 24, -64, -8),
                new MineralProfile("deep_crystal_mix", -56, 8, -64, -24)
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

    public record MineralProfile(String id, int primaryMinY, int primaryMaxY, int secondaryMinY, int secondaryMaxY) {
    }

    public record MineralRegionData(int size, float[] richness, float[] deepBandBias, byte[] layerProfile,
                                    MineralProfile[] palette) {
    }
}