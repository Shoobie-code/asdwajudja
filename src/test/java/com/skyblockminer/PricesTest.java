package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PricesTest {
    @Test
    void mapsDisplayNamesToProductIds() {
        assertEquals("ENCHANTED_MITHRIL", Prices.productId("Enchanted Mithril"));
        assertEquals("MITHRIL_ORE", Prices.productId("Mithril"));
        assertEquals("FLAWED_RUBY_GEM", Prices.productId("Flawed Ruby Gemstone"));
        assertEquals("NETHER_STALK", Prices.productId(" Nether Wart "));
    }

    @Test
    void parsesBazaarJson() {
        Prices prices = new Prices();
        assertFalse(prices.ready());
        prices.parse("{\"success\":true,\"products\":{\"MITHRIL_ORE\":{\"quick_status\":{\"sellPrice\":2.5}},\"BROKEN\":{}}}");
        assertTrue(prices.ready());
        assertEquals(2.5, prices.price("Mithril"));
        assertEquals(0.0, prices.price("Unknown Item"));
    }
}
