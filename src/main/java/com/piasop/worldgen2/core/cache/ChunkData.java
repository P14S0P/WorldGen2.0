package com.piasop.worldgen2.core.cache;

/**
 * Per-chunk cached generation data (heightmap samples, biome base, noise buffers).
 */
public record ChunkData(int chunkX, int chunkZ) {
}
