package com.skyblockminer.helpers;

/**
 * Tracks coins earned over a session from the sidebar purse, so the HUD can show a coins-per-hour rate. Only the net
 * change from the first reading is reported; purse readings of -1 (no sidebar line) are ignored.
 */
public final class CoinTracker {
    private long startPurse = -1;
    private long startTime;
    private long latestPurse = -1;
    private long latestTime;

    public void reset() {
        this.startPurse = -1;
        this.latestPurse = -1;
    }

    /** Feeds a purse reading; the first valid one sets the baseline. */
    public void update(long purse, long nowMs) {
        if (purse < 0) {
            return;
        }
        if (this.startPurse < 0) {
            this.startPurse = purse;
            this.startTime = nowMs;
        }
        this.latestPurse = purse;
        this.latestTime = nowMs;
    }

    public boolean ready() {
        return this.startPurse >= 0 && this.latestPurse >= 0;
    }

    /** Net coins since the baseline (can be negative if the player spent). */
    public long gained() {
        return this.ready() ? this.latestPurse - this.startPurse : 0;
    }

    /** Coins per hour since the baseline, or 0 before enough time has passed. */
    public double perHour() {
        if (!this.ready()) {
            return 0.0;
        }
        long elapsed = this.latestTime - this.startTime;
        if (elapsed < 1000L) {
            return 0.0;
        }
        return this.gained() * 3_600_000.0 / elapsed;
    }
}
