package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionNoticeTest {
    @Test
    void showsOnceForANewVersion() {
        assertTrue(MinerConfig.isNewVersion("2.2.0", "2.1.0"));
        assertTrue(MinerConfig.isNewVersion("2.1.0", ""));
    }

    @Test
    void silentWhenUnchangedOrUnknown() {
        assertFalse(MinerConfig.isNewVersion("2.1.0", "2.1.0"));
        assertFalse(MinerConfig.isNewVersion("", "2.1.0"));
        assertFalse(MinerConfig.isNewVersion(null, "2.1.0"));
        assertFalse(MinerConfig.isNewVersion("   ", "2.1.0"));
    }
}
