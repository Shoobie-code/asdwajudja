package com.skyblockminer.macro.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.config.MinerConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class MiningTest {
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
}
