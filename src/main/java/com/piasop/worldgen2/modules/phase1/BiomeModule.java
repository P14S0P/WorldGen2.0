package com.piasop.worldgen2.modules.phase1;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;

/**
 * Phase 1 climate-to-biome lookup prototype.
 */
public final class BiomeModule implements WG2Module {
    private static final String[][] CLIMATE_BIOME_TABLE = {
            {"minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:taiga", "minecraft:old_growth_spruce_taiga"},
            {"minecraft:plains", "minecraft:forest", "minecraft:flower_forest", "minecraft:dark_forest"},
            {"minecraft:savanna", "minecraft:savanna", "minecraft:jungle", "minecraft:bamboo_jungle"},
            {"minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands"}
    };

    @Override
    public String getId() {
        return "wg2:biome";
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public GenerationPhase getPhase() {
        return GenerationPhase.REGIONAL;
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
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
    }

    public String chooseBiome(float temperatureC, float precipitationMm) {
        int tempIndex = temperatureIndex(temperatureC);
        int precipIndex = precipitationIndex(precipitationMm);
        return CLIMATE_BIOME_TABLE[tempIndex][precipIndex];
    }

    public String chooseBiomeBlended(float temperatureC, float precipitationMm, float blendNoise) {
        int t = temperatureIndex(temperatureC);
        int p = precipitationIndex(precipitationMm);

        if (Math.abs(blendNoise) < 0.35f) {
            return CLIMATE_BIOME_TABLE[t][p];
        }

        int tNeighbor = clampIndex(t + (blendNoise > 0 ? 1 : -1));
        int pNeighbor = clampIndex(p + (blendNoise > 0 ? 1 : -1));

        if (Math.abs(blendNoise) > 0.72f) {
            return CLIMATE_BIOME_TABLE[tNeighbor][pNeighbor];
        }

        // Mid blend ranges cross only one axis for smoother transitions.
        return Math.abs(temperatureC) > precipitationMm / 20.0f
                ? CLIMATE_BIOME_TABLE[tNeighbor][p]
                : CLIMATE_BIOME_TABLE[t][pNeighbor];
    }

    private static int temperatureIndex(float temperatureC) {
        return temperatureC < -2.0f ? 0 : temperatureC < 12.0f ? 1 : temperatureC < 25.0f ? 2 : 3;
    }

    private static int precipitationIndex(float precipitationMm) {
        return precipitationMm < 250.0f ? 0 : precipitationMm < 450.0f ? 1 : precipitationMm < 700.0f ? 2 : 3;
    }

    private static int clampIndex(int idx) {
        return Math.max(0, Math.min(3, idx));
    }
}
