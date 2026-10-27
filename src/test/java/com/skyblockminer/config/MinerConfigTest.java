package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MinerConfigTest {
    @Test
    void clampsOutOfRangeValues() {
        MinerConfig config = new MinerConfig();
        config.weaponSlot = 42;
        config.breakLength = -5;
        config.breakEvery = -1;
        config.powderRadius = 0;
        config.fishTimeout = 0;
        config.reach = 99.0;
        config.wynnSpellSeconds = 0.0;
        config.sanitize();

        assertEquals(8, config.weaponSlot);
        assertEquals(1, config.breakLength);
        assertEquals(0, config.breakEvery);
        assertEquals(1, config.powderRadius);
        assertEquals(1, config.fishTimeout);
        assertEquals(6.0, config.reach, 1e-9);
        assertEquals(0.5, config.wynnSpellSeconds, 1e-9);
    }

    @Test
    void replacesNanWithSafeValues() {
        MinerConfig config = new MinerConfig();
        config.reach = Double.NaN;
        config.etherwarpRange = Double.NaN;
        config.bazaarBudget = Double.NaN;
        config.bazaarMinMargin = Double.NaN;
        config.sanitize();

        assertEquals(1.0, config.reach, 1e-9);
        assertEquals(10.0, config.etherwarpRange, 1e-9);
        assertEquals(new MinerConfig().bazaarBudget, config.bazaarBudget, 1e-9);
        assertEquals(new MinerConfig().bazaarMinMargin, config.bazaarMinMargin, 1e-9);
    }

    @Test
    void negativeMoneyLimitsBecomeZero() {
        MinerConfig config = new MinerConfig();
        config.auctionMinProfit = -1;
        config.craftMinProfit = -100;
        config.npcBudget = -5.0;
        config.sanitize();

        assertEquals(0, config.auctionMinProfit);
        assertEquals(0, config.craftMinProfit);
        assertEquals(0.0, config.npcBudget, 1e-9);
    }

    @Test
    void leavesValidValuesAlone() {
        MinerConfig config = new MinerConfig();
        config.weaponSlot = 4;
        config.breakEvery = 600;
        config.reach = 4.5;
        config.sanitize();

        assertEquals(4, config.weaponSlot);
        assertEquals(600, config.breakEvery);
        assertEquals(4.5, config.reach, 1e-9);
    }

    @Test
    void defaultsSurviveSanitize() {
        MinerConfig config = new MinerConfig();
        config.sanitize();

        assertNull(config.farmRewarp);
        assertEquals(new MinerConfig().reach, config.reach, 1e-9);
        assertEquals(new MinerConfig().weaponSlot, config.weaponSlot);
    }
}
