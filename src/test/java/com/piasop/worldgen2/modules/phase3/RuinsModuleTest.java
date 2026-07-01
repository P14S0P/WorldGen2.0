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

    @Test
    void degradationGateRequiresBothRuinChanceAndDamage() {
        assertTrue(module.shouldApplyDegradation(0.83f, 0.71f));
        assertTrue(!module.shouldApplyDegradation(0.50f, 0.90f));
        assertTrue(!module.shouldApplyDegradation(0.90f, 0.30f));
    }

    @Test
    void contextualRuinModifierIncreasesWithStressAndHumidity() {
        float mild = module.computeContextualRuinModifier(20.0f, 220.0f, 95.0);
        float stressed = module.computeContextualRuinModifier(-2.0f, 980.0f, 260.0);
        assertTrue(stressed > mild);
        assertTrue(stressed >= 0.60f && stressed <= 1.15f);
    }

    @Test
    void collapseGateDependsOnWeaknessAndDamage() {
        assertTrue(module.shouldCollapseWithWeakness(1.0f, 0.90f));
        assertTrue(!module.shouldCollapseWithWeakness(0.8f, 0.40f));
    }

    @Test
    void vinesNeedMoistureAndDamage() {
        assertTrue(module.shouldAddVines(0.70f, 0.75f));
        assertTrue(!module.shouldAddVines(0.30f, 0.75f));
        assertTrue(!module.shouldAddVines(0.70f, 0.20f));
    }
}