package com.piasop.worldgen2.modules.phase2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveModuleTest {
    private final CaveModule module = new CaveModule();

    @Test
    void sampleIsDeterministicForSameInputs() {
        double a = module.sampleCaveCarveLikelihood(120, 32, -80, 987654321L);
        double b = module.sampleCaveCarveLikelihood(120, 32, -80, 987654321L);
        assertEquals(a, b, 1.0e-12);
    }

    @Test
    void sampleStaysWithinZeroToOneRange() {
        for (int y = -64; y <= 256; y += 32) {
            double value = module.sampleCaveCarveLikelihood(42, y, -19, 11L);
            assertTrue(value >= 0.0 && value <= 1.0);
        }
    }

    @Test
    void verticalBandInfluencesCaveLikelihood() {
        double shallow = module.sampleCaveCarveLikelihood(42, 180, -19, 11L);
        double middle = module.sampleCaveCarveLikelihood(42, 32, -19, 11L);
        assertNotEquals(shallow, middle);
    }

    @Test
    void carveDecisionIsDeterministicForSameVoxel() {
        boolean a = module.shouldCarveAt(128, 24, -96, 2222L);
        boolean b = module.shouldCarveAt(128, 24, -96, 2222L);
        assertEquals(a, b);
    }

    @Test
    void abyssalBandIncreasesDeepLikelihood() {
        double deep = module.sampleAbyssalContribution(-56);
        double mid = module.sampleAbyssalContribution(-24);
        double shallow = module.sampleAbyssalContribution(16);
        assertTrue(deep > mid);
        assertTrue(mid > shallow);
    }
}
