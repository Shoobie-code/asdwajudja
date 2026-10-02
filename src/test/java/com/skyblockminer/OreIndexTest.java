package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OreIndexTest {
    @Test
    void tracksEveryMiningTargetButPlainStone() {
        assertTrue(OreIndex.TRACKED.contains("minecraft:polished_diorite"));
        assertTrue(OreIndex.TRACKED.contains("minecraft:red_stained_glass_pane"));
        assertTrue(OreIndex.TRACKED.contains("minecraft:black_stained_glass"));
        assertTrue(OreIndex.TRACKED.contains("minecraft:packed_ice"));
        assertTrue(OreIndex.TRACKED.contains("minecraft:diamond_ore"));
        assertFalse(OreIndex.TRACKED.contains("minecraft:stone"), "stone is everywhere and would flood the index");
        assertEquals(OreIndex.TRACKED.size(), OreIndex.TRACKED.stream().distinct().count());
    }

    @Test
    void packKeepsTypeAndLocalPosition() {
        for (int y : new int[]{-64, -1, 0, 127, 319}) {
            int packed = OreIndex.pack(OreIndex.TRACKED.size() - 1, 31, y, -2);
            assertEquals(OreIndex.TRACKED.size() - 1, packed >>> 17);
            assertEquals(15, packed & 15, "x is stored relative to the chunk");
            assertEquals(14, packed >> 4 & 15, "z is stored relative to the chunk");
            assertEquals(y, (packed >> 8 & 511) - 64);
        }
    }
}
