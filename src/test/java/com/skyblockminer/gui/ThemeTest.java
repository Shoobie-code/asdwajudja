package com.skyblockminer.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ThemeTest {
    @Test
    void colorBlindPaletteDiffersFromDefault() {
        assertNotEquals(Theme.good(false), Theme.good(true));
        assertNotEquals(Theme.bad(false), Theme.bad(true));
        assertEquals(Theme.GOOD, Theme.good(false));
        assertEquals(Theme.CB_BAD, Theme.bad(true));
    }

    @Test
    void colorBlindStatusColoursAreDistinct() {
        assertNotEquals(Theme.good(true), Theme.warn(true));
        assertNotEquals(Theme.warn(true), Theme.bad(true));
        assertNotEquals(Theme.good(true), Theme.bad(true));
    }

    @Test
    void flagSelectsThePalette() {
        boolean saved = Theme.colorBlind;
        try {
            Theme.colorBlind = true;
            assertEquals(Theme.CB_GOOD, Theme.good());
            Theme.colorBlind = false;
            assertEquals(Theme.GOOD, Theme.good());
        } finally {
            Theme.colorBlind = saved;
        }
    }
}
