package com.skyblockminer.macro.wynncraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WynnSpellsTest {
    @Test
    void parsesRotationSeparators() {
        assertEquals(List.of(1, 3), WynnSpells.rotation("1, 3"));
        assertEquals(List.of(3, 1, 1), WynnSpells.rotation("3 1 1"));
        assertEquals(List.of(2, 4), WynnSpells.rotation("R2L4"));
    }

    @Test
    void skipsOutOfRangeAndMultiDigit() {
        assertEquals(List.of(1, 4), WynnSpells.rotation("1 5 0 9 4"));
        assertTrue(WynnSpells.rotation("12 99").isEmpty());
        assertTrue(WynnSpells.rotation(null).isEmpty());
        assertTrue(WynnSpells.rotation("").isEmpty());
    }

    @Test
    void classParseIsCaseInsensitiveAndRejectsUnknown() {
        assertNull(WynnSpells.WynnClass.parse("not-a-class"));
    }
}
