package com.skyblockminer.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class SidebarSlayerTest {
    @Test
    void parsesBossAndTier() {
        assertEquals("Revenant Horror V", Sidebar.slayer(List.of("Slayer Quest", "☠ Revenant Horror V", "Slay the boss!")));
        assertEquals("Voidgloom Seraph IV", Sidebar.slayer(List.of("Voidgloom Seraph IV", "Combat XP")));
    }

    @Test
    void nullWhenNotOnSlayer() {
        assertNull(Sidebar.slayer(List.of("Hub", "Purse: 5")));
    }
}
