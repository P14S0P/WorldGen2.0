package com.piasop.worldgen2.mixin;

import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2Mod;
import com.piasop.worldgen2.core.WG2Registry;
import com.piasop.worldgen2.modules.phase1.BiomeModule;
import com.piasop.worldgen2.modules.phase1.ClimateModule;
import com.piasop.worldgen2.modules.phase2.CaveModule;
import com.piasop.worldgen2.modules.phase2.OceanModule;
import com.piasop.worldgen2.modules.phase2.RiverModule;
import com.piasop.worldgen2.modules.phase3.MineralModule;
import com.piasop.worldgen2.modules.phase3.RuinsModule;
import com.piasop.worldgen2.modules.phase3.StructureModule;
import com.piasop.worldgen2.modules.phase3.TreeModule;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Optional;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    @Final
    protected BiomeSource biomeSource;

    @Inject(method = "doFill", at = @At("RETURN"))
    private void wg2$injectTerrainCarvers(
            Blender blender,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk,
            int minCellY,
            int cellHeight,
            CallbackInfoReturnable<ChunkAccess> cir) {
        if (!WG2Config.enabled || WG2Config.vanillaCompat) {
            return;
        }

        long worldSeed = ((StructureManagerAccessor) structureManager).wg2$getWorldOptions().seed();
        ChunkAccess outChunk = cir.getReturnValue();
        Optional<OceanModule> oceanModule = WG2Registry.get("wg2:ocean")
            .filter(OceanModule.class::isInstance)
            .map(OceanModule.class::cast);
        Optional<CaveModule> caveModule = WG2Registry.get("wg2:caves")
            .filter(CaveModule.class::isInstance)
            .map(CaveModule.class::cast);
        Optional<RiverModule> riverModule = WG2Registry.get("wg2:rivers")
                .filter(RiverModule.class::isInstance)
                .map(RiverModule.class::cast);
        Optional<MineralModule> mineralModule = WG2Registry.get("wg2:minerals")
            .filter(MineralModule.class::isInstance)
            .map(MineralModule.class::cast);
        Optional<TreeModule> treeModule = WG2Registry.get("wg2:trees")
            .filter(TreeModule.class::isInstance)
            .map(TreeModule.class::cast);
        Optional<StructureModule> structureModule = WG2Registry.get("wg2:structures")
            .filter(StructureModule.class::isInstance)
            .map(StructureModule.class::cast);
        Optional<RuinsModule> ruinsModule = WG2Registry.get("wg2:ruins")
            .filter(RuinsModule.class::isInstance)
            .map(RuinsModule.class::cast);
        oceanModule.ifPresent(module -> module.applyOceanToChunk(outChunk, worldSeed));
        mineralModule.ifPresent(module -> module.applyMineralStrataToChunk(outChunk, worldSeed));
        caveModule.ifPresent(module -> module.carveChunkCaves(outChunk, worldSeed));
        riverModule.ifPresent(module -> module.carveChunkRivers(outChunk, worldSeed));
        treeModule.ifPresent(module -> module.applyTreesToChunk(outChunk, worldSeed));
        structureModule.ifPresent(module -> module.applyStructureAnchorsToChunk(outChunk, worldSeed));
        ruinsModule.ifPresent(module -> module.applyRuinDegradationToChunk(outChunk, worldSeed));
    }

    @Inject(method = "doCreateBiomes", at = @At("HEAD"), cancellable = true)
    private void wg2$injectBiomeSelection(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfo ci) {
        if (!WG2Config.enabled || WG2Config.vanillaCompat) {
            return;
        }

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        long worldSeed = ((StructureManagerAccessor) structureManager).wg2$getWorldOptions().seed();
        WG2Mod.dispatchRegionLoad(GenerationPhase.MACRO, new RegionGenContext(Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32), worldSeed));
        WG2Mod.dispatchRegionLoad(GenerationPhase.REGIONAL, new RegionGenContext(Math.floorDiv(chunkX, 8), Math.floorDiv(chunkZ, 8), worldSeed));

        BiomeResolver vanillaResolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.biomeSource), chunk);

        Optional<ClimateModule> climateOpt = WG2Registry.get("wg2:climate").filter(ClimateModule.class::isInstance).map(ClimateModule.class::cast);
        Optional<BiomeModule> biomeOpt = WG2Registry.get("wg2:biome").filter(BiomeModule.class::isInstance).map(BiomeModule.class::cast);
        if (climateOpt.isEmpty() || biomeOpt.isEmpty()) {
            chunk.fillBiomesFromNoise(vanillaResolver, randomState.sampler());
            ci.cancel();
            return;
        }

        ClimateModule climateModule = climateOpt.get();
        BiomeModule biomeModule = biomeOpt.get();
        Holder<Biome>[] biomeLookup = new Holder[16];
        for (int packedIndex = 0; packedIndex < biomeLookup.length; packedIndex++) {
            ResourceLocation id = biomeModule.biomeIdFromPackedIndex(packedIndex);
            Biome biome = ForgeRegistries.BIOMES.getValue(id);
            if (biome != null) {
                biomeLookup[packedIndex] = Holder.direct(biome);
            }
        }
        HashMap<Long, Holder<Biome>> columnBiomeCache = new HashMap<>(64);

        BiomeResolver wg2Resolver = (quartX, quartY, quartZ, sampler) -> {
            long columnKey = (((long) quartX) << 32) ^ (quartZ & 0xffffffffL);
            Holder<Biome> cached = columnBiomeCache.get(columnKey);
            if (cached != null) {
                return cached;
            }

            int worldX = QuartPos.toBlock(quartX);
            int worldZ = QuartPos.toBlock(quartZ);
            float temp = climateModule.sampleTemperature(worldX, worldZ, worldSeed);
            float precip = climateModule.samplePrecipitation(worldX, worldZ, worldSeed);
            float blendNoise = (float) (Math.sin(worldX * 0.011) * Math.cos(worldZ * 0.011));

            int packedIndex = biomeModule.chooseBiomePackedIndexBlended(temp, precip, blendNoise);
            Holder<Biome> biomeHolder = biomeLookup[packedIndex];
            if (biomeHolder != null) {
                columnBiomeCache.put(columnKey, biomeHolder);
                return biomeHolder;
            }

            return vanillaResolver.getNoiseBiome(quartX, quartY, quartZ, sampler);
        };

        chunk.fillBiomesFromNoise(wg2Resolver, randomState.sampler());
        ci.cancel();
    }
}
