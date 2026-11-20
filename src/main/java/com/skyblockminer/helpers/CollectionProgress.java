package com.skyblockminer.helpers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a collection (or similar) progress percentage out of an item's lore, for the collections readout. */
public final class CollectionProgress {
    // A progress line in SkyBlock lore shows a percentage, e.g. "Progress to Tier V: 45%" or a bar ending "45%".
    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)%");

    private CollectionProgress() {
    }

    /** The first percentage (0-100) found in the lore lines, or -1 when there is none. */
    public static int percent(List<String> lore) {
        if (lore == null) {
            return -1;
        }
        for (String line : lore) {
            Matcher matcher = PERCENT.matcher(line);
            if (matcher.find()) {
                int value = (int) Math.round(Double.parseDouble(matcher.group(1)));
                if (value >= 0 && value <= 100) {
                    return value;
                }
            }
        }
        return -1;
    }
}
