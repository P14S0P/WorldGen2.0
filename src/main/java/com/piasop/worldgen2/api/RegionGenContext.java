package com.piasop.worldgen2.api;

/**
 * Context passed to modules during macro/regional pre-generation.
 */
public record RegionGenContext(int regionX, int regionZ, long worldSeed) {
}
