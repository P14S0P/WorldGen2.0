package com.piasop.worldgen2.modules.phase3;

import com.piasop.worldgen2.api.RegionGenContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineralModuleTest {
    private final MineralModule module = new MineralModule();

    @Test
    void generatedRegionHasExpectedArraySizes() {
        MineralModule.MineralRegionData data = module.generateRegionMinerals(new RegionGenContext(0, 0, 411L));
        int expected = 32 * 32;
        assertEquals(expected, data.richness().length);
        assertEquals(expected, data.deepBandBias().length);
        assertEquals(expected, data.layerProfile().length);
        assertEquals(4, data.palette().length);
    }

    @Test
    void samplingIsDeterministicAndClamped() {
        float richnessA = module.sampleRichness(256, -896, 73L);
        float richnessB = module.sampleRichness(256, -896, 73L);
        float deepA = module.sampleDeepBandBias(256, -896, 73L);
        float deepB = module.sampleDeepBandBias(256, -896, 73L);

        assertEquals(richnessA, richnessB, 1.0e-6f);
        assertEquals(deepA, deepB, 1.0e-6f);
        assertTrue(richnessA >= 0.0f && richnessA <= 1.0f);
        assertTrue(deepA >= 0.0f && deepA <= 1.0f);
    }

    @Test
    void profileSamplingIsDeterministic() {
        MineralModule.MineralProfile first = module.sampleProfile(-992, 208, 1001L);
        MineralModule.MineralProfile second = module.sampleProfile(-992, 208, 1001L);
        assertNotNull(first);
        assertEquals(first.id(), second.id());
        assertTrue(first.primaryMinY() <= first.primaryMaxY());
    }

    @Test
    void strataGateDependsOnBandAndSignals() {
        MineralModule.MineralProfile profile = new MineralModule.MineralProfile("test", -20, 20, -50, -30);
        assertTrue(module.shouldApplyStrataAt(0, profile, 0.95f, 0.90f, 0.9));
        assertTrue(!module.shouldApplyStrataAt(90, profile, 0.95f, 0.90f, 0.9));
        assertTrue(!module.shouldApplyStrataAt(0, profile, 0.20f, 0.10f, 0.1));
    }

    @Test
    void contextualMineralModifierBoostsUpliftedAndColdRegions() {
        float plainsWarm = module.computeContextualMineralModifier(90.0, 20.0f);
        float mountainsCold = module.computeContextualMineralModifier(260.0, -6.0f);
        assertTrue(mountainsCold > plainsWarm);
        assertTrue(mountainsCold >= 0.70f && mountainsCold <= 1.25f);
    }

    @Test
    void depositTypeIsDeterministic() {
        MineralModule.DepositType first = module.sampleDepositType(-992, 208, 1001L);
        MineralModule.DepositType second = module.sampleDepositType(-992, 208, 1001L);
        assertEquals(first, second);
    }

    @Test
    void nearbyDepositQueryIsDeterministic() {
        boolean a = module.hasDeposit(0, 0, 77L, 2);
        boolean b = module.hasDeposit(0, 0, 77L, 2);
        assertEquals(a, b);
    }
}