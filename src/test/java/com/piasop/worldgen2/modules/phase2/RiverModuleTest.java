package com.piasop.worldgen2.modules.phase2;

import com.piasop.worldgen2.api.RegionGenContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiverModuleTest {
    private final RiverModule module = new RiverModule();

    @Test
    void generatedRegionHasExpectedCellCount() {
        RiverModule.RiverRegionData data = module.generateRegionFlow(new RegionGenContext(0, 0, 1234L));
        int expected = 32 * 32;
        assertEquals(expected, data.heights().length);
        assertEquals(expected, data.downstream().length);
        assertEquals(expected, data.accumulation().length);
    }

    @Test
    void riverMaskIsDeterministicAndClamped() {
        double a = module.sampleRiverMask(140, -260, 9876L);
        double b = module.sampleRiverMask(140, -260, 9876L);
        assertEquals(a, b, 1.0e-12);
        assertTrue(a >= 0.0 && a <= 1.0);
    }

    @Test
    void accumulationProducesLargeFlowCells() {
        RiverModule.RiverRegionData data = module.generateRegionFlow(new RegionGenContext(1, 1, 42L));
        double max = 0.0;
        for (double v : data.accumulation()) {
            if (v > max) {
                max = v;
            }
        }
        assertTrue(max > 2.0);
    }

    @Test
    void carveProfileScalesWithRiverMask() {
        int lowWidth = module.riverHalfWidth(0.1);
        int highWidth = module.riverHalfWidth(0.9);
        int lowDepth = module.riverDepth(0.1);
        int highDepth = module.riverDepth(0.9);

        assertTrue(highWidth > lowWidth);
        assertTrue(highDepth > lowDepth);
    }
}
