package com.skyblockminer.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RejoinPolicyTest {
    @Test
    void backoffGrowsThenCaps() {
        assertEquals(RejoinPolicy.BASE_DELAY_MS, RejoinPolicy.backoffMs(0));
        assertEquals(RejoinPolicy.BASE_DELAY_MS + RejoinPolicy.STEP_MS, RejoinPolicy.backoffMs(1));
        assertTrue(RejoinPolicy.backoffMs(1) > RejoinPolicy.backoffMs(0));
        assertEquals(RejoinPolicy.MAX_DELAY_MS, RejoinPolicy.backoffMs(1000));
    }

    @Test
    void givesUpOnlyAfterTheLastTry() {
        assertFalse(RejoinPolicy.giveUp(0));
        assertFalse(RejoinPolicy.giveUp(RejoinPolicy.MAX_SKYBLOCK_TRIES - 1));
        assertTrue(RejoinPolicy.giveUp(RejoinPolicy.MAX_SKYBLOCK_TRIES));
    }
}
