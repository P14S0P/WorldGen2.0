package com.piasop.worldgen2.core;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WG2ModDispatchTest {
    @AfterEach
    void tearDown() {
        WG2Registry.resetForTesting();
        WG2Config.enabled = true;
        WG2Config.vanillaCompat = false;
    }

    @Test
    void dispatchChunkGenerationRunsOnlyMatchingPhaseModules() {
        AtomicInteger noiseCalls = new AtomicInteger();
        AtomicInteger featuresCalls = new AtomicInteger();

        WG2Registry.register(new CountingModule("wg2:test-noise", GenerationPhase.NOISE, noiseCalls));
        WG2Registry.register(new CountingModule("wg2:test-features", GenerationPhase.FEATURES, featuresCalls));

        WG2Mod.dispatchChunkGeneration(GenerationPhase.NOISE, new ChunkGenContext(2, -3, 99L));
        WG2Mod.dispatchChunkGeneration(GenerationPhase.FEATURES, new ChunkGenContext(2, -3, 99L));

        assertEquals(1, noiseCalls.get());
        assertEquals(1, featuresCalls.get());
    }

    @Test
    void dispatchChunkGenerationHonorsGlobalConfigGates() {
        AtomicInteger calls = new AtomicInteger();
        WG2Registry.register(new CountingModule("wg2:test-gated", GenerationPhase.FEATURES, calls));

        WG2Config.enabled = false;
        WG2Mod.dispatchChunkGeneration(GenerationPhase.FEATURES, new ChunkGenContext(0, 0, 1L));
        WG2Config.enabled = true;
        WG2Config.vanillaCompat = true;
        WG2Mod.dispatchChunkGeneration(GenerationPhase.FEATURES, new ChunkGenContext(0, 0, 1L));

        assertEquals(0, calls.get());
    }

    private static final class CountingModule implements WG2Module {
        private final String id;
        private final GenerationPhase phase;
        private final AtomicInteger counter;

        CountingModule(String id, GenerationPhase phase, AtomicInteger counter) {
            this.id = id;
            this.phase = phase;
            this.counter = counter;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public GenerationPhase getPhase() {
            return phase;
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
            counter.incrementAndGet();
        }

        @Override
        public void onRegionLoad(RegionGenContext ctx) {
        }
    }
}