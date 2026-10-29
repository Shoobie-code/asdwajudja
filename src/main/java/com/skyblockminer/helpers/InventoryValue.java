package com.skyblockminer.helpers;

import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Totals the Bazaar instant-sell value of a set of items, for the "what is my inventory worth" readout. */
public final class InventoryValue {
    private InventoryValue() {
    }

    /**
     * The summed value of {@code counts} (display name to quantity) at the prices {@code price} returns per unit.
     * Items the price function values at 0 (not on the Bazaar) add nothing.
     */
    public static double total(Map<String, Integer> counts, ToDoubleFunction<String> price) {
        double sum = 0.0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            double each = price.applyAsDouble(entry.getKey());
            if (each > 0.0) {
                sum += each * entry.getValue();
            }
        }
        return sum;
    }
}
