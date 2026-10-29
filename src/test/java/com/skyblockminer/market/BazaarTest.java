package com.skyblockminer.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BazaarTest {
    private static Bazaar one(double buyOrder, double sellOffer, long buyWeek, long sellWeek) {
        Bazaar.Product p = new Bazaar.Product("X", buyOrder, sellOffer, buyWeek, sellWeek, 1, 1);
        return new Bazaar(Map.of("X", p), 0L);
    }

    @Test
    void instaPricesUseTheRightSides() {
        Bazaar b = one(100.0, 150.0, 0, 0);
        assertEquals(150.0, b.instaBuy("X"), 1e-9);  // you pay the lowest sell offer
        assertEquals(100.0, b.instaSell("X"), 1e-9); // you receive the highest buy order
    }

    @Test
    void unknownOrOneSidedItemsAreNaN() {
        assertTrue(Double.isNaN(one(100.0, 150.0, 0, 0).instaBuy("NOPE")));
        assertTrue(Double.isNaN(one(0.0, 150.0, 0, 0).instaSell("X"))); // no buy orders
    }

    @Test
    void dailyVolumeIsTheSmallerSideOverSeven() {
        assertEquals(1000.0, one(1, 1, 7000, 14000).get("X").dailyVolume(), 1e-9);
    }

    @Test
    void tradableNeedsBothSides() {
        assertTrue(one(1, 1, 0, 0).get("X").tradable());
        assertFalse(one(0, 1, 0, 0).get("X").tradable());
    }
}
