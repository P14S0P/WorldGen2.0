package com.piasop.worldgen2.modules.phase2;

import com.piasop.worldgen2.api.RegionGenContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OceanModuleTest {
    private final OceanModule module = new OceanModule();

    @Test
    void generatedRegionHasExpectedArraySizes() {
        OceanModule.OceanRegionData data = module.generateRegionOcean(new RegionGenContext(0, 0, 77L));
        int expected = 32 * 32;
        assertEquals(expected, data.floorY().length);
        assertEquals(expected, data.oceanMask().length);
    }

    @Test
    void oceanSamplingIsDeterministic() {
        float aMask = module.sampleOceanMask(128, -256, 42L);
        float bMask = module.sampleOceanMask(128, -256, 42L);
        float aY = module.sampleOceanFloorY(128, -256, 42L);
        float bY = module.sampleOceanFloorY(128, -256, 42L);
        assertEquals(aMask, bMask, 1.0e-6f);
        assertEquals(aY, bY, 1.0e-6f);
    }

    @Test
    void oceanMaskAndFloorStayInExpectedRanges() {
        for (int i = 0; i < 24; i++) {
            int x = -4096 + (i * 341);
            int z = 2048 - (i * 173);
            float mask = module.sampleOceanMask(x, z, 9001L);
            float floor = module.sampleOceanFloorY(x, z, 9001L);
            assertTrue(mask >= 0.0f && mask <= 1.0f);
            assertTrue(floor <= 63.0f);
            assertTrue(floor >= -140.0f);
        }
    }

    @Test
    void oceanClassificationThresholdIsStable() {
        long seed = 31337L;
        int oceanLike = 0;
        int landLike = 0;
        for (int i = 0; i < 48; i++) {
            int x = -2048 + (i * 97);
            int z = 1024 - (i * 53);
            float mask = module.sampleOceanMask(x, z, seed);
            if (mask >= 0.5f) {
                oceanLike++;
            } else {
                landLike++;
            }
        }
        assertTrue(oceanLike > 0);
        assertTrue(landLike > 0);
    }
}
