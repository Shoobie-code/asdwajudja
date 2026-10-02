package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MenuLogicTest {
    @Test
    void namesMatchWholeWords() {
        assertTrue(Inv.contains("Revenant Horror IV", "IV"));
        assertFalse(Inv.contains("Revenant Horror V", "IV"));
        assertFalse(Inv.contains("Tier II", "Tier I"));
        assertTrue(Inv.contains("» Accept Offer «", "accept offer"));
        assertFalse(Inv.contains("Anything", " "));
    }

    @Test
    void glaciteRulesPickBlocksOrMobs() {
        List<String> rules = new MinerConfig().glaciteRules;
        GlaciteCommissions.Rule slayer = GlaciteCommissions.ruleFor("Glacite Walker Slayer", rules);
        assertNotNull(slayer);
        assertEquals(List.of("Glacite Walker", "Ice Walker"), slayer.mobs());
        GlaciteCommissions.Rule glacite = GlaciteCommissions.ruleFor("Glacite Collector", rules);
        assertTrue(glacite.blocks().containsKey("minecraft:packed_ice"));
        assertTrue(GlaciteCommissions.ruleFor("Onyx Gemstone Collector", rules).blocks().containsKey("minecraft:black_stained_glass"));
        assertNull(GlaciteCommissions.ruleFor("Mineshaft Explorer", rules));
        assertNull(GlaciteCommissions.Rule.parse("broken"));
        assertNull(GlaciteCommissions.Rule.parse("Key=mob:"));
    }

    @Test
    void tabLinesForCommissionsAndVisitors() {
        List<String> lines = List.of("Area: Glacite Tunnels", "Glacite Collector: 45%", "Umber Collector: DONE", "Visitors: (3)");
        assertEquals(0.45, TabList.parseProgress(lines).get("Glacite Collector"), 1e-9);
        assertEquals(1.0, TabList.parseProgress(lines).get("Umber Collector"));
        assertEquals(3, TabList.visitors(lines));
        assertEquals(-1, TabList.visitors(List.of("Area: Garden")));
    }
}
