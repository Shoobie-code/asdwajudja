package com.skyblockminer.helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helps with the Accessory Bag: spotting duplicate talismans, which can be sold or combined. */
public final class Accessories {
    private Accessories() {
    }

    /**
     * The names that appear more than once in {@code names}, each listed once, in first-seen order. Blank names are
     * ignored. A duplicate talisman is wasted bag space, so this flags them for the user.
     */
    public static List<String> duplicates(List<String> names) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        List<String> dupes = new ArrayList<>();
        counts.forEach((name, count) -> {
            if (count > 1) {
                dupes.add(name);
            }
        });
        return dupes;
    }
}
