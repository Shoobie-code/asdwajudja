package com.skyblockminer.macro;

/**
 * How many times auto rejoin sends {@code /skyblock} before giving up. Without a cap, a server that never comes back
 * (a ban, or a long outage) keeps the macro waiting in the rejoining state until someone presses stop.
 */
final class RejoinPolicy {
    /** Sends, 15 s apart: about two minutes of trying. */
    static final int MAX_SKYBLOCK_TRIES = 8;

    private RejoinPolicy() {
    }

    /** True once {@code tries} sends have already gone out and none got back to SkyBlock. */
    static boolean giveUp(int tries) {
        return tries >= MAX_SKYBLOCK_TRIES;
    }
}
