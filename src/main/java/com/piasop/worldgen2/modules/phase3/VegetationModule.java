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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3 vegetation base: climate + altitude driven vegetation potential.
 */
public final class VegetationModule implements WG2Module {
    private static final int REGION_SIZE = 32;

    private final ConcurrentHashMap<Long, VegetationRegionData> regions = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:vegetation";
    }

    @Override
    public int getPriority() {
        return 55;
    }

    @Override
    public GenerationPhase getPhase() {
        return GenerationPhase.MACRO;
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
                k -> generateRegionVegetation(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()),
                k -> generateRegionVegetation(ctx));
    }

    public VegetationRegionData generateRegionVegetation(RegionGenContext ctx) {
        int size = REGION_SIZE;
        float[] density = new float[size * size];
        float[] diversity = new float[size * size];

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;
        Optional<ClimateModule> climate = WG2Registry.get("wg2:climate")
                .filter(ClimateModule.class::isInstance)
                .map(ClimateModule.class::cast);
        Optional<TerrainModule> terrain = WG2Registry.get("wg2:terrain")
                .filter(TerrainModule.class::isInstance)
                .map(TerrainModule.class::cast);

        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float temp = climate.map(c -> c.sampleTemperature(worldX, worldZ, ctx.worldSeed()))
                        .orElse((float) fallbackTemperature(worldX, worldZ, ctx.worldSeed()));
                float precip = climate.map(c -> c.samplePrecipitation(worldX, worldZ, ctx.worldSeed()))
                        .orElse((float) fallbackPrecipitation(worldX, worldZ, ctx.worldSeed()));
                double elevation = terrain.map(t -> t.sampleHeight(worldX, worldZ, ctx.worldSeed()))
                        .orElse(fallbackElevation(worldX, worldZ, ctx.worldSeed()));

                density[idx] = (float) computeDensity(temp, precip, elevation);
                diversity[idx] = (float) computeDiversity(temp, precip, worldX, worldZ, ctx.worldSeed());
            }
        }

        return new VegetationRegionData(size, density, diversity);
    }

    public float sampleVegetationDensity(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        double tx = (Math.floorMod(worldX, 16) + 0.5) / 16.0;
        double tz = (Math.floorMod(worldZ, 16) + 0.5) / 16.0;

        double d00 = sampleDensityAtChunk(chunkX, chunkZ, seed);
        double d10 = sampleDensityAtChunk(chunkX + 1, chunkZ, seed);
        double d01 = sampleDensityAtChunk(chunkX, chunkZ + 1, seed);
        double d11 = sampleDensityAtChunk(chunkX + 1, chunkZ + 1, seed);

        double dx0 = lerp(d00, d10, tx);
        double dx1 = lerp(d01, d11, tx);
        return (float) clamp(lerp(dx0, dx1, tz), 0.0, 1.0);
    }

    public float sampleVegetationDiversity(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        VegetationRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionVegetation(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.diversity()[index(localChunkX, localChunkZ, data.size())];
    }

    private double sampleDensityAtChunk(int chunkX, int chunkZ, long seed) {
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        VegetationRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionVegetation(new RegionGenContext(regionX, regionZ, seed)));

        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.density()[index(localChunkX, localChunkZ, data.size())];
    }

    double computeDensity(float temperature, float precipitation, double elevation) {
        double tempSuitability = 1.0 - clamp(Math.abs(temperature - 19.0) / 42.0, 0.0, 1.0);
        double precipSuitability = clamp((precipitation - 180.0) / 900.0, 0.0, 1.0);
        double altitudePenalty = clamp((elevation - 118.0) / 165.0, 0.0, 1.0);
        double base = (tempSuitability * 0.35) + (precipSuitability * 0.65);
        return clamp(base * (1.0 - (altitudePenalty * 0.72)), 0.0, 1.0);
    }

    double computeDiversity(float temperature, float precipitation, int worldX, int worldZ, long seed) {
        double hydro = clamp((precipitation - 150.0) / 1000.0, 0.0, 1.0);
        double thermal = 1.0 - clamp(Math.abs(temperature - 16.0) / 48.0, 0.0, 1.0);
        double biomeNoise = (Phase1Noise.fbm2D(worldX * 0.0024, worldZ * 0.0024, seed + 5711L, 3, 2.0, 0.5) + 1.0) * 0.5;
        return clamp((hydro * 0.4) + (thermal * 0.3) + (biomeNoise * 0.3), 0.0, 1.0);
    }

    private static double fallbackTemperature(int worldX, int worldZ, long seed) {
        double latitude = Math.abs(worldZ) / 100000.0;
        double tempBase = Math.cos(latitude * Math.PI) * 30.0 - 5.0;
        double localVariation = Phase1Noise.fbm2D(worldX * 0.0008, worldZ * 0.0008, seed + 7001L, 3, 2.0, 0.5) * 6.0;
        return tempBase + localVariation;
    }

    private static double fallbackPrecipitation(int worldX, int worldZ, long seed) {
        double oceanNoise = (Phase1Noise.value2D(worldX * 0.0004, worldZ * 0.0004, seed + 8803L) + 1.0) * 0.5;
        double moistureNoise = (Phase1Noise.fbm2D(worldX * 0.0015, worldZ * 0.0015, seed + 9901L, 4, 2.0, 0.5) + 1.0) * 0.5;
        double basePrecip = 200.0 + (oceanNoise * 350.0) + (moistureNoise * 300.0);
        return clamp(basePrecip, 100.0, 1400.0);
    }

    private static double fallbackElevation(int worldX, int worldZ, long seed) {
        double macro = Phase1Noise.fbm2D(worldX * 0.0018, worldZ * 0.0018, seed + 331L, 4, 2.0, 0.5);
        double ridged = Phase1Noise.ridgedFbm2D(worldX * 0.0025, worldZ * 0.0025, seed + 991L, 3, 2.0, 0.5);
        return 80.0 + (macro * 28.0) + ((ridged - 0.5) * 36.0);
    }

    private static int index(int x, int z, int size) {
        return (z * size) + x;
    }

    private static long regionKey(int rx, int rz) {
        return (((long) rx) << 32) ^ (rz & 0xffffffffL);
    }

    private static double lerp(double a, double b, double t) {
        return a + ((b - a) * t);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record VegetationRegionData(int size, float[] density, float[] diversity) {
    }
}
