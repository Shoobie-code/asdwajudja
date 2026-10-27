package com.skyblockminer.macro;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RejoinPolicyTest {
    @Test
    void givesUpOnlyAfterTheLastTry() {
        assertFalse(RejoinPolicy.giveUp(0));
        assertFalse(RejoinPolicy.giveUp(RejoinPolicy.MAX_SKYBLOCK_TRIES - 1));
        assertTrue(RejoinPolicy.giveUp(RejoinPolicy.MAX_SKYBLOCK_TRIES));
    }
}
