package com.piasop.worldgen2.core;

import com.piasop.worldgen2.core.cache.RegionData;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WG2DataCacheTest {
    @AfterEach
    void tearDown() {
        WG2DataCache.INSTANCE.clear();
        WG2DataCache.INSTANCE.configure(512);
    }

    @Test
    void createsAndReusesRegionData() {
        RegionData first = WG2DataCache.INSTANCE.getRegionData(2, 3);
        RegionData second = WG2DataCache.INSTANCE.getRegionData(2, 3);

        assertSame(first, second);
        assertEquals(2, first.regionX());
        assertEquals(3, first.regionZ());
    }

    @Test
    void usesCustomRegionGeneratorOnce() {
        RegionData generated = WG2DataCache.INSTANCE.getRegionData(1, 1, () -> new RegionData(1, 1, 42L));

        assertEquals(42L, generated.seed());
        assertEquals(42L, WG2DataCache.INSTANCE.getRegionData(1, 1).seed());
    }

    @Test
    void evictsOldestChunkWhenCapacityExceeded() {
        WG2DataCache.INSTANCE.configure(2);

        WG2DataCache.INSTANCE.getOrCreateChunkData(0, 0);
        WG2DataCache.INSTANCE.getOrCreateChunkData(1, 0);
        WG2DataCache.INSTANCE.getOrCreateChunkData(2, 0);

        assertNull(WG2DataCache.INSTANCE.getChunkData(0, 0));
        assertNotNull(WG2DataCache.INSTANCE.getChunkData(1, 0));
        assertNotNull(WG2DataCache.INSTANCE.getChunkData(2, 0));
    }

    @Test
    void invalidateRemovesChunkAndRegionEntries() {
        ChunkPos pos = new ChunkPos(64, 64);
        WG2DataCache.INSTANCE.getRegionData(pos.getRegionX(), pos.getRegionZ());
        WG2DataCache.INSTANCE.getOrCreateChunkData(pos.x, pos.z);

        WG2DataCache.INSTANCE.invalidate(pos);

        assertNull(WG2DataCache.INSTANCE.getChunkData(pos.x, pos.z));
        assertEquals(0, WG2DataCache.INSTANCE.macroView().size());
    }
}
