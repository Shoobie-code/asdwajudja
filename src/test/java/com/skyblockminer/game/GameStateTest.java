package com.skyblockminer.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.macro.MacroType;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameStateTest {
    @Test
    void tabListPowderAndCommissionsParse() {
        assertEquals(12345L, TabList.parsePowder(List.of("Mithril: 12,345", "Gemstone: 7"), "Mithril"));
        assertEquals(-1L, TabList.parsePowder(List.of("Area: Dwarven Mines"), "Glacite"));
    }

    @Test
    void sidebarHeatAndCold() {
        assertEquals(42, Sidebar.heat(List.of("Heat: 42♨")));
        assertEquals(15, Sidebar.cold(List.of("Cold: -15❄")));
        assertEquals(-1, Sidebar.heat(List.of("Purse: 100")));
    }

    @Test
    void namesMatchWholeWords() {
        assertTrue(Inv.contains("Revenant Horror IV", "IV"));
        assertFalse(Inv.contains("Revenant Horror V", "IV"));
        assertFalse(Inv.contains("Tier II", "Tier I"));
        assertTrue(Inv.contains("» Accept Offer «", "accept offer"));
        assertFalse(Inv.contains("Anything", " "));
    }

    @Test
    void tabLinesForCommissionsAndVisitors() {
        List<String> lines = List.of("Area: Glacite Tunnels", "Glacite Collector: 45%", "Umber Collector: DONE", "Visitors: (3)");
        assertEquals(0.45, TabList.parseProgress(lines).get("Glacite Collector"), 1e-9);
        assertEquals(1.0, TabList.parseProgress(lines).get("Umber Collector"));
        assertEquals(3, TabList.visitors(lines));
        assertEquals(-1, TabList.visitors(List.of("Area: Garden")));
    }

    @Test
    void detectsServersFromHost() {
        assertEquals(Server.Kind.HYPIXEL, Server.fromHost("mc.hypixel.net"));
        assertEquals(Server.Kind.HYPIXEL, Server.fromHost("Hypixel.net:25565"));
        assertEquals(Server.Kind.WYNNCRAFT, Server.fromHost("play.wynncraft.com"));
        assertEquals(Server.Kind.WYNNCRAFT, Server.fromHost("wynncraft.net."));
        assertEquals(Server.Kind.WYNNCRAFT, Server.fromHost("beta.wynncraft.org"));
        assertEquals(Server.Kind.OTHER, Server.fromHost("notwynncraft.com"));
        assertEquals(Server.Kind.OTHER, Server.fromHost("hypixel.net.evil.com"));
        assertEquals(Server.Kind.OTHER, Server.fromHost("localhost"));
        assertEquals(Server.Kind.OTHER, Server.fromHost(null));
    }

    @Test
    void macrosOnlyStartOnTheirServer() {
        assertNull(Server.mismatch(MacroType.MITHRIL, Server.Kind.HYPIXEL));
        assertNull(Server.mismatch(MacroType.WYNN_COMBAT, Server.Kind.WYNNCRAFT));
        assertNotNull(Server.mismatch(MacroType.MITHRIL, Server.Kind.WYNNCRAFT));
        assertNotNull(Server.mismatch(MacroType.WYNN_COMBAT, Server.Kind.HYPIXEL));
        // Unknown servers and plain walking are never blocked.
        assertNull(Server.mismatch(MacroType.WYNN_COMBAT, Server.Kind.OTHER));
        assertNull(Server.mismatch(MacroType.GOTO, Server.Kind.WYNNCRAFT));
    }

    @Test
    void readsTheWynncraftWorld() {
        assertEquals("WC12", Server.wynnWorld(List.of("Friends", "  Global [WC12]", "Party")));
        assertNull(Server.wynnWorld(List.of("Area: Dwarven Mines")));
    }
}
