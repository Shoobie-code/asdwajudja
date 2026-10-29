package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SanitizeCollectionsTest {
    @Test
    void dropsMalformedCollectionEntries() {
        MinerConfig c = new MinerConfig();
        c.waypoints.put("ok", new int[]{1, 2, 3});
        c.waypoints.put("bad", new int[]{1, 2});
        c.waypoints.put("nul", null);
        c.hudPresets.put("ok", new int[10]);
        c.hudPresets.put("bad", new int[3]);
        c.alertWords.add("keep");
        c.alertWords.add("  ");
        c.watchedPrices.add("MITHRIL_ORE");
        c.watchedPrices.add("");

        c.sanitize();

        assertTrue(c.waypoints.containsKey("ok"));
        assertFalse(c.waypoints.containsKey("bad"));
        assertFalse(c.waypoints.containsKey("nul"));
        assertTrue(c.hudPresets.containsKey("ok"));
        assertFalse(c.hudPresets.containsKey("bad"));
        assertEquals(java.util.List.of("keep"), c.alertWords);
        assertEquals(java.util.List.of("MITHRIL_ORE"), c.watchedPrices);
    }

    @Test
    void keepsValidCollections() {
        MinerConfig c = new MinerConfig();
        c.aliases.put("go", "start mithril");
        c.sanitize();
        assertEquals("start mithril", c.aliases.get("go"));
    }
}
