package com.skyblockminer.macro;

import java.util.Locale;

/** Turns a terse macro-stop reason into a short recovery hint, so a user knows what to do next. */
final class StopHints {
    private StopHints() {
    }

    /** A follow-up sentence for a known stop reason, or null when none applies. Matching is on keywords, case-insensitive. */
    static String hintFor(String reason) {
        if (reason == null) {
            return null;
        }
        String r = reason.toLowerCase(Locale.ROOT);
        if (r.contains("inventory is full")) {
            return "Turn on Auto Sell (Misc) or clear some space, then restart.";
        }
        if (r.contains("out of fuel")) {
            return "Refuel your drill, then restart the macro.";
        }
        if (r.contains("tool broke")) {
            return "Repair or replace the tool in your hotbar, then restart.";
        }
        if (r.contains("limbo")) {
            return "You were sent to Limbo. Rejoin SkyBlock; turn on Auto rejoin to do this automatically.";
        }
        if (r.contains("get back to skyblock")) {
            return "The server did not let you back in. Check your connection and rejoin manually.";
        }
        if (r.contains("heat reached")) {
            return "Let your heat cool down before mining in the Crystal Hollows again.";
        }
        if (r.contains("cold reached")) {
            return "Warm up before returning to the Glacite Tunnels.";
        }
        if (r.startsWith("failsafe")) {
            return "A failsafe triggered. Check what happened before restarting; someone may be watching.";
        }
        return null;
    }
}
