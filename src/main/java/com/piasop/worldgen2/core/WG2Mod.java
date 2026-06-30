package com.piasop.worldgen2.core;

import com.mojang.logging.LogUtils;
import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.api.events.WG2LifecycleEvent;
import com.piasop.worldgen2.modules.phase1.BiomeModule;
import com.piasop.worldgen2.modules.phase1.ClimateModule;
import com.piasop.worldgen2.modules.phase1.TerrainModule;
import com.piasop.worldgen2.modules.phase2.CaveModule;
import com.piasop.worldgen2.modules.phase2.OceanModule;
import com.piasop.worldgen2.modules.phase2.RiverModule;
import com.piasop.worldgen2.modules.phase3.MineralModule;
import com.piasop.worldgen2.modules.phase3.RuinsModule;
import com.piasop.worldgen2.modules.phase3.StructureModule;
import com.piasop.worldgen2.modules.phase3.TreeModule;
import com.piasop.worldgen2.modules.phase3.VegetationModule;
import com.piasop.worldgen2.threading.WG2ThreadPool;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Forge entry point for WorldGen 2.0.
 */
@Mod(WG2Mod.MODID)
public class WG2Mod {
    public static final String MODID = "worldgen2";
    private static final Logger LOGGER = LogUtils.getLogger();

    public WG2Mod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        context.registerConfig(ModConfig.Type.COMMON, WG2Config.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("WorldGen 2.0 loading — author PIASOP");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerBuiltInModules();
            WG2DataCache.INSTANCE.configure(WG2Config.chunkCacheSize);
            WG2ThreadPool.start(WG2Config.threadPoolSize);
            WG2Registry.initializeAll(WG2Config.INSTANCE, WG2DataCache.INSTANCE);
            LOGGER.info("WorldGen 2.0 core initialized ({} modules registered)", WG2Registry.all().size());
        });
    }

    private static void registerBuiltInModules() {
        if (WG2Registry.get("wg2:terrain").isPresent()) {
            return;
        }
        WG2Registry.register(new TerrainModule());
        WG2Registry.register(new ClimateModule());
        WG2Registry.register(new BiomeModule());
        WG2Registry.register(new CaveModule());
        WG2Registry.register(new RiverModule());
        WG2Registry.register(new OceanModule());
        WG2Registry.register(new VegetationModule());
        WG2Registry.register(new TreeModule());
        WG2Registry.register(new StructureModule());
        WG2Registry.register(new RuinsModule());
        WG2Registry.register(new MineralModule());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        WG2ThreadPool.shutdown();
        WG2DataCache.INSTANCE.clear();
        WG2EventBus.fire(new WG2LifecycleEvent(WG2LifecycleEvent.Stage.SHUTDOWN));
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && WG2Config.isActiveInDimension(serverLevel.dimension())) {
            WG2DataCache.INSTANCE.clear();
        }
    }

    /**
     * Dispatches chunk generation to all modules for the given phase.
     * Called by future mixins/hooks once terrain modules are implemented.
     */
    public static void dispatchChunkGeneration(GenerationPhase phase, ChunkGenContext ctx) {
        if (!WG2Config.enabled || WG2Config.vanillaCompat) {
            return;
        }
        for (WG2Module module : WG2Registry.byPhase(phase)) {
            if (module.canRunAsync()) {
                WG2ThreadPool.submit(() -> module.onChunkGenerate(ctx));
            } else {
                module.onChunkGenerate(ctx);
            }
        }
    }

    /**
     * Dispatches regional pre-generation to all modules for the given phase.
     */
    public static void dispatchRegionLoad(GenerationPhase phase, RegionGenContext ctx) {
        if (!WG2Config.enabled || WG2Config.vanillaCompat) {
            return;
        }
        for (WG2Module module : WG2Registry.byPhase(phase)) {
            if (module.canRunAsync()) {
                WG2ThreadPool.submit(() -> module.onRegionLoad(ctx));
            } else {
                module.onRegionLoad(ctx);
            }
        }
    }
}
