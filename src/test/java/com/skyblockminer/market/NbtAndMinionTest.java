package com.skyblockminer.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NbtAndMinionTest {
    @Test
    void minionIdsSplitIntoTypeAndTier() {
        Object[] wheat = CraftCalc.minion("WHEAT_GENERATOR_5");
        assertEquals("WHEAT", wheat[0]);
        assertEquals(5, wheat[1]);
        assertNull(CraftCalc.minion("ENCHANTED_SUGAR"));
        assertNull(CraftCalc.minion("WHEAT_GENERATOR_"));
    }

    @Test
    void pathFollowsCompoundKeys() {
        Map<String, Object> root = Map.of("tag", Map.of("ExtraAttributes", Map.of("id", "ASPECT_OF_THE_END")));
        assertEquals("ASPECT_OF_THE_END", Nbt.path(root, "tag", "ExtraAttributes", "id"));
        assertNull(Nbt.path(root, "tag", "Missing", "id"));
        assertNull(Nbt.path(root, "tag", "ExtraAttributes", "id", "deeper"));
    }
}
