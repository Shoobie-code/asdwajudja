package com.skyblockminer.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SidebarPurseTest {
    @Test
    void parsesPurseWithCommas() {
        assertEquals(1_234_567L, Sidebar.purse(List.of("Location: Hub", "Purse: 1,234,567", "Bits: 0")));
    }

    @Test
    void parsesPiggyVariant() {
        assertEquals(500L, Sidebar.purse(List.of("Piggy: 500")));
    }

    @Test
    void minusOneWhenAbsent() {
        assertEquals(-1L, Sidebar.purse(List.of("Location: Hub", "Bits: 0")));
    }
}
