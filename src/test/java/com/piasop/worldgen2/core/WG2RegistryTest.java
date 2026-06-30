package com.piasop.worldgen2.core;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.test.TestModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WG2RegistryTest {
    @AfterEach
    void tearDown() {
        WG2Registry.resetForTesting();
        WG2EventBus.clear();
    }

    @Test
    void registersAndRetrievesModulesById() {
        TestModule module = new TestModule("wg2:test", GenerationPhase.MACRO, 10);
        WG2Registry.register(module);

        assertTrue(WG2Registry.get("wg2:test").isPresent());
        assertEquals(module, WG2Registry.get("wg2:test").orElseThrow());
    }

    @Test
    void sortsModulesByPhaseAndPriority() {
        WG2Registry.register(new TestModule("wg2:low", GenerationPhase.NOISE, 20));
        WG2Registry.register(new TestModule("wg2:high", GenerationPhase.NOISE, 5));
        WG2Registry.register(new TestModule("wg2:macro", GenerationPhase.MACRO, 1));

        List<String> noiseIds = WG2Registry.byPhase(GenerationPhase.NOISE).stream()
                .map(WG2Module::getId)
                .toList();
        assertEquals(List.of("wg2:high", "wg2:low"), noiseIds);
        assertEquals(1, WG2Registry.byPhase(GenerationPhase.MACRO).size());
    }

    @Test
    void initializesModulesAndFiresLifecycleEvents() {
        TestModule module = new TestModule("wg2:core", GenerationPhase.MACRO, 0);
        WG2Registry.register(module);

        AtomicReference<com.piasop.worldgen2.api.events.ModulesInitializedEvent> initEvent = new AtomicReference<>();
        AtomicReference<com.piasop.worldgen2.api.events.WG2LifecycleEvent> readyEvent = new AtomicReference<>();
        WG2EventBus.on(com.piasop.worldgen2.api.events.ModulesInitializedEvent.class, initEvent::set);
        WG2EventBus.on(com.piasop.worldgen2.api.events.WG2LifecycleEvent.class, event -> {
            if (event.stage() == com.piasop.worldgen2.api.events.WG2LifecycleEvent.Stage.READY) {
                readyEvent.set(event);
            }
        });

        WG2Registry.initializeAll(WG2Config.INSTANCE, WG2DataCache.INSTANCE);

        assertTrue(module.isInitialized());
        assertEquals(1, initEvent.get().modules().size());
        assertEquals(com.piasop.worldgen2.api.events.WG2LifecycleEvent.Stage.READY, readyEvent.get().stage());
    }

    @Test
    void respectsDependencyOrderDuringInitialization() {
        List<String> initOrder = new ArrayList<>();
        BaseDependencyModule base = new BaseDependencyModule(initOrder);
        DependentDependencyModule dependent = new DependentDependencyModule(initOrder);

        WG2Registry.register(dependent);
        WG2Registry.register(base);
        WG2Registry.initializeAll(WG2Config.INSTANCE, WG2DataCache.INSTANCE);

        assertEquals(List.of("wg2:base", "wg2:dependent"), initOrder);
    }

    @Test
    void rejectsRegistrationAfterInitialization() {
        WG2Registry.register(new TestModule("wg2:first", GenerationPhase.MACRO, 0));
        WG2Registry.initializeAll(WG2Config.INSTANCE, WG2DataCache.INSTANCE);

        assertThrows(IllegalStateException.class, () ->
                WG2Registry.register(new TestModule("wg2:late", GenerationPhase.MACRO, 1)));
    }

    @Test
    void initializeAllIsIdempotent() {
        TestModule module = new TestModule("wg2:once", GenerationPhase.MACRO, 0);
        WG2Registry.register(module);

        WG2Registry.initializeAll(WG2Config.INSTANCE, WG2DataCache.INSTANCE);
        WG2Registry.initializeAll(WG2Config.INSTANCE, WG2DataCache.INSTANCE);

        assertTrue(module.isInitialized());
        assertFalse(WG2Registry.all().isEmpty());
    }

    private static final class BaseDependencyModule extends RecordingModule {
        BaseDependencyModule(List<String> initOrder) {
            super("wg2:base", GenerationPhase.MACRO, 50, initOrder);
        }
    }

    private static final class DependentDependencyModule extends RecordingModule {
        DependentDependencyModule(List<String> initOrder) {
            super("wg2:dependent", GenerationPhase.MACRO, 10, initOrder);
        }

        @Override
        public List<Class<? extends WG2Module>> getDependencies() {
            return List.of(BaseDependencyModule.class);
        }
    }

    private abstract static class RecordingModule implements WG2Module {
        private final String id;
        private final GenerationPhase phase;
        private final int priority;
        private final List<String> initOrder;

        RecordingModule(String id, GenerationPhase phase, int priority, List<String> initOrder) {
            this.id = id;
            this.phase = phase;
            this.priority = priority;
            this.initOrder = initOrder;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public int getPriority() {
            return priority;
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
            initOrder.add(id);
        }

        @Override
        public void onChunkGenerate(ChunkGenContext ctx) {
        }

        @Override
        public void onRegionLoad(RegionGenContext ctx) {
        }
    }
}
