package com.skyblockminer.macro.wynncraft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RepathPolicyTest {
    @Test
    void waitsForTheDelayBeforeAnyReplan() {
        assertFalse(RepathPolicy.shouldRepath(false, RepathPolicy.DELAY_MS - 1, 100.0));
        assertFalse(RepathPolicy.shouldRepath(true, 0, 100.0));
    }

    @Test
    void planWhenNotWalkingOnceTheDelayHasPassed() {
        assertTrue(RepathPolicy.shouldRepath(false, RepathPolicy.DELAY_MS, 0.0));
    }

    @Test
    void whileWalkingOnlyMovedTargetsTriggerAReplan() {
        assertFalse(RepathPolicy.shouldRepath(true, 5000L, RepathPolicy.DISTANCE - 0.1));
        assertTrue(RepathPolicy.shouldRepath(true, 5000L, RepathPolicy.DISTANCE));
    }
}
