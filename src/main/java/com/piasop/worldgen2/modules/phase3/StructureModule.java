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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3 structure module: contextual placement + bounded WFC footprint generation.
 */
public final class StructureModule implements WG2Module {
    private static final int REGION_SIZE = 32;

    private final TreeModule fallbackTrees = new TreeModule();
    private final ConcurrentHashMap<Long, StructureRegionData> regions = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "wg2:structures";
    }

    @Override
    public int getPriority() {
        return 57;
    }

    @Override
    public GenerationPhase getPhase() {
        return GenerationPhase.FEATURES;
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
                k -> generateRegionStructures(new RegionGenContext(regionX, regionZ, ctx.worldSeed())));
    }

    @Override
    public void onRegionLoad(RegionGenContext ctx) {
        regions.computeIfAbsent(regionKey(ctx.regionX(), ctx.regionZ()),
                k -> generateRegionStructures(ctx));
    }

    public StructureRegionData generateRegionStructures(RegionGenContext ctx) {
        int size = REGION_SIZE;
        float[] spawnChance = new float[size * size];
        byte[] prototypeIndex = new byte[size * size];
        StructurePrototype[] palette = defaultPalette();
        TreeModule trees = resolveTreeModule();

        int baseChunkX = ctx.regionX() * size;
        int baseChunkZ = ctx.regionZ() * size;
        for (int rz = 0; rz < size; rz++) {
            for (int rx = 0; rx < size; rx++) {
                int idx = index(rx, rz, size);
                int worldX = ((baseChunkX + rx) << 4) + 8;
                int worldZ = ((baseChunkZ + rz) << 4) + 8;

                float treeDensity = trees.sampleTreeDensity(worldX, worldZ, ctx.worldSeed());
                double spacingNoise = (Phase1Noise.fbmOpenSimplex2S2D(worldX * 0.0022, worldZ * 0.0022,
                        ctx.worldSeed() + 16057L, 3, 2.0, 0.5) + 1.0) * 0.5;
                double rarityNoise = (Phase1Noise.value2D(worldX * 0.007, worldZ * 0.007,
                        ctx.worldSeed() + 18233L) + 1.0) * 0.5;

                spawnChance[idx] = (float) clamp((treeDensity * 0.35) + (spacingNoise * 0.45) + (rarityNoise * 0.20), 0.0, 1.0);
                prototypeIndex[idx] = pickPrototypeIndex(spawnChance[idx], worldX, worldZ, ctx.worldSeed(), palette.length);
            }
        }
        return new StructureRegionData(size, spawnChance, prototypeIndex, palette);
    }

    public float sampleStructureChance(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        StructureRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionStructures(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        return data.spawnChance()[index(localChunkX, localChunkZ, data.size())];
    }

    private TreeModule resolveTreeModule() {
        return WG2Registry.get("wg2:trees")
                .filter(TreeModule.class::isInstance)
                .map(TreeModule.class::cast)
                .orElse(fallbackTrees);
    }

    public StructurePrototype samplePrototype(int worldX, int worldZ, long seed) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        StructureRegionData data = regions.computeIfAbsent(regionKey(regionX, regionZ),
                k -> generateRegionStructures(new RegionGenContext(regionX, regionZ, seed)));
        int localChunkX = Math.floorMod(chunkX, REGION_SIZE);
        int localChunkZ = Math.floorMod(chunkZ, REGION_SIZE);
        int idx = index(localChunkX, localChunkZ, data.size());
        int paletteIdx = Byte.toUnsignedInt(data.prototypeIndex()[idx]);
        return data.palette()[Math.min(paletteIdx, data.palette().length - 1)];
    }

    public void applyStructureAnchorsToChunk(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int minBuildY = chunk.getMinBuildHeight();
        int maxBuildY = minBuildY + chunk.getHeight() - 1;
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState anchor = Blocks.COBBLESTONE.defaultBlockState();
        Optional<ClimateModule> climate = WG2Registry.get("wg2:climate")
            .filter(ClimateModule.class::isInstance)
            .map(ClimateModule.class::cast);
        Optional<TerrainModule> terrain = WG2Registry.get("wg2:terrain")
            .filter(TerrainModule.class::isInstance)
            .map(TerrainModule.class::cast);

        int localX = 8;
        int localZ = 8;
        int worldX = baseX + localX;
        int worldZ = baseZ + localZ;
        StructurePrototype prototype = samplePrototype(worldX, worldZ, seed);
        float tunedChance = contextualSpawnChance(prototype, worldX, worldZ, seed, climate, terrain);
        float slope = sampleSlope(worldX, worldZ, seed, terrain);
        if (!shouldSpawnStructure(prototype, chunkPos.x, chunkPos.z, tunedChance, slope, seed)) {
            return;
        }

        int surfaceY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, localX, localZ) - 1;
        int placeY = clampInt(surfaceY + 1, minBuildY + 1, maxBuildY - 6);
        String[] footprint = generateFootprint(prototype, chunkPos.x, chunkPos.z, seed);
        placeFootprint(chunk, cursor, footprint, prototype, baseX + localX, placeY, baseZ + localZ, minBuildY, maxBuildY, anchor);
    }

    boolean shouldSpawnStructure(StructurePrototype prototype, int chunkX, int chunkZ, float tunedChance, float slope, long seed) {
        if (slope > prototype.maxSlope()) {
            return false;
        }
        if ("mine_adit".equals(prototype.id())) {
            Optional<MineralModule> minerals = WG2Registry.get("wg2:minerals")
                    .filter(MineralModule.class::isInstance)
                    .map(MineralModule.class::cast);
            if (minerals.isPresent() && !minerals.get().hasDeposit(chunkX, chunkZ, seed, 3)) {
                return false;
            }
        }
        double score = spawnScore(prototype, chunkX, chunkZ, tunedChance, seed);
        if (score < prototype.minDensity()) {
            return false;
        }
        return hasLocalDominance(prototype, chunkX, chunkZ, tunedChance, seed);
    }

    String[] generateFootprint(StructurePrototype prototype, int chunkX, int chunkZ, long seed) {
        WFCGenerator generator = new WFCGenerator(
                prototype.footprintSize(),
                List.of("core", "path", "wall", "empty"),
                footprintAdjacency(prototype),
                seed ^ prototype.salt() ^ (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL));
        return generator.generate("empty");
    }

    float computeContextualStructureModifier(float temperature, float precipitation, double elevation) {
        double hydro = clamp((precipitation - 220.0) / 900.0, 0.0, 1.0);
        double thermal = 1.0 - clamp(Math.abs(temperature - 14.0) / 40.0, 0.0, 1.0);
        double elevationPenalty = clamp((elevation - 130.0) / 180.0, 0.0, 1.0);
        double modifier = 0.70 + (hydro * 0.22) + (thermal * 0.12) - (elevationPenalty * 0.24);
        return (float) clamp(modifier, 0.55, 1.10);
    }

    private float contextualSpawnChance(
            StructurePrototype prototype,
            int worldX,
            int worldZ,
            long seed,
            Optional<ClimateModule> climate,
            Optional<TerrainModule> terrain) {
        float chance = sampleStructureChance(worldX, worldZ, seed);
        float temp = climate.map(c -> c.sampleTemperature(worldX, worldZ, seed)).orElse(14.0f);
        float precip = climate.map(c -> c.samplePrecipitation(worldX, worldZ, seed)).orElse(500.0f);
        double elevation = terrain.map(t -> t.sampleHeight(worldX, worldZ, seed)).orElse(90.0);
        float contextual = computeContextualStructureModifier(temp, precip, elevation) * prototypeBiomeAffinity(prototype, temp, precip);
        return (float) clamp(chance * contextual, 0.0, 1.0);
    }

    private float prototypeBiomeAffinity(StructurePrototype prototype, float temp, float precip) {
        return switch (prototype.id()) {
            case "camp_small" -> temp > 6.0f ? 1.0f : 0.84f;
            case "watchtower" -> 0.92f + (float) clamp((20.0 - Math.abs(temp - 10.0)) / 20.0, 0.0, 0.15);
            case "village_cluster" -> precip > 260.0f ? 1.06f : 0.82f;
            case "mine_adit" -> precip < 900.0f ? 1.04f : 0.88f;
            default -> 0.96f;
        };
    }

    float sampleSlope(int worldX, int worldZ, long seed, Optional<TerrainModule> terrain) {
        double center = terrain.map(t -> t.sampleHeight(worldX, worldZ, seed)).orElse(90.0);
        double east = terrain.map(t -> t.sampleHeight(worldX + 16, worldZ, seed)).orElse(center);
        double south = terrain.map(t -> t.sampleHeight(worldX, worldZ + 16, seed)).orElse(center);
        double dx = Math.abs(east - center);
        double dz = Math.abs(south - center);
        return (float) Math.sqrt((dx * dx) + (dz * dz));
    }

    private double spawnScore(StructurePrototype prototype, int chunkX, int chunkZ, float tunedChance, long seed) {
        double jitter = (Phase1Noise.value2D(chunkX * 0.17, chunkZ * 0.17, seed + prototype.salt()) + 1.0) * 0.5;
        return (tunedChance * 0.84) + (jitter * 0.16);
    }

    private boolean hasLocalDominance(StructurePrototype prototype, int chunkX, int chunkZ, float tunedChance, long seed) {
        double centerScore = spawnScore(prototype, chunkX, chunkZ, tunedChance, seed);
        for (int dz = -prototype.minSeparationChunks(); dz <= prototype.minSeparationChunks(); dz++) {
            for (int dx = -prototype.minSeparationChunks(); dx <= prototype.minSeparationChunks(); dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = chunkX + dx;
                int nz = chunkZ + dz;
                int worldX = (nx << 4) + 8;
                int worldZ = (nz << 4) + 8;
                float nearbyChance = sampleStructureChance(worldX, worldZ, seed);
                double nearbyScore = spawnScore(prototype, nx, nz, nearbyChance, seed);
                if (nearbyScore > centerScore) {
                    return false;
                }
            }
        }
        return true;
    }

    private Map<String, Set<String>> footprintAdjacency(StructurePrototype prototype) {
        Set<String> all = Set.of("core", "path", "wall", "empty");
        return switch (prototype.grammarTag()) {
            case "vertical-anchor" -> Map.of(
                    "core", Set.of("wall", "path", "core"),
                    "wall", Set.of("core", "wall", "empty"),
                    "path", Set.of("core", "path", "empty"),
                    "empty", all);
            case "grid-branch" -> Map.of(
                    "core", Set.of("core", "path", "wall"),
                    "path", Set.of("core", "path", "empty"),
                    "wall", Set.of("core", "wall", "path"),
                    "empty", all);
            default -> Map.of(
                    "core", Set.of("core", "path", "wall", "empty"),
                    "path", Set.of("core", "path", "empty"),
                    "wall", Set.of("core", "wall", "empty"),
                    "empty", all);
        };
    }

    private void placeFootprint(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            String[] footprint,
            StructurePrototype prototype,
            int centerX,
            int baseY,
            int centerZ,
            int minBuildY,
            int maxBuildY,
            BlockState fallbackAnchor) {
        int size = prototype.footprintSize();
        int half = size / 2;
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int chunkMaxZ = chunkMinZ + 15;

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                String tile = footprint[(z * size) + x];
                if ("empty".equals(tile)) {
                    continue;
                }
                int worldX = centerX + (x - half);
                int worldZ = centerZ + (z - half);
                if (worldX < chunkMinX || worldX > chunkMaxX || worldZ < chunkMinZ || worldZ > chunkMaxZ) {
                    continue;
                }

                BlockState state = switch (tile) {
                    case "core" -> prototype.id().equals("mine_adit") ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : fallbackAnchor;
                    case "wall" -> Blocks.STONE_BRICKS.defaultBlockState();
                    case "path" -> Blocks.GRAVEL.defaultBlockState();
                    default -> fallbackAnchor;
                };
                int y = clampInt(baseY + ("core".equals(tile) ? 1 : 0), minBuildY + 1, maxBuildY - 1);
                cursor.set(worldX, y, worldZ);
                chunk.setBlockState(cursor, state, false);
            }
        }
    }

    boolean shouldPlaceAnchor(float chance, double jitter) {
        return ((chance * 0.82) + (jitter * 0.18)) > 0.90;
    }

    private static byte pickPrototypeIndex(float chance, int worldX, int worldZ, long seed, int paletteSize) {
        double jitter = (Phase1Noise.value2D(worldX * 0.013, worldZ * 0.013, seed + 20389L) + 1.0) * 0.5;
        double selector = clamp((chance * 0.82) + (jitter * 0.18), 0.0, 0.999999);
        int raw = (int) Math.floor(selector * paletteSize);
        return (byte) Math.max(0, Math.min(raw, paletteSize - 1));
    }

    private static StructurePrototype[] defaultPalette() {
        return new StructurePrototype[]{
                new StructurePrototype("camp_small", "node-hub", 0.18f, 0.48f, 2, 18.0f, 7101L, 5),
                new StructurePrototype("watchtower", "vertical-anchor", 0.32f, 0.66f, 3, 14.0f, 9109L, 5),
                new StructurePrototype("village_cluster", "grid-branch", 0.55f, 0.92f, 4, 10.0f, 12113L, 7),
                new StructurePrototype("mine_adit", "grid-branch", 0.24f, 0.56f, 3, 16.0f, 15121L, 5),
                new StructurePrototype("ruin_scatter", "scatter-noise", 0.12f, 0.40f, 2, 22.0f, 17141L, 5)
        };
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

        public record StructurePrototype(
            String id,
            String grammarTag,
            float minDensity,
            float maxDensity,
            int minSeparationChunks,
            float maxSlope,
            long salt,
            int footprintSize) {
    }

    public record StructureRegionData(int size, float[] spawnChance, byte[] prototypeIndex, StructurePrototype[] palette) {
    }
}