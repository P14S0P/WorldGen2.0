package com.piasop.worldgen2.modules.phase1;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;
import net.minecraft.resources.ResourceLocation;

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
    private static final ResourceLocation[][] CLIMATE_BIOME_IDS = buildBiomeIds();

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
        return false;
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
        return CLIMATE_BIOME_TABLE[temperatureIndex(temperatureC)][precipitationIndex(precipitationMm)];
    }

    public String chooseBiomeBlended(float temperatureC, float precipitationMm, float blendNoise) {
        int packedIndex = chooseBiomePackedIndexBlended(temperatureC, precipitationMm, blendNoise);
        return CLIMATE_BIOME_TABLE[rowFromPackedIndex(packedIndex)][columnFromPackedIndex(packedIndex)];
    }

    public int chooseBiomePackedIndexBlended(float temperatureC, float precipitationMm, float blendNoise) {
        int t = temperatureIndex(temperatureC);
        int p = precipitationIndex(precipitationMm);

        if (Math.abs(blendNoise) < 0.35f) {
            return packedIndex(t, p);
        }

        int tNeighbor = clampIndex(t + (blendNoise > 0 ? 1 : -1));
        int pNeighbor = clampIndex(p + (blendNoise > 0 ? 1 : -1));

        if (Math.abs(blendNoise) > 0.72f) {
            return packedIndex(tNeighbor, pNeighbor);
        }

        return Math.abs(temperatureC) > precipitationMm / 20.0f
                ? packedIndex(tNeighbor, p)
                : packedIndex(t, pNeighbor);
    }

    public ResourceLocation biomeIdFromPackedIndex(int packedIndex) {
        return CLIMATE_BIOME_IDS[rowFromPackedIndex(packedIndex)][columnFromPackedIndex(packedIndex)];
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

    private static int packedIndex(int row, int column) {
        return (row << 2) | column;
    }

    private static int rowFromPackedIndex(int packedIndex) {
        return (packedIndex >> 2) & 3;
    }

    private static int columnFromPackedIndex(int packedIndex) {
        return packedIndex & 3;
    }

    private static ResourceLocation[][] buildBiomeIds() {
        ResourceLocation[][] ids = new ResourceLocation[4][4];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                ids[row][column] = ResourceLocation.parse(CLIMATE_BIOME_TABLE[row][column]);
            }
        }
        return ids;
    }
}
