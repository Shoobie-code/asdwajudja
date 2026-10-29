package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class InventoryValueTest {
    private static final ToDoubleFunction<String> PRICES = name -> switch (name) {
        case "Enchanted Mithril" -> 100.0;
        case "Mithril" -> 2.0;
        default -> 0.0;
    };

    @Test
    void sumsCountTimesPrice() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Enchanted Mithril", 3);
        counts.put("Mithril", 64);
        assertEquals(3 * 100.0 + 64 * 2.0, InventoryValue.total(counts, PRICES), 1e-9);
    }

    @Test
    void itemsWithNoPriceAddNothing() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Random Junk", 10);
        counts.put("Mithril", 5);
        assertEquals(10.0, InventoryValue.total(counts, PRICES), 1e-9);
    }

    @Test
    void emptyInventoryIsZero() {
        assertEquals(0.0, InventoryValue.total(new LinkedHashMap<>(), PRICES), 1e-9);
    }
}
