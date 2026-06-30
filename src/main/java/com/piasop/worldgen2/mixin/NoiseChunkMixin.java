package com.piasop.worldgen2.mixin;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.core.WG2Mod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runtime hook to execute WG2 NOISE phase during vanilla NoiseChunk creation.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {
	private static final ResourceLocation WG2_NOISE_SEED_PROBE = ResourceLocation.fromNamespaceAndPath("worldgen2", "noise_seed_probe");

	@Inject(method = "<init>", at = @At("TAIL"))
	private void wg2$onNoiseChunkConstructed(
			int cellCountXZ,
			RandomState randomState,
			int firstBlockX,
			int firstBlockZ,
			NoiseSettings noiseSettings,
			DensityFunctions.BeardifierOrMarker beardifier,
			NoiseGeneratorSettings generatorSettings,
			Aquifer.FluidPicker fluidPicker,
			Blender blender,
			CallbackInfo ci) {
		int chunkX = Math.floorDiv(firstBlockX, 16);
		int chunkZ = Math.floorDiv(firstBlockZ, 16);
		long worldSeed = randomState.getOrCreateRandomFactory(WG2_NOISE_SEED_PROBE).at(0, 0, 0).nextLong();
		WG2Mod.dispatchChunkGeneration(GenerationPhase.NOISE, new ChunkGenContext(chunkX, chunkZ, worldSeed));
	}
}
