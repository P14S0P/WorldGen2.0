package com.piasop.worldgen2.core;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * Forge-backed configuration for WorldGen 2.0.
 */
@Mod.EventBusSubscriber(modid = WG2Mod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WG2Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Master switch for WorldGen 2.0 generation pipeline.")
            .define("enabled", true);

    private static final ForgeConfigSpec.BooleanValue VANILLA_COMPAT = BUILDER
            .comment("When true, only performance improvements run; vanilla terrain and biomes are preserved.")
            .define("vanilla_compat", false);

    private static final ForgeConfigSpec.IntValue CHUNK_CACHE_SIZE = BUILDER
            .comment("Maximum number of chunks kept in the LRU chunk cache.")
            .defineInRange("chunk_cache_size", 512, 64, 4096);

    private static final ForgeConfigSpec.IntValue THREAD_POOL_SIZE = BUILDER
            .comment("Worker threads for async macro/regional generation. 0 = auto (CPU cores - 1).")
            .defineInRange("thread_pool_size", 0, 0, 32);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ACTIVE_DIMENSIONS = BUILDER
            .comment("Dimensions where WG2 replaces vanilla generation (e.g. minecraft:overworld).")
            .defineListAllowEmpty("active_dimensions", List.of("minecraft:overworld"), WG2Config::isValidDimensionId);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enabled = true;
    public static boolean vanillaCompat = false;
    public static int chunkCacheSize = 512;
    public static int threadPoolSize = 0;
    public static List<? extends String> activeDimensions = List.of("minecraft:overworld");

    public static final WG2Config INSTANCE = new WG2Config();

    private WG2Config() {
    }

    public static boolean isActiveInDimension(ResourceKey<Level> dimension) {
        if (!enabled || vanillaCompat) {
            return false;
        }
        return activeDimensions.contains(dimension.location().toString());
    }

    public float getFloat(String key, float defaultValue) {
        // Module-specific TOML keys will be expanded in later phases.
        return defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        return defaultValue;
    }

    private static boolean isValidDimensionId(Object value) {
        return value instanceof String id && id.contains(":");
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        enabled = ENABLED.get();
        vanillaCompat = VANILLA_COMPAT.get();
        chunkCacheSize = CHUNK_CACHE_SIZE.get();
        threadPoolSize = THREAD_POOL_SIZE.get();
        activeDimensions = ACTIVE_DIMENSIONS.get();
    }
}
