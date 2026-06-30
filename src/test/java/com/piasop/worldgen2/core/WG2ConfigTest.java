package com.piasop.worldgen2.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WG2ConfigTest {
    @Test
    void exposesDefaultValuesBeforeForgeLoadsConfig() {
        assertTrue(WG2Config.enabled);
        assertFalse(WG2Config.vanillaCompat);
        assertTrue(WG2Config.activeDimensions.contains("minecraft:overworld"));
    }

    @Test
    void returnsModuleDefaultsFromTypedAccessors() {
        assertEqualsDefault(96.0f, WG2Config.INSTANCE.getFloat("terrain.vertical_scale", 96.0f));
        assertEqualsDefault(4, WG2Config.INSTANCE.getInt("terrain.ridged_octaves", 4));
    }

    private static void assertEqualsDefault(float expected, float actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static void assertEqualsDefault(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
