package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AliasTest {
    @Test
    void acceptsSimpleNames() {
        assertTrue(MinerConfig.validAlias("go"));
        assertTrue(MinerConfig.validAlias("fast-mine_1"));
    }

    @Test
    void rejectsNamesThatCouldInjectArguments() {
        assertFalse(MinerConfig.validAlias("has space"));
        assertFalse(MinerConfig.validAlias(""));
        assertFalse(MinerConfig.validAlias(null));
        assertFalse(MinerConfig.validAlias("way-too-long-alias-name-that-exceeds"));
    }
}
