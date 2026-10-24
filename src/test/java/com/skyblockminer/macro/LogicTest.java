package com.skyblockminer.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogicTest {
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
        assertSame(MacroType.COMBAT.next(), MacroType.WYNN_COMBAT);
        assertSame(MacroType.WYNN_COMBAT.next(), MacroType.SCRIPT);
        assertSame(MacroType.SCRIPT.next(), MacroType.MITHRIL);
        assertSame(MacroType.MITHRIL.previous(), MacroType.SCRIPT);
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
    void chatMentionsDetectWatchWords() {
        assertTrue(Failsafes.chatMention("[MVP+] Someone: is that a macro?", "Me").contains("macro"));
        assertTrue(Failsafes.chatMention("From [VIP] Friend: hi", "Me").startsWith("Private message"));
        assertNull(Failsafes.chatMention("[NPC] Emissary: hello", "Me"));
        assertNull(Failsafes.chatMention("[VIP] Me: macro test", "Me"), "our own messages are ignored");
    }
}
