package com.skyblockminer.helpers;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Recognises the current SkyBlock mayor from menu text (the election / calendar menus show the mayor's name). */
public final class Mayors {
    /** The known mayor candidates in the election rotation. */
    public static final Set<String> KNOWN = Set.of(
        "Aatrox", "Cole", "Diana", "Diaz", "Finnegan", "Foxy", "Marina", "Paul",
        "Barry", "Derpy", "Jerry", "Scorpius", "Dante");

    private Mayors() {
    }

    /** The first known mayor name that appears (as a whole word) in any of the lines, or null. */
    public static String find(List<String> lines) {
        if (lines == null) {
            return null;
        }
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String mayor : KNOWN) {
                if (lower.matches(".*\\b" + mayor.toLowerCase(Locale.ROOT) + "\\b.*")) {
                    return mayor;
                }
            }
        }
        return null;
    }
}
