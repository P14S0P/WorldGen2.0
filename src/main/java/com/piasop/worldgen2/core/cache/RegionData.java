package com.piasop.worldgen2.core.cache;

/**
 * Macro-scale data for a 32×32 chunk region (climate, tectonics, river network).
 */
public record RegionData(int regionX, int regionZ, long seed) {
    public static RegionData empty(int regionX, int regionZ, long seed) {
        return new RegionData(regionX, regionZ, seed);
    }
}
