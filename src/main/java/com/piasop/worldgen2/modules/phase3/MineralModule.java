package com.piasop.worldgen2.modules.phase3;

import com.piasop.worldgen2.api.ChunkGenContext;
import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.RegionGenContext;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.core.WG2Config;
import com.piasop.worldgen2.core.WG2DataCache;
import com.piasop.worldgen2.core.WG2Registry;
import com.piasop.worldgen2.modules.phase1.ClimateModule;
import com.piasop.worldgen2.modules.phase1.Phase1Noise;
import com.piasop.worldgen2.modules.phase1.TerrainModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3 mineral baseline: deterministic stratigraphy signals and ore richness potential.
 */
public final class MineralModule implements WG2Module {
    private static final int REGION_SIZE = 32;
    private static final int STRATA_MIN_Y = -56;
    private static final int STRATA_MAX_Y = 70;
    private static final int STRATA_Y_STEP = 2;
    private static final float COLUMN_MIN_SIGNAL = 0.46f;
        private static final MineralProfile[] DEFAULT_PROFILES = new MineralProfile[]{
            new MineralProfile("sedimentary_iron", -8, 64, -32, 32),
            new MineralProfile("metamorphic_copper", -24, 48, -48, 16),
            new MineralProfile("igneous_gold", -40, 24, -64, -8),
            new MineralProfile("deep_crystal_mix", -56, 8, -64, -24)
        };

    private final RuinsModule fallbackRuins = new RuinsModule();
    private final ConcurrentHashMap<Long, MineralRegionData> regions = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:minerals";
    }

    @Override
    public int getPriority() {
        return 59;
    }

    @Override
    public GenerationPhase getPhase() {
        return GenerationPhase.NOISE;
    }

    @Override
    public boolean canRunAsync() {
        return true;
    }

    @Override
    public void initialize(WG2Config config, WG2DataCache cache) {
    }

    @Override
    public void onChunkGenerate(ChunkGenContext ctx) {
        int regionX = Math.floorDiv(ctx.chunkX(), REGION_SIZE);
        int regionZ = Math.floorDiv(ctx.chunkZ(), REGION_SIZE);
        regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()),
                k -> generateRegionMinerals(ctx));
    }

    public MineralRegionData generateRegionMinerals(RegionGenContext ctx) {
        int size = REGION_SIZE;
        float[] richness = new float[size * size];
        float[] deepBandBias = new float[size * size];
        byte[] layerProfile = new byte[size * size];
        MineralProfile[] palette = DEFAULT_PROFILES;
        RuinsModule ruins = resolveRuinsModule();

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;
        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float ruinDegradation = ruins.sampleDegradation(worldX, worldZ, ctx.worldSeed());
                double baseNoise = (Phase1Noise.fbmOpenSimplex2S2D(worldX * 0.0028, worldZ * 0.0028,
                        ctx.worldSeed() + 26417L, 3, 2.0, 0.5) + 1.0) * 0.5;
                double fractureNoise = (Phase1Noise.value2D(worldX * 0.009, worldZ * 0.009,
                        ctx.worldSeed() + 27803L) + 1.0) * 0.5;

                richness[idx] = (float) clamp((baseNoise * 0.58) + (fractureNoise * 0.27) + (ruinDegradation * 0.15), 0.0, 1.0);
                deepBandBias[idx] = (float) clamp((fractureNoise * 0.62) + (baseNoise * 0.38), 0.0, 1.0);
                layerProfile[idx] = pickLayerProfile(richness[idx], deepBandBias[idx], worldX, worldZ, ctx.worldSeed(), palette.length);
            }
        }
        return new MineralRegionData(size, richness, deepBandBias, layerProfile, palette);
    }

    public float sampleRichness(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.richness()[index(localChunkX, localChunkZ, data.size())];
    }

    private RuinsModule resolveRuinsModule() {
        return WG2Registry.get("wg2:ruins")
                .filter(RuinsModule.class::isInstance)
                .map(RuinsModule.class::cast)
                .orElse(fallbackRuins);
    }

    public float sampleDeepBandBias(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.deepBandBias()[index(localChunkX, localChunkZ, data.size())];
    }

    public MineralProfile sampleProfile(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());
        int paletteIdx = Byte.toUnsignedInt(data.layerProfile()[idx]);
        return data.palette()[Math.min(paletteIdx, data.palette().length - 1)];
    }

    public DepositType sampleDepositType(int worldX, int worldZ, long seed) {
        MineralProfile profile = sampleProfile(worldX, worldZ, seed);
        return switch (profile.id()) {
            case "sedimentary_iron" -> DepositType.SEAM;
            case "metamorphic_copper" -> DepositType.VEIN;
            case "igneous_gold" -> DepositType.VEIN;
            case "deep_crystal_mix" -> DepositType.NODULE;
            default -> DepositType.MASSIVE;
        };
    }

    public boolean hasDeposit(int chunkX, int chunkZ, long seed, int radiusChunks) {
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                int worldX = ((chunkX + dx) << 4) + 8;
                int worldZ = ((chunkZ + dz) << 4) + 8;
                if (sampleRichness(worldX, worldZ, seed) > 0.74f) {
                    return true;
                }
            }
        }
        return false;
    }

    public void applyMineralStrataToChunk(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;
        int minBuildY = chunk.getMinBuildHeight();
        int maxBuildY = minBuildY + chunk.getHeight() - 1;
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();

        int minY = Math.max(minBuildY + 8, STRATA_MIN_Y);
        int maxY = Math.min(maxBuildY - 4, STRATA_MAX_Y);
        if (maxY <= minY) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        float chunkRichness = sampleRichnessForChunk(chunkX, chunkZ, seed);
        float chunkDeepBias = sampleDeepBandBiasForChunk(chunkX, chunkZ, seed);
        MineralProfile chunkProfile = sampleProfileForChunk(chunkX, chunkZ, seed);
        DepositType chunkDepositType = depositTypeForProfile(chunkProfile);
        BlockState chunkTarget = profileToState(chunkProfile.id());

        TerrainModule terrain = resolveTerrainModule();
        ClimateModule climate = resolveClimateModule();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
            double elevation = terrain != null ? terrain.sampleHeight(worldX, worldZ, seed) : 90.0;
            float temperature = climate != null ? climate.sampleTemperature(worldX, worldZ, seed) : 14.0f;
                float contextual = computeContextualMineralModifier(elevation, temperature);
                float tunedRichness = (float) clamp(chunkRichness * contextual, 0.0, 1.0);
                if (((tunedRichness * 0.65f) + (chunkDeepBias * 0.35f)) < COLUMN_MIN_SIGNAL) {
                    continue;
                }
                double selectorX = worldX * 0.032;
                double selectorZBase = worldZ * 0.017;
                double selectorY = (minY * 0.041) + selectorZBase;
                double selectorStep = STRATA_Y_STEP * 0.041;

                for (int y = minY; y <= maxY; y += STRATA_Y_STEP) {
                    double selector = (Phase1Noise.value2D(selectorX, selectorY, seed + 30757L) + 1.0) * 0.5;
                    selectorY += selectorStep;
                    if (!shouldApplyStrataAt(y, chunkProfile, tunedRichness, chunkDeepBias, selector)) {
                        continue;
                    }

                    cursor.set(worldX, y, worldZ);
                    BlockState current = chunk.getBlockState(cursor);
                    if (!isReplaceableHost(current.getBlock())) {
                        continue;
                    }
                    chunk.setBlockState(cursor, chunkTarget, false);
                    placeDepositOre(chunk, cursor, chunkProfile.id(), chunkDepositType, y, selector);
                }
            }
        }
    }

    private float sampleRichnessForChunk(int chunkX, int chunkZ, long seed) {
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.richness()[index(localChunkX, localChunkZ, data.size())];
    }

    private float sampleDeepBandBiasForChunk(int chunkX, int chunkZ, long seed) {
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.deepBandBias()[index(localChunkX, localChunkZ, data.size())];
    }

    private MineralProfile sampleProfileForChunk(int chunkX, int chunkZ, long seed) {
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);
        MineralRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionMinerals(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());
        int paletteIdx = Byte.toUnsignedInt(data.layerProfile()[idx]);
        return data.palette()[Math.min(paletteIdx, data.palette().length - 1)];
    }

    float computeContextualMineralModifier(double elevation, float temperature) {
        double uplift = clamp((elevation - 95.0) / 170.0, 0.0, 1.0);
        double coldness = clamp((10.0 - temperature) / 30.0, 0.0, 1.0);
        double modifier = 0.85 + (uplift * 0.30) + (coldness * 0.10);
        return (float) clamp(modifier, 0.70, 1.25);
    }

    boolean shouldApplyStrataAt(int y, MineralProfile profile, float richness, float deepBias, double selector) {
        boolean inPrimary = y >= profile.primaryMinY() && y <= profile.primaryMaxY();
        boolean inSecondary = y >= profile.secondaryMinY() && y <= profile.secondaryMaxY();
        if (!inPrimary && !inSecondary) {
            return false;
        }

        double bandWeight = inPrimary ? 0.72 : 0.46;
        double gate = (richness * 0.58) + (deepBias * 0.27) + (selector * 0.15);
        return gate > (0.66 - (bandWeight * 0.08));
    }

    private void placeDepositOre(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, String profileId, DepositType depositType, int y, double selector) {
        if (selector < 0.82) {
            return;
        }

        BlockState oreState = switch (profileId) {
            case "sedimentary_iron" -> y < 0 ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
            case "metamorphic_copper" -> y < 0 ? Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState() : Blocks.COPPER_ORE.defaultBlockState();
            case "igneous_gold" -> y < 0 ? Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState() : Blocks.GOLD_ORE.defaultBlockState();
            case "deep_crystal_mix" -> Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
            default -> Blocks.COAL_ORE.defaultBlockState();
        };

        if (depositType == DepositType.NODULE && selector < 0.93) {
            return;
        }
        chunk.setBlockState(cursor, oreState, false);
    }

    private static DepositType depositTypeForProfile(MineralProfile profile) {
        return switch (profile.id()) {
            case "sedimentary_iron" -> DepositType.SEAM;
            case "metamorphic_copper", "igneous_gold" -> DepositType.VEIN;
            case "deep_crystal_mix" -> DepositType.NODULE;
            default -> DepositType.MASSIVE;
        };
    }

    private static boolean isReplaceableHost(Block block) {
        return block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.TUFF;
    }

    private static BlockState profileToState(String id) {
        if ("metamorphic_copper".equals(id)) {
            return Blocks.GRANITE.defaultBlockState();
        }
        if ("igneous_gold".equals(id)) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        if ("deep_crystal_mix".equals(id)) {
            return Blocks.TUFF.defaultBlockState();
        }
        return Blocks.CALCITE.defaultBlockState();
    }

    private static byte pickLayerProfile(float richness, float deepBias, int worldX, int worldZ, long seed, int paletteSize) {
        double jitter = (Phase1Noise.value2D(worldX * 0.016, worldZ * 0.016, seed + 29191L) + 1.0) * 0.5;
        double selector = clamp((richness * 0.57) + (deepBias * 0.30) + (jitter * 0.13), 0.0, 0.999999);
        int raw = (int) Math.floor(selector * paletteSize);
        return (byte) Math.max(0, Math.min(raw, paletteSize - 1));
    }

    private TerrainModule resolveTerrainModule() {
        return WG2Registry.get("wg2:terrain")
                .filter(TerrainModule.class::isInstance)
                .map(TerrainModule.class::cast)
                .orElse(null);
    }

    private ClimateModule resolveClimateModule() {
        return WG2Registry.get("wg2:climate")
                .filter(ClimateModule.class::isInstance)
                .map(ClimateModule.class::cast)
                .orElse(null);
    }

    private static int index(int x, int z, int size) {
        return (z * size) + x;
    }

    private static long regionKey(int rx, int rz) {
        return (((long) rx) << 32) ^ (rz & 0xffffffffL);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum DepositType {
        VEIN,
        SEAM,
        NODULE,
        MASSIVE
    }

    public record MineralProfile(String id, int primaryMinY, int primaryMaxY, int secondaryMinY, int secondaryMaxY) {
    }

    public record MineralRegionData(int size, float[] richness, float[] deepBandBias, byte[] layerProfile,
                                    MineralProfile[] palette) {
    }
}