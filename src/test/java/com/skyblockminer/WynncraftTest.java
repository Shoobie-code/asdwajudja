package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WynncraftTest {
    // Spacers seen around the health and mana text (negative space U+DAFF.., positive space U+DB00..).
    private static final String NEG = "󏿰";
    private static final String POS = "󐀐";

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

    @Test
    void readsHealthAndManaGlyphs() {
        String bar = "x" + NEG + WynnActionBar.encode("1234/2500") + POS + "  " + POS + WynnActionBar.encode("87/120") + NEG + "y";
        assertEquals(new WynnActionBar.Bar(1234, 2500), WynnActionBar.health(bar));
        assertEquals(new WynnActionBar.Bar(87, 120), WynnActionBar.mana(bar));
        assertEquals(49, WynnActionBar.health(bar).percent());
    }

    @Test
    void healthAndManaCanShareASpacer() {
        String bar = NEG + WynnActionBar.encode("12k/15k") + POS + WynnActionBar.encode("100/150") + NEG;
        assertEquals(new WynnActionBar.Bar(12_000, 15_000), WynnActionBar.health(bar));
        assertEquals(new WynnActionBar.Bar(100, 150), WynnActionBar.mana(bar));
    }

    @Test
    void ignoresOtherActionBars() {
        assertNull(WynnActionBar.health("+12 Mining (1,234/5,000)"));
        assertNull(WynnActionBar.mana(null));
        assertEquals(1_500_000L, WynnActionBar.number("1.5m"));
    }

    @Test
    void readsSpellClicks() {
        String gap = "  ";
        String edge = "󏿠";
        assertEquals("RL", WynnActionBar.spellInputs(edge + "" + gap + "" + gap + "" + edge));
        assertEquals("", WynnActionBar.spellInputs(edge + "" + gap + "" + gap + "" + edge));
        assertNull(WynnActionBar.spellInputs("nothing here"));
    }

    @Test
    void spellCombos() {
        WynnSpells.Click l = WynnSpells.Click.LEFT;
        WynnSpells.Click r = WynnSpells.Click.RIGHT;
        assertEquals(List.of(r, l, r), WynnSpells.combo(1, WynnSpells.WynnClass.MAGE));
        assertEquals(List.of(r, r, l), WynnSpells.combo(4, WynnSpells.WynnClass.WARRIOR));
        assertEquals(List.of(l, r, l), WynnSpells.combo(1, WynnSpells.WynnClass.ARCHER));
        assertEquals(List.of(), WynnSpells.combo(5, WynnSpells.WynnClass.MAGE));
        assertEquals(r, WynnSpells.WynnClass.ARCHER.attack());
        assertEquals(l, WynnSpells.WynnClass.SHAMAN.attack());
        assertEquals(List.of(1, 3, 3), WynnSpells.rotation(" 1, 3 3, 7, x"));
        assertTrue(WynnSpells.rotation("").isEmpty());
        assertEquals(WynnSpells.WynnClass.ARCHER, WynnSpells.WynnClass.parse(" Archer "));
    }

    @Test
    void mobLabels() {
        assertEquals("Timber Wolf", WynnCombatMacro.mobName("§cTimber Wolf\n§4❤ 120"));
        // NPC labels start with an icon on its own line; damage popups start with a sign.
        assertNull(WynnCombatMacro.mobName("§f\n§cTasim\n§7Merchant"));
        assertNull(WynnCombatMacro.mobName("-120 ❤"));
        assertTrue(WynnCombatMacro.matches("Timber Wolf", List.of()));
        assertTrue(WynnCombatMacro.matches("Timber Wolf", List.of("wolf")));
        assertFalse(WynnCombatMacro.matches("Timber Wolf", List.of("Zombie")));
    }
}
