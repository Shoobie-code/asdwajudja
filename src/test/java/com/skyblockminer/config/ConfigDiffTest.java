package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigDiffTest {
    @Test
    void identicalConfigsHaveNoDiff() {
        assertTrue(MinerConfig.diff(new MinerConfig(), new MinerConfig()).isEmpty());
    }

    @Test
    void reportsChangedFieldsSortedByName() {
        MinerConfig b = new MinerConfig();
        b.reach = 3.0;
        b.weaponSlot = 7;
        List<String> lines = MinerConfig.diff(new MinerConfig(), b);
        assertEquals(2, lines.size());
        // Sorted by field name: reach before weaponSlot.
        assertTrue(lines.get(0).startsWith("reach: "), lines.toString());
        assertTrue(lines.get(0).contains("-> 3.0"), lines.toString());
        assertTrue(lines.get(1).startsWith("weaponSlot: "), lines.toString());
    }

    @Test
    void webhookChangesAreNotShown() {
        MinerConfig b = new MinerConfig();
        b.webhookUrl = "https://discord.com/api/webhooks/1/secret";
        assertTrue(MinerConfig.diff(new MinerConfig(), b).isEmpty());
    }
}
