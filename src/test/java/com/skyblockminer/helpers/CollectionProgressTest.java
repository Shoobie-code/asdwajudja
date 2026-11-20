package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionProgressTest {
    @Test
    void readsPercentFromLore() {
        assertEquals(45, CollectionProgress.percent(List.of("Wheat Collection", "Progress to Tier V: 45%")));
        assertEquals(100, CollectionProgress.percent(List.of("Maxed out!", "100%")));
    }

    @Test
    void roundsDecimalPercent() {
        assertEquals(46, CollectionProgress.percent(List.of("Progress: 45.6%")));
    }

    @Test
    void minusOneWhenNoPercent() {
        assertEquals(-1, CollectionProgress.percent(List.of("Click to view", "No numbers here")));
        assertEquals(-1, CollectionProgress.percent(null));
    }

    @Test
    void ignoresOutOfRangeNumbers() {
        assertEquals(-1, CollectionProgress.percent(List.of("Deals 250% damage")));
    }
}
