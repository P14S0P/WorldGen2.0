package com.piasop.worldgen2.core;

import com.piasop.worldgen2.core.cache.ChunkData;
import com.piasop.worldgen2.core.cache.ChunkWorkspace;
import com.piasop.worldgen2.core.cache.RegionData;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Thread-safe three-level cache for macro, chunk, and workspace data.
 */
public final class WG2DataCache {
    public static final WG2DataCache INSTANCE = new WG2DataCache();

    private final ConcurrentHashMap<Long, RegionData> macroCache = new ConcurrentHashMap<>();
    private final LinkedHashMap<Long, ChunkData> chunkCache;
    private int chunkCacheCapacity = 512;

    private WG2DataCache() {
        chunkCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ChunkData> eldest) {
                return size() > chunkCacheCapacity;
            }
        };
    }

    public void configure(int maxChunks) {
        synchronized (chunkCache) {
            chunkCacheCapacity = maxChunks;
            while (chunkCache.size() > chunkCacheCapacity) {
                var iterator = chunkCache.entrySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
    }

    public RegionData getRegionData(int regionX, int regionZ) {
        long key = regionKey(regionX, regionZ);
        return macroCache.computeIfAbsent(key, k -> RegionData.empty(regionX, regionZ, 0L));
    }

    public RegionData getRegionData(int regionX, int regionZ, Supplier<RegionData> generator) {
        long key = regionKey(regionX, regionZ);
        return macroCache.computeIfAbsent(key, k -> generator.get());
    }

    public ChunkData getChunkData(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (chunkCache) {
            return chunkCache.get(key);
        }
    }

    public ChunkData getOrCreateChunkData(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (chunkCache) {
            return chunkCache.computeIfAbsent(key, k -> new ChunkData(chunkX, chunkZ));
        }
    }

    public ChunkWorkspace workspace() {
        return ChunkWorkspace.POOL.get();
    }

    public void invalidate(ChunkPos pos) {
        synchronized (chunkCache) {
            chunkCache.remove(pos.toLong());
        }
        macroCache.remove(regionKey(pos.getRegionX(), pos.getRegionZ()));
    }

    public void clear() {
        macroCache.clear();
        synchronized (chunkCache) {
            chunkCache.clear();
        }
    }

    public Map<Long, RegionData> macroView() {
        return Collections.unmodifiableMap(macroCache);
    }

    private static long regionKey(int regionX, int regionZ) {
        return ChunkPos.asLong(regionX, regionZ);
    }
}
