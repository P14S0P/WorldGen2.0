package com.piasop.worldgen2.modules.phase3;

import com.piasop.worldgen2.api.RegionGenContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeModuleTest {
    private final TreeModule module = new TreeModule();

    @Test
    void generatedRegionHasExpectedArraySizes() {
        TreeModule.TreeRegionData data = module.generateRegionTrees(new RegionGenContext(0, 0, 91L));
        int expected = 32 * 32;
        assertEquals(expected, data.treeDensity().length);
        assertEquals(expected, data.prototypeIndex().length);
        assertEquals(4, data.palette().length);
    }

    @Test
    void samplingDensityIsDeterministicAndClamped() {
        float a = module.sampleTreeDensity(384, -448, 1234L);
        float b = module.sampleTreeDensity(384, -448, 1234L);
        assertEquals(a, b, 1.0e-6f);
        assertTrue(a >= 0.0f && a <= 1.0f);
    }

    @Test
    void prototypeSamplingIsDeterministic() {
        TreeModule.TreePrototype first = module.samplePrototype(1280, 736, 42L);
        TreeModule.TreePrototype second = module.samplePrototype(1280, 736, 42L);
        assertNotNull(first);
        assertEquals(first.id(), second.id());
        assertTrue(first.minHeight() <= first.maxHeight());
        assertTrue(first.minCanopyRadius() <= first.maxCanopyRadius());
    }
}