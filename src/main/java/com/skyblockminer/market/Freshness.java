package com.skyblockminer.market;

/** Decides whether market data is too old to trust for a flip, so the screen can say so. */
public final class Freshness {
    /** The bazaar is polled often; older than this, prices may have moved. */
    public static final long BAZAAR_STALE_MS = 2L * 60_000L;
    /** Auction pages take longer to scan, so they get a longer limit. */
    public static final long AUCTION_STALE_MS = 10L * 60_000L;

    private Freshness() {
    }

    /** True when data stamped {@code updatedAt} is older than {@code limitMs}. Data with no timestamp is always stale. */
    public static boolean stale(long updatedAt, long now, long limitMs) {
        return updatedAt <= 0L || now - updatedAt > limitMs;
    }
}
