package com.skyblockminer.macro;

/**
 * How many times auto rejoin sends {@code /skyblock} before giving up. Without a cap, a server that never comes back
 * (a ban, or a long outage) keeps the macro waiting in the rejoining state until someone presses stop.
 */
final class RejoinPolicy {
    static final int MAX_SKYBLOCK_TRIES = 8;
    static final long BASE_DELAY_MS = 10_000L;
    static final long STEP_MS = 5_000L;
    static final long MAX_DELAY_MS = 45_000L;

    private RejoinPolicy() {
    }

    /** True once {@code tries} sends have already gone out and none got back to SkyBlock. */
    static boolean giveUp(int tries) {
        return tries >= MAX_SKYBLOCK_TRIES;
    }

    /**
     * How long to wait before the next {@code /skyblock}, given how many have already gone out. The wait grows with
     * each try (back-off), so a server that is slow or briefly down is not spammed with reconnect attempts.
     */
    static long backoffMs(int triesAlreadySent) {
        long delay = BASE_DELAY_MS + Math.max(0, triesAlreadySent) * STEP_MS;
        return Math.min(delay, MAX_DELAY_MS);
    }
}
