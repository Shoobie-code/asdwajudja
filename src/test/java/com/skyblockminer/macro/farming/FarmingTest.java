package com.skyblockminer.macro.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.config.MinerConfig;
import com.skyblockminer.macro.Keys;
import org.junit.jupiter.api.Test;

class FarmingTest {
    @Test
    void yawSnapsToNearest45WhenAsked() {
        MinerConfig config = new MinerConfig();
        config.farmKeepYaw = true;
        config.farmSnapYaw = true;
        assertEquals(45.0F, FarmingMacro.startYaw(52.0F, config));
        assertEquals(-90.0F, FarmingMacro.startYaw(-100.0F, config));
        config.farmSnapYaw = false;
        assertEquals(52.0F, FarmingMacro.startYaw(52.0F, config));
        config.farmKeepYaw = false;
        config.farmYaw = 12.5;
        assertEquals(12.5F, FarmingMacro.startYaw(52.0F, config));
    }

    @Test
    void farmPatternsParse() {
        assertSame(FarmingMacro.Pattern.COCOA, FarmingMacro.Pattern.parse("Cocoa"));
        assertNull(FarmingMacro.Pattern.parse("wheat"));
        for (FarmingMacro.Pattern pattern : FarmingMacro.Pattern.ALL) {
            if (pattern != FarmingMacro.Pattern.CUSTOM && pattern != FarmingMacro.Pattern.ECHO) {
                assertTrue(Keys.valid(pattern.left) && Keys.valid(pattern.right), pattern.id);
            }
        }
    }
}
