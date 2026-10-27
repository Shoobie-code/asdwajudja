package com.skyblockminer.market;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FreshnessTest {
    @Test
    void staleAfterTheLimit() {
        long now = 10_000_000L;
        assertFalse(Freshness.stale(now - 1_000L, now, Freshness.BAZAAR_STALE_MS));
        assertFalse(Freshness.stale(now - Freshness.BAZAAR_STALE_MS, now, Freshness.BAZAAR_STALE_MS));
        assertTrue(Freshness.stale(now - Freshness.BAZAAR_STALE_MS - 1L, now, Freshness.BAZAAR_STALE_MS));
    }

    @Test
    void missingTimestampIsStale() {
        assertTrue(Freshness.stale(0L, 10_000_000L, Freshness.AUCTION_STALE_MS));
    }
}
