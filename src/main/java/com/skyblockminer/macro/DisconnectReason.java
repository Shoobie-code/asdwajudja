package com.skyblockminer.macro;

import java.util.Locale;

/**
 * Tells a ban disconnect from an ordinary one. A banned account cannot rejoin, so auto rejoin must stop instead of
 * retrying. Idea from V5's failsafes (the ban screen text and the appeal link); the matching is written here.
 */
public final class DisconnectReason {
    private static final String APPEAL_LINK = "hypixel.net/appeal";

    private DisconnectReason() {
    }

    /** True when a disconnect message reads like a ban, which means rejoining will fail again. */
    public static boolean isBan(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains(APPEAL_LINK)
            || lower.contains("banned") && !lower.contains("not banned")
            || lower.contains("cheating") || lower.contains("boosting");
    }
}
