package com.skyblockminer.helpers;

/**
 * Estimates server ticks-per-second from how fast the world's game time advances against real time. The client always
 * runs its own tick loop, so server lag only shows as the game time falling behind; comparing the two reveals it.
 */
public final class TpsEstimator {
    private static final double FULL_TPS = 20.0;

    private long lastGameTime = -1;
    private long lastRealMs;
    private double tps = FULL_TPS;
    private boolean ready;

    public void reset() {
        this.lastGameTime = -1;
        this.tps = FULL_TPS;
        this.ready = false;
    }

    /**
     * Feeds a sample of the world game time (in ticks) at real time {@code realMs}. Gaps larger than 5 s (a pause,
     * a warp) are skipped so they do not look like lag.
     */
    public void update(long gameTime, long realMs) {
        if (this.lastGameTime >= 0) {
            double dtReal = (realMs - this.lastRealMs) / 1000.0;
            long dtGame = gameTime - this.lastGameTime;
            if (dtReal > 0.0 && dtReal <= 5.0 && dtGame >= 0) {
                double instant = Math.max(0.0, Math.min(FULL_TPS, dtGame / dtReal));
                this.tps = this.ready ? this.tps * 0.8 + instant * 0.2 : instant;
                this.ready = true;
            }
        }
        this.lastGameTime = gameTime;
        this.lastRealMs = realMs;
    }

    public boolean ready() {
        return this.ready;
    }

    public double tps() {
        return this.tps;
    }

    /** True when a reading exists and the server is running below {@code threshold} TPS. */
    public boolean laggy(double threshold) {
        return this.ready && this.tps < threshold;
    }
}
