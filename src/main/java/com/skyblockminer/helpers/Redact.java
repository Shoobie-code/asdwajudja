package com.skyblockminer.helpers;

import java.util.regex.Pattern;

/** Removes secrets from text before it is shared (for example, an exported macro log for a bug report). */
public final class Redact {
    // Discord webhook URLs carry a token in the path; replace the whole URL.
    private static final Pattern WEBHOOK = Pattern.compile(
        "https://(?:ptb\\.|canary\\.)?discord(?:app)?\\.com/api/webhooks/\\S+");

    private Redact() {
    }

    /** Returns {@code text} with Discord webhook URLs replaced by a placeholder. Null in, null out. */
    public static String scrub(String text) {
        if (text == null) {
            return null;
        }
        return WEBHOOK.matcher(text).replaceAll("<webhook removed>");
    }
}
