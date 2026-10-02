package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LogicTest {
    @Test
    void yawSnapsToNearest45WhenAsked() {
        MinerConfig config = new MinerConfig();
        config.farmKeepYaw = true;
        config.farmSnapYaw = true;
        assertEquals(45.0F, FarmingMacro.startYaw(52.0F, config));
        assertEquals(-90.0F, FarmingMacro.startYaw(-100.0F, config));
        config.farmSnapYaw = false;
        assertEquals(52.0F, FarmingMacro.startYaw(52.0F, config));
        config.farmKeepYaw = false;
        config.farmYaw = 12.5;
        assertEquals(12.5F, FarmingMacro.startYaw(52.0F, config));
    }

    @Test
    void farmPatternsParse() {
        assertSame(FarmingMacro.Pattern.COCOA, FarmingMacro.Pattern.parse("Cocoa"));
        assertNull(FarmingMacro.Pattern.parse("wheat"));
        for (FarmingMacro.Pattern pattern : FarmingMacro.Pattern.ALL) {
            if (pattern != FarmingMacro.Pattern.CUSTOM) {
                assertTrue(Keys.valid(pattern.left) && Keys.valid(pattern.right), pattern.id);
            }
        }
    }

    @Test
    void laneKeySpecsAreValidated() {
        assertTrue(Keys.valid("A+W"));
        assertTrue(Keys.valid("d"));
        assertFalse(Keys.valid(""));
        assertFalse(Keys.valid("+"));
        assertFalse(Keys.valid("A+Q"));
        assertFalse(Keys.valid(null));
    }

    @Test
    void macroTypesCycleWithinSelectable() {
        assertSame(MacroType.BUILDER.next(), MacroType.MITHRIL);
        assertSame(MacroType.MITHRIL.previous(), MacroType.BUILDER);
        assertFalse(MacroType.SELECTABLE.contains(MacroType.GOTO));
        assertNull(MacroType.parse("goto"));
        assertSame(MacroType.FARMING, MacroType.parse(" Farming "));
    }

    @Test
    void sackMessagesAreCounted() {
        ItemTracker tracker = new ItemTracker();
        tracker.onChat("[Sacks] +1,234 items. (Last 30s.)");
        tracker.onChat("[Sacks] +1 item, -5 items. (Last 30s.)");
        tracker.onChat("Some other message +99 items");
        assertEquals(1235L, tracker.sacks());
    }

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
    void chatMentionsDetectWatchWords() {
        assertTrue(Failsafes.chatMention("[MVP+] Someone: is that a macro?", "Me").contains("macro"));
        assertTrue(Failsafes.chatMention("From [VIP] Friend: hi", "Me").startsWith("Private message"));
        assertNull(Failsafes.chatMention("[NPC] Emissary: hello", "Me"));
        assertNull(Failsafes.chatMention("[VIP] Me: macro test", "Me"), "our own messages are ignored");
    }
}
