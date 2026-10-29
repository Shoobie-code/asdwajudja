package com.skyblockminer.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class SidebarLocationTest {
    @Test
    void parsesTheAreaGlyphLine() {
        assertEquals("Dwarven Mines", Sidebar.location(List.of("Purse: 10", "⏣ Dwarven Mines", "12/31/24")));
    }

    @Test
    void nullWhenNoLocationLine() {
        assertNull(Sidebar.location(List.of("Purse: 10", "Bits: 0")));
    }

    @Test
    void parsesDungeonClearedPercent() {
        assertEquals(45, Sidebar.dungeonCleared(List.of("The Catacombs", "Cleared: 45% (320)", "Deaths: 0")));
        assertEquals(-1, Sidebar.dungeonCleared(List.of("Hub", "Purse: 5")));
    }
}
