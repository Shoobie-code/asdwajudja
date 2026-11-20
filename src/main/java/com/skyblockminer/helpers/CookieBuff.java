package com.skyblockminer.helpers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the Booster Cookie time remaining out of the SkyBlock menu lore, for the cookie-timer readout. */
public final class CookieBuff {
    // A cookie-buff duration in lore reads like "5d 3h 20m" or "23h 59m 10s".
    private static final Pattern DURATION = Pattern.compile("(?:\\d+[dhms]\\s*){1,4}");

    private CookieBuff() {
    }

    /** The time-left duration string from a lore block that mentions the cookie buff, or null when absent. */
    public static String timeLeft(List<String> lore) {
        if (lore == null) {
            return null;
        }
        boolean cookieContext = false;
        for (String line : lore) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("cookie buff") || lower.contains("booster cookie")) {
                cookieContext = true;
            }
            if (cookieContext) {
                Matcher matcher = DURATION.matcher(line);
                if (matcher.find()) {
                    String found = matcher.group().trim();
                    // Guard against matching a single stray token like a stack count.
                    if (found.matches(".*\\d+[dh].*")) {
                        return found;
                    }
                }
            }
        }
        return null;
    }
}
