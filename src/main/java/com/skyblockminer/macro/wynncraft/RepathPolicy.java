package com.skyblockminer.macro.wynncraft;

/**
 * When a chasing macro plans a new path to its target. Ported from V5's CombatBot (REPATH_DELAY_MS = 1200 and
 * REPATH_DISTANCE = 7): plan at most once per delay, and while following a path only re-plan when the target has moved
 * far enough from where that path was aimed. Without this, a macro that is not walking re-plans every tick, and a
 * standing mob is re-planned every 1.5 s.
 */
final class RepathPolicy {
    static final long DELAY_MS = 1200L;
    static final double DISTANCE = 7.0;

    private RepathPolicy() {
    }

    /**
     * @param walking whether a path is being followed right now
     * @param sincePlanMs milliseconds since the last path was planned
     * @param movedSincePlan how far the target is from the point the last path was aimed at
     */
    static boolean shouldRepath(boolean walking, long sincePlanMs, double movedSincePlan) {
        if (sincePlanMs < DELAY_MS) {
            return false;
        }
        return !walking || movedSincePlan >= DISTANCE;
    }
}
