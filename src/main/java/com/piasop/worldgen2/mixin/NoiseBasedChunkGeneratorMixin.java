package com.piasop.worldgen2.mixin;

import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2Mod;
import com.piasop.worldgen2.core.WG2Registry;
import com.piasop.worldgen2.modules.phase1.BiomeModule;
import com.piasop.worldgen2.modules.phase1.ClimateModule;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    @Final
    protected BiomeSource biomeSource;

    @Shadow
    private NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState randomState) {
        throw new IllegalStateException("Mixin shadow");
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
        WG2Mod.dispatchRegionLoad(GenerationPhase.MACRO, new RegionGenContext(Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32), 0x5747324CL));
        WG2Mod.dispatchRegionLoad(GenerationPhase.REGIONAL, new RegionGenContext(Math.floorDiv(chunkX, 8), Math.floorDiv(chunkZ, 8), 0x5747324CL));

        NoiseChunk noisechunk = chunk.getOrCreateNoiseChunk((access) -> this.createNoiseChunk(access, structureManager, blender, randomState));
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
        BiomeResolver wg2Resolver = (quartX, quartY, quartZ, sampler) -> {
            int worldX = QuartPos.toBlock(quartX);
            int worldZ = QuartPos.toBlock(quartZ);
            float temp = climateModule.sampleTemperature(worldX, worldZ, 0x5747324CL);
            float precip = climateModule.samplePrecipitation(worldX, worldZ, 0x5747324CL);
            float blendNoise = (float) (Math.sin(worldX * 0.011) * Math.cos(worldZ * 0.011));

            String biomeId = biomeModule.chooseBiomeBlended(temp, precip, blendNoise);
            ResourceLocation id = ResourceLocation.tryParse(biomeId);
            if (id != null) {
                Biome biome = ForgeRegistries.BIOMES.getValue(id);
                if (biome != null) {
                    return Holder.direct(biome);
                }
            }

            return vanillaResolver.getNoiseBiome(quartX, quartY, quartZ, sampler);
        };

        chunk.fillBiomesFromNoise(wg2Resolver, randomState.sampler());
        ci.cancel();
    }
}
