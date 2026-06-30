package com.piasop.worldgen2.api;

/**
 * Context passed to modules during per-chunk generation.
 */
public record ChunkGenContext(int chunkX, int chunkZ, long worldSeed) {
}
