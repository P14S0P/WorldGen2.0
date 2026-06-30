package com.piasop.worldgen2.test;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TestModule implements WG2Module {
    private final String id;
    private final GenerationPhase phase;
    private final int priority;
    private final List<Class<? extends WG2Module>> dependencies;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final List<String> initOrder;

    public TestModule(String id, GenerationPhase phase, int priority) {
        this(id, phase, priority, List.of(), null);
    }

    TestModule(String id, GenerationPhase phase, int priority, Class<? extends WG2Module> dependency, List<String> initOrder) {
        this(id, phase, priority, List.of(dependency), initOrder);
    }

    private TestModule(
            String id,
            GenerationPhase phase,
            int priority,
            List<Class<? extends WG2Module>> dependencies,
            List<String> initOrder) {
        this.id = id;
        this.phase = phase;
        this.priority = priority;
        this.dependencies = dependencies;
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
        initialized.set(true);
        if (initOrder != null) {
            initOrder.add(id);
        }
    }

    @Override
    public void onChunkGenerate(ChunkGenContext ctx) {
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
    }

    @Override
    public List<Class<? extends WG2Module>> getDependencies() {
        return dependencies;
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}
