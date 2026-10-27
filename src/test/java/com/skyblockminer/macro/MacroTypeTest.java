package com.skyblockminer.macro;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MacroTypeTest {
    @Test
    void flagsOnlyTheMacrosNotYetTestedInGame() {
        assertTrue(MacroType.GLACITE.untested());
        assertTrue(MacroType.EXCAVATOR.untested());
        assertFalse(MacroType.MITHRIL.untested());
        assertFalse(MacroType.COMBAT.untested());
    }
}
