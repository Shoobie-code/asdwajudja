package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AccessoriesTest {
    @Test
    void findsNamesThatAppearMoreThanOnce() {
        List<String> dupes = Accessories.duplicates(List.of(
            "Red Talisman", "Blue Talisman", "Red Talisman", "Green Ring", "Red Talisman", "Blue Talisman"));
        assertEquals(List.of("Red Talisman", "Blue Talisman"), dupes);
    }

    @Test
    void noneWhenAllUnique() {
        assertTrue(Accessories.duplicates(List.of("A", "B", "C")).isEmpty());
    }

    @Test
    void ignoresBlankNames() {
        assertTrue(Accessories.duplicates(List.of("", "  ", "")).isEmpty());
    }
}
