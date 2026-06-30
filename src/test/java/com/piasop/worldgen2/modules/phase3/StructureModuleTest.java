package com.piasop.worldgen2.modules.phase3;

import com.piasop.worldgen2.api.RegionGenContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureModuleTest {
    private final StructureModule module = new StructureModule();

    @Test
    void generatedRegionHasExpectedArraySizes() {
        StructureModule.StructureRegionData data = module.generateRegionStructures(new RegionGenContext(0, 0, 903L));
        int expected = 32 * 32;
        assertEquals(expected, data.spawnChance().length);
        assertEquals(expected, data.prototypeIndex().length);
        assertEquals(4, data.palette().length);
    }

    @Test
    void structureChanceIsDeterministicAndClamped() {
        float a = module.sampleStructureChance(-144, 992, 77L);
        float b = module.sampleStructureChance(-144, 992, 77L);
        assertEquals(a, b, 1.0e-6f);
        assertTrue(a >= 0.0f && a <= 1.0f);
    }

    @Test
    void prototypeSamplingIsDeterministic() {
        StructureModule.StructurePrototype first = module.samplePrototype(640, -2048, 135L);
        StructureModule.StructurePrototype second = module.samplePrototype(640, -2048, 135L);
        assertNotNull(first);
        assertEquals(first.id(), second.id());
        assertTrue(first.minDensity() <= first.maxDensity());
    }
}