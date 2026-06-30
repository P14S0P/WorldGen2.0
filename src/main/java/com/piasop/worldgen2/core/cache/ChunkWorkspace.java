package com.piasop.worldgen2.core.cache;

/**
 * Thread-local reusable buffers for a single chunk generation pass.
 */
public final class ChunkWorkspace {
    public static final ThreadLocal<ChunkWorkspace> POOL = ThreadLocal.withInitial(ChunkWorkspace::new);

    private final double[] densityBuffer = new double[384];
    private final double[] caveBuffer = new double[384];

    public double[] densityBuffer() {
        return densityBuffer;
    }

    public double[] caveBuffer() {
        return caveBuffer;
    }

    public void reset() {
        // Buffers are overwritten each pass; no explicit reset required yet.
    }
}
