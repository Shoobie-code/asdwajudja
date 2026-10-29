package com.skyblockminer.helpers;

import java.util.List;

/** Shared helpers for the string-list settings (alert phrases, watched prices, schedule, ...), to avoid repeating the
 * same add/trim/dedup logic in every command. */
public final class ConfigLists {
    private ConfigLists() {
    }

    /** Trims {@code item} and adds it to {@code list} unless it is blank or already present. Returns true if added. */
    public static boolean addUnique(List<String> list, String item) {
        if (item == null) {
            return false;
        }
        String trimmed = item.trim();
        if (trimmed.isEmpty() || list.contains(trimmed)) {
            return false;
        }
        list.add(trimmed);
        return true;
    }
}
