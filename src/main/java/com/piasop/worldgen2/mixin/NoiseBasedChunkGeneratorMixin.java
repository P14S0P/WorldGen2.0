package com.piasop.worldgen2.mixin;

import com.mojang.logging.LogUtils;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Unique
    private static final Logger WG2_LOGGER = LogUtils.getLogger();
    @Unique
    private static final int WG2_PROFILE_WINDOW_CHUNKS = 32;
    @Unique
    private static final AtomicInteger wg2$profileChunkCounter = new AtomicInteger();
    @Unique
    private static final AtomicBoolean wg2$profileFlushLock = new AtomicBoolean(false);
    @Unique
    private static final AtomicBoolean wg2$profileHookLogged = new AtomicBoolean(false);
    @Unique
    private static final LongAdder wg2$oceanNs = new LongAdder();
    @Unique
    private static final LongAdder wg2$mineralNs = new LongAdder();
    @Unique
    private static final LongAdder wg2$caveNs = new LongAdder();
    @Unique
    private static final LongAdder wg2$riverNs = new LongAdder();
    @Unique
    private static final LongAdder wg2$treeNs = new LongAdder();
    @Unique
    private static final LongAdder wg2$structureNs = new LongAdder();
    @Unique
    private static final LongAdder wg2$ruinsNs = new LongAdder();
    @Unique
    private static final LongAdder wg2$oceanDominantChunks = new LongAdder();
    @Unique
    private static final LongAdder wg2$mineralDominantChunks = new LongAdder();
    @Unique
    private static final LongAdder wg2$caveDominantChunks = new LongAdder();
    @Unique
    private static final LongAdder wg2$riverDominantChunks = new LongAdder();
    @Unique
    private static final LongAdder wg2$treeDominantChunks = new LongAdder();
    @Unique
    private static final LongAdder wg2$structureDominantChunks = new LongAdder();
    @Unique
    private static final LongAdder wg2$ruinsDominantChunks = new LongAdder();

    @Unique
    private static volatile BiomeModule wg2$cachedBiomeModule;
    @Unique
    private static volatile Holder<Biome>[] wg2$cachedBiomeLookup;

    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true)
    private void wg2$injectTerrainCarvers(
            Executor executor,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!WG2Config.enabled || WG2Config.vanillaCompat) {
            return;
        }

        long worldSeed = ((StructureManagerAccessor) structureManager).wg2$getWorldOptions().seed();
        CompletableFuture<ChunkAccess> original = cir.getReturnValue();
        cir.setReturnValue(original.thenApply(outChunk -> {
            OceanModule oceanModule = wg2$getModule("wg2:ocean", OceanModule.class);
            CaveModule caveModule = wg2$getModule("wg2:caves", CaveModule.class);
            RiverModule riverModule = wg2$getModule("wg2:rivers", RiverModule.class);
            MineralModule mineralModule = wg2$getModule("wg2:minerals", MineralModule.class);
            TreeModule treeModule = wg2$getModule("wg2:trees", TreeModule.class);
            StructureModule structureModule = wg2$getModule("wg2:structures", StructureModule.class);
            RuinsModule ruinsModule = wg2$getModule("wg2:ruins", RuinsModule.class);

            if (wg2$profileHookLogged.compareAndSet(false, true)) {
                WG2_LOGGER.warn(
                        "WG2 profiler hook active in fillFromNoise.thenApply | modules present: ocean={}, mineral={}, cave={}, river={}, tree={}, structure={}, ruins={} | window={} chunk(s)",
                        oceanModule != null,
                        mineralModule != null,
                        caveModule != null,
                        riverModule != null,
                        treeModule != null,
                        structureModule != null,
                        ruinsModule != null,
                        WG2_PROFILE_WINDOW_CHUNKS
                );
            }
            long oceanNs = 0L;
            long mineralNs = 0L;
            long caveNs = 0L;
            long riverNs = 0L;
            long treeNs = 0L;
            long structureNs = 0L;
            long ruinsNs = 0L;
            if (mineralModule != null) {
                long t0 = System.nanoTime();
                mineralModule.applyMineralStrataToChunk(outChunk, worldSeed);
                mineralNs = System.nanoTime() - t0;
                wg2$mineralNs.add(mineralNs);
            }
            if (caveModule != null) {
                long t0 = System.nanoTime();
                caveModule.carveChunkCaves(outChunk, worldSeed);
                caveNs = System.nanoTime() - t0;
                wg2$caveNs.add(caveNs);
            }
            if (riverModule != null) {
                long t0 = System.nanoTime();
                riverModule.carveChunkRivers(outChunk, worldSeed);
                riverNs = System.nanoTime() - t0;
                wg2$riverNs.add(riverNs);
            }
            if (oceanModule != null) {
                long t0 = System.nanoTime();
                oceanModule.applyOceanToChunk(outChunk, worldSeed);
                oceanNs = System.nanoTime() - t0;
                wg2$oceanNs.add(oceanNs);
            }
            if (treeModule != null) {
                long t0 = System.nanoTime();
                treeModule.applyTreesToChunk(outChunk, worldSeed);
                treeNs = System.nanoTime() - t0;
                wg2$treeNs.add(treeNs);
            }
            if (structureModule != null) {
                long t0 = System.nanoTime();
                structureModule.applyStructureAnchorsToChunk(outChunk, worldSeed);
                structureNs = System.nanoTime() - t0;
                wg2$structureNs.add(structureNs);
            }
            if (ruinsModule != null) {
                long t0 = System.nanoTime();
                ruinsModule.applyRuinDegradationToChunk(outChunk, worldSeed);
                ruinsNs = System.nanoTime() - t0;
                wg2$ruinsNs.add(ruinsNs);
            }

            wg2$recordDominantChunk(oceanNs, mineralNs, caveNs, riverNs, treeNs, structureNs, ruinsNs);
            wg2$maybeFlushProfileWindow();
            return outChunk;
        }));
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

        BiomeSource baseBiomeSource = ((ChunkGenerator) (Object) this).getBiomeSource();
        BiomeResolver vanillaResolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(baseBiomeSource), chunk);

        ClimateModule climateModule = wg2$getModule("wg2:climate", ClimateModule.class);
        BiomeModule biomeModule = wg2$getModule("wg2:biome", BiomeModule.class);
        if (climateModule == null || biomeModule == null) {
            chunk.fillBiomesFromNoise(vanillaResolver, randomState.sampler());
            ci.cancel();
            return;
        }

        Holder<Biome>[] biomeLookup = wg2$getOrBuildBiomeLookup(biomeModule);
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

    @Unique
    @SuppressWarnings("unchecked")
    private static Holder<Biome>[] wg2$getOrBuildBiomeLookup(BiomeModule biomeModule) {
        Holder<Biome>[] cachedLookup = wg2$cachedBiomeLookup;
        if (wg2$cachedBiomeModule == biomeModule && cachedLookup != null) {
            return cachedLookup;
        }

        Holder<Biome>[] rebuilt = new Holder[16];
        for (int packedIndex = 0; packedIndex < rebuilt.length; packedIndex++) {
            ResourceLocation id = biomeModule.biomeIdFromPackedIndex(packedIndex);
            Biome biome = ForgeRegistries.BIOMES.getValue(id);
            if (biome != null) {
                rebuilt[packedIndex] = Holder.direct(biome);
            }
        }
        wg2$cachedBiomeModule = biomeModule;
        wg2$cachedBiomeLookup = rebuilt;
        return rebuilt;
    }

    @Unique
    private static <T> T wg2$getModule(String id, Class<T> type) {
        return WG2Registry.get(id)
                .filter(type::isInstance)
                .map(type::cast)
                .orElse(null);
    }

    @Unique
    private static void wg2$maybeFlushProfileWindow() {
        int chunks = wg2$profileChunkCounter.incrementAndGet();
        if (chunks % WG2_PROFILE_WINDOW_CHUNKS != 0) {
            return;
        }
        if (!wg2$profileFlushLock.compareAndSet(false, true)) {
            return;
        }

        try {
            long oceanNs = wg2$oceanNs.sumThenReset();
            long mineralNs = wg2$mineralNs.sumThenReset();
            long caveNs = wg2$caveNs.sumThenReset();
            long riverNs = wg2$riverNs.sumThenReset();
            long treeNs = wg2$treeNs.sumThenReset();
            long structureNs = wg2$structureNs.sumThenReset();
            long ruinsNs = wg2$ruinsNs.sumThenReset();
            long oceanDominantChunks = wg2$oceanDominantChunks.sumThenReset();
            long mineralDominantChunks = wg2$mineralDominantChunks.sumThenReset();
            long caveDominantChunks = wg2$caveDominantChunks.sumThenReset();
            long riverDominantChunks = wg2$riverDominantChunks.sumThenReset();
            long treeDominantChunks = wg2$treeDominantChunks.sumThenReset();
            long structureDominantChunks = wg2$structureDominantChunks.sumThenReset();
            long ruinsDominantChunks = wg2$ruinsDominantChunks.sumThenReset();

            long totalNs = oceanNs + mineralNs + caveNs + riverNs + treeNs + structureNs + ruinsNs;
            if (totalNs <= 0L) {
                WG2_LOGGER.warn("WG2 profile window completed but totalNs=0 (no module timings captured)");
                return;
            }

            String dominant = "ocean";
            long dominantNs = oceanNs;
            if (mineralNs > dominantNs) {
                dominant = "mineral";
                dominantNs = mineralNs;
            }
            if (caveNs > dominantNs) {
                dominant = "cave";
                dominantNs = caveNs;
            }
            if (riverNs > dominantNs) {
                dominant = "river";
                dominantNs = riverNs;
            }
            if (treeNs > dominantNs) {
                dominant = "tree";
                dominantNs = treeNs;
            }
            if (structureNs > dominantNs) {
                dominant = "structure";
                dominantNs = structureNs;
            }
            if (ruinsNs > dominantNs) {
                dominant = "ruins";
                dominantNs = ruinsNs;
            }

            double invWindow = 1.0 / WG2_PROFILE_WINDOW_CHUNKS;
            WG2_LOGGER.info(
                    "WG2 profile {} chunks | avg ms/chunk: ocean={}; mineral={}; cave={}; river={}; tree={}; structure={}; ruins={} | total={} | dominant-by-time={} ({}%) | dominant-chunks: ocean={}; mineral={}; cave={}; river={}; tree={}; structure={}; ruins={}",
                    WG2_PROFILE_WINDOW_CHUNKS,
                    wg2$fmtMs(oceanNs * invWindow),
                    wg2$fmtMs(mineralNs * invWindow),
                    wg2$fmtMs(caveNs * invWindow),
                    wg2$fmtMs(riverNs * invWindow),
                    wg2$fmtMs(treeNs * invWindow),
                    wg2$fmtMs(structureNs * invWindow),
                    wg2$fmtMs(ruinsNs * invWindow),
                    wg2$fmtMs(totalNs * invWindow),
                    dominant,
                    wg2$fmtPct((dominantNs * 100.0) / totalNs),
                    oceanDominantChunks,
                    mineralDominantChunks,
                    caveDominantChunks,
                    riverDominantChunks,
                    treeDominantChunks,
                    structureDominantChunks,
                    ruinsDominantChunks
            );
        } finally {
            wg2$profileFlushLock.set(false);
        }
    }

    @Unique
    private static void wg2$recordDominantChunk(
            long oceanNs,
            long mineralNs,
            long caveNs,
            long riverNs,
            long treeNs,
            long structureNs,
            long ruinsNs) {
        String dominant = "ocean";
        long dominantNs = oceanNs;
        if (mineralNs > dominantNs) {
            dominant = "mineral";
            dominantNs = mineralNs;
        }
        if (caveNs > dominantNs) {
            dominant = "cave";
            dominantNs = caveNs;
        }
        if (riverNs > dominantNs) {
            dominant = "river";
            dominantNs = riverNs;
        }
        if (treeNs > dominantNs) {
            dominant = "tree";
            dominantNs = treeNs;
        }
        if (structureNs > dominantNs) {
            dominant = "structure";
            dominantNs = structureNs;
        }
        if (ruinsNs > dominantNs) {
            dominant = "ruins";
        }

        switch (dominant) {
            case "ocean" -> wg2$oceanDominantChunks.increment();
            case "mineral" -> wg2$mineralDominantChunks.increment();
            case "cave" -> wg2$caveDominantChunks.increment();
            case "river" -> wg2$riverDominantChunks.increment();
            case "tree" -> wg2$treeDominantChunks.increment();
            case "structure" -> wg2$structureDominantChunks.increment();
            case "ruins" -> wg2$ruinsDominantChunks.increment();
            default -> {
            }
        }
    }

    @Unique
    private static String wg2$fmtMs(double ns) {
        return String.format("%.3f", ns / 1_000_000.0);
    }

    @Unique
    private static String wg2$fmtPct(double pct) {
        return String.format("%.1f", pct);
    }
}
