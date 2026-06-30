package com.piasop.worldgen2.api;

import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;

import java.util.Collections;
import java.util.List;

/**
 * Pluggable generation module interface. Each subsystem (terrain, climate, rivers, etc.)
 * implements this contract and registers with {@link com.piasop.worldgen2.core.WG2Registry}.
 */
public interface WG2Module {
    String getId();

    int getPriority();

    GenerationPhase getPhase();

    boolean canRunAsync();

    void initialize(WG2Config config, WG2DataCache cache);

    void onChunkGenerate(ChunkGenContext ctx);

    void onRegionLoad(RegionGenContext ctx);

    default List<Class<? extends WG2Module>> getDependencies() {
        return Collections.emptyList();
    }
}
