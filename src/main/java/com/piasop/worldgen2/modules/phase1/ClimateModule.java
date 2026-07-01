package com.piasop.worldgen2.modules.phase1;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 climate prototype (temperature + precipitation + simplified Koppen).
 */
public final class ClimateModule implements WG2Module {
    private static final int REGION_SIZE = 32;
    private static final double WIND_X = 0.2873478855663454; // normalized from (0.3, 1.0)
    private static final double WIND_Z = 0.9578262852211513;

    private final ConcurrentHashMap<Long, RegionClimateGrid> climateByRegion = new ConcurrentHashMap<>();
    private WG2DataCache cache;

    @Override
    public String getId() {
        return "wg2:climate";
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public GenerationPhase getPhase() {
        return GenerationPhase.MACRO;
    }

    @Override
    public boolean canRunAsync() {
        return false;
    }

    @Override
    public void initialize(WG2Config config, WG2DataCache cache) {
        this.cache = cache;
    }

    @Override
    public void onChunkGenerate(ChunkGenContext ctx) {
        int regionX = Math.floorDiv(ctx.chunkX(), REGION_SIZE);
        int regionZ = Math.floorDiv(ctx.chunkZ(), REGION_SIZE);
        long key = regionKey(regionX, regionZ);
        climateByRegion.computeIfAbsent(key, unused -> generateRegionClimate(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        climateByRegion.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()), unused -> generateRegionClimate(ctx));
        cache.getRegionData(ctx.regionX(), ctx.regionZ(),
                () -> com.piasop.worldgen2.core.cache.RegionData.empty(ctx.regionX(), ctx.regionZ(), ctx.worldSeed()));
    }

    public RegionClimateGrid generateRegionClimate(RegionGenContext ctx) {
        float[] temperature = new float[REGION_SIZE * REGION_SIZE];
        float[] precipitation = new float[REGION_SIZE * REGION_SIZE];
        byte[] koppen = new byte[REGION_SIZE * REGION_SIZE];

        int baseChunkX = ctx.regionX() * REGION_SIZE;
        int baseChunkZ = ctx.regionZ() * REGION_SIZE;

        int idx = 0;
        for (int rz = 0; rz < REGION_SIZE; rz++) {
            for (int rx = 0; rx < REGION_SIZE; rx++) {
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float temp = sampleTemperatureRaw(worldX, worldZ, ctx.worldSeed());
                float precip = samplePrecipitationRaw(worldX, worldZ, ctx.worldSeed());

                temperature[idx] = temp;
                precipitation[idx] = precip;
                koppen[idx] = classifyKoppen(temp, precip).code;
                idx++;
            }
        }

        return new RegionClimateGrid(temperature, precipitation, koppen);
    }

    public float sampleTemperature(int worldX, int worldZ, long seed) {
        RegionClimateGrid region = resolveRegionForWorld(worldX, worldZ, seed);
        return region.temperature()[localChunkIndex(worldX, worldZ)];
    }

    private float sampleTemperatureRaw(int worldX, int worldZ, long seed) {
        double latitude = Math.abs(worldZ) / 100000.0;
        double tempBase = Math.cos(latitude * Math.PI) * 30.0 - 5.0;
        double localVariation = Phase1Noise.fbm2D(worldX * 0.0008, worldZ * 0.0008, seed + 7001L, 3, 2.0, 0.5) * 6.0;
        return (float) (tempBase + localVariation);
    }

    public float samplePrecipitation(int worldX, int worldZ, long seed) {
        RegionClimateGrid region = resolveRegionForWorld(worldX, worldZ, seed);
        return region.precipitation()[localChunkIndex(worldX, worldZ)];
    }

    private float samplePrecipitationRaw(int worldX, int worldZ, long seed) {
        double oceanNoise = (Phase1Noise.value2D(worldX * 0.0004, worldZ * 0.0004, seed + 8803L) + 1.0) * 0.5;
        double moistureNoise = (Phase1Noise.fbm2D(worldX * 0.0015, worldZ * 0.0015, seed + 9901L, 4, 2.0, 0.5) + 1.0) * 0.5;
        double basePrecip = 200.0 + (oceanNoise * 350.0) + (moistureNoise * 300.0);
        double orographic = sampleOrographicFactor(worldX, worldZ, seed);
        return (float) clamp(basePrecip * (1.0 + (orographic * 0.8)), 100.0, 1400.0);
    }

    private RegionClimateGrid resolveRegionForWorld(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        return climateByRegion.computeIfAbsent(regionKey(regionX, regionZ),
                unused -> generateRegionClimate(new RegionGenContext(regionX, regionZ, seed)));
    }

    private static int localChunkIndex(int worldX, int worldZ) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int localX = Math.floorMod(chunkX, REGION_SIZE);
        int localZ = Math.floorMod(chunkZ, REGION_SIZE);
        return (localZ * REGION_SIZE) + localX;
    }

    private double sampleOrographicFactor(int worldX, int worldZ, long seed) {
        int step = 48;
        double hL = sampleTerrainProxy(worldX - step, worldZ, seed);
        double hR = sampleTerrainProxy(worldX + step, worldZ, seed);
        double hD = sampleTerrainProxy(worldX, worldZ - step, seed);
        double hU = sampleTerrainProxy(worldX, worldZ + step, seed);

        double gradX = (hR - hL) / (2.0 * step);
        double gradZ = (hU - hD) / (2.0 * step);
        double alongWind = (gradX * WIND_X) + (gradZ * WIND_Z);
        return clamp(alongWind * 8.0, -1.0, 1.0);
    }

    private static double sampleTerrainProxy(int worldX, int worldZ, long seed) {
        double macro = Phase1Noise.fbm2D(worldX * 0.0018, worldZ * 0.0018, seed + 331L, 4, 2.0, 0.5);
        double ridged = Phase1Noise.ridgedFbm2D(worldX * 0.0025, worldZ * 0.0025, seed + 991L, 3, 2.0, 0.5);
        return 80.0 + (macro * 28.0) + ((ridged - 0.5) * 36.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public ClimateType classifyKoppen(float temperatureC, float precipitationMm) {
        if (temperatureC < -10.0f) {
            return precipitationMm > 400.0f ? ClimateType.ET : ClimateType.EF;
        }
        if (precipitationMm < 250.0f) {
            return temperatureC > 18.0f ? ClimateType.BWH : ClimateType.BSK;
        }
        if (temperatureC >= 18.0f) {
            return precipitationMm > 700.0f ? ClimateType.AF : ClimateType.AW;
        }
        if (temperatureC >= 5.0f) {
            return precipitationMm > 600.0f ? ClimateType.CFA : ClimateType.CSB;
        }
        return precipitationMm > 500.0f ? ClimateType.DFB : ClimateType.DWC;
    }

    private static long regionKey(int regionX, int regionZ) {
        return (((long) regionX) << 32) ^ (regionZ & 0xffffffffL);
    }

    public enum ClimateType {
        EF((byte) 1),
        ET((byte) 2),
        BWH((byte) 3),
        BSK((byte) 4),
        AW((byte) 5),
        AF((byte) 6),
        CSB((byte) 7),
        CFA((byte) 8),
        DWC((byte) 9),
        DFB((byte) 10);

        private final byte code;

        ClimateType(byte code) {
            this.code = code;
        }

        public byte code() {
            return code;
        }
    }

    public record RegionClimateGrid(float[] temperature, float[] precipitation, byte[] koppenCode) {
    }
}
