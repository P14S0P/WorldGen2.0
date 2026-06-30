package com.piasop.worldgen2.modules.phase3;

import com.piasop.worldgen2.api.RegionGenContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VegetationModuleTest {
    private final VegetationModule module = new VegetationModule();

    @Test
    void generatedRegionHasExpectedArraySizes() {
        VegetationModule.VegetationRegionData data = module.generateRegionVegetation(new RegionGenContext(0, 0, 77L));
        int expected = 32 * 32;
        assertEquals(expected, data.density().length);
        assertEquals(expected, data.diversity().length);
    }

    @Test
    void samplingIsDeterministicAndClamped() {
        float a = module.sampleVegetationDensity(640, -912, 119L);
        float b = module.sampleVegetationDensity(640, -912, 119L);
        assertEquals(a, b, 1.0e-6f);
        assertTrue(a >= 0.0f && a <= 1.0f);
    }

    @Test
    void altitudePenaltyReducesDensityAtHighElevation() {
        double low = module.computeDensity(18.0f, 850.0f, 72.0);
        double high = module.computeDensity(18.0f, 850.0f, 240.0);
        assertTrue(low > high);
    }
}
