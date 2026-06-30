package com.piasop.worldgen2.modules.phase3;

import com.piasop.worldgen2.api.RegionGenContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsModuleTest {
    private final RuinsModule module = new RuinsModule();

    @Test
    void generatedRegionHasExpectedArraySizes() {
        RuinsModule.RuinsRegionData data = module.generateRegionRuins(new RegionGenContext(0, 0, 271L));
        int expected = 32 * 32;
        assertEquals(expected, data.ruinChance().length);
        assertEquals(expected, data.degradation().length);
        assertEquals(expected, data.archetypeIndex().length);
        assertEquals(4, data.palette().length);
    }

    @Test
    void sampledValuesAreDeterministicAndClamped() {
        float chanceA = module.sampleRuinChance(1024, -512, 55L);
        float chanceB = module.sampleRuinChance(1024, -512, 55L);
        float decayA = module.sampleDegradation(1024, -512, 55L);
        float decayB = module.sampleDegradation(1024, -512, 55L);

        assertEquals(chanceA, chanceB, 1.0e-6f);
        assertEquals(decayA, decayB, 1.0e-6f);
        assertTrue(chanceA >= 0.0f && chanceA <= 1.0f);
        assertTrue(decayA >= 0.0f && decayA <= 1.0f);
    }

    @Test
    void archetypeSamplingIsDeterministic() {
        RuinsModule.RuinArchetype first = module.sampleArchetype(-736, 1440, 889L);
        RuinsModule.RuinArchetype second = module.sampleArchetype(-736, 1440, 889L);
        assertNotNull(first);
        assertEquals(first.id(), second.id());
        assertTrue(first.minDegradation() <= first.maxDegradation());
    }
}