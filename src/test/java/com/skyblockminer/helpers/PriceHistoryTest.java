package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PriceHistoryTest {
    @Test
    void tracksLowHighAndLatest() {
        PriceHistory h = new PriceHistory(10);
        h.add("MITHRIL_ORE", 3.0, 2.0, 1);
        h.add("MITHRIL_ORE", 3.5, 5.0, 2);
        h.add("MITHRIL_ORE", 3.2, 3.0, 3);
        PriceHistory.Stats s = h.stats("MITHRIL_ORE");
        assertEquals(2.0, s.lowSell(), 1e-9);
        assertEquals(5.0, s.highSell(), 1e-9);
        assertEquals(3.0, s.latestSell(), 1e-9);
        assertEquals(3, s.samples());
    }

    @Test
    void capBoundsTheWindow() {
        PriceHistory h = new PriceHistory(3);
        for (int i = 1; i <= 6; i++) {
            h.add("X", i, i, i);
        }
        PriceHistory.Stats s = h.stats("X");
        assertEquals(3, s.samples());
        assertEquals(4.0, s.lowSell(), 1e-9); // oldest kept is the 4th
        assertEquals(6.0, s.latestSell(), 1e-9);
    }

    @Test
    void nullWhenUntracked() {
        assertNull(new PriceHistory(5).stats("NOPE"));
    }
}
