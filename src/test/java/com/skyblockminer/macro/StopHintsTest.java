package com.skyblockminer.macro;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StopHintsTest {
    @Test
    void givesHintsForKnownReasons() {
        assertTrue(StopHints.hintFor("Your inventory is full").toLowerCase().contains("auto sell"));
        assertNotNull(StopHints.hintFor("Your drill is out of fuel"));
        assertNotNull(StopHints.hintFor("Your tool broke"));
        assertNotNull(StopHints.hintFor("Sent to Limbo"));
        assertNotNull(StopHints.hintFor("FAILSAFE: forced rotation"));
        assertNotNull(StopHints.hintFor("Heat reached 90: warped to the Forge"));
    }

    @Test
    void noHintForUnknownOrNull() {
        assertNull(StopHints.hintFor("Stopped"));
        assertNull(StopHints.hintFor("Switching to Mithril"));
        assertNull(StopHints.hintFor(null));
    }
}
