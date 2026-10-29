package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoinTrackerTest {
    @Test
    void tracksNetGainAndRate() {
        CoinTracker t = new CoinTracker();
        t.update(1_000, 0L);
        t.update(1_000_000, 3_600_000L); // +999,000 over one hour
        assertTrue(t.ready());
        assertEquals(999_000L, t.gained());
        assertEquals(999_000.0, t.perHour(), 1.0);
    }

    @Test
    void ignoresMissingPurseReadings() {
        CoinTracker t = new CoinTracker();
        t.update(-1, 0L);
        assertFalse(t.ready());
        t.update(500, 1000L);
        assertTrue(t.ready());
        assertEquals(0L, t.gained());
    }

    @Test
    void rateZeroBeforeASecondPasses() {
        CoinTracker t = new CoinTracker();
        t.update(100, 0L);
        t.update(200, 500L);
        assertEquals(0.0, t.perHour(), 1e-9);
    }

    @Test
    void resetClearsBaseline() {
        CoinTracker t = new CoinTracker();
        t.update(100, 0L);
        t.reset();
        assertFalse(t.ready());
    }
}
