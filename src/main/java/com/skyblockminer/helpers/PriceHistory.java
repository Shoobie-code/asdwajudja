package com.skyblockminer.helpers;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps a short rolling history of Bazaar prices for watched items, so the user can see recent lows and highs. */
public final class PriceHistory {
    /** The shared history the market tick samples into; read by {@code /sm pricelog}. */
    public static final PriceHistory SHARED = new PriceHistory(120);

    public record Stats(double lowSell, double highSell, double latestSell, int samples) {
    }

    private final int capacity;
    private final Map<String, Deque<double[]>> samples = new ConcurrentHashMap<>();

    public PriceHistory(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Records a buy/sell reading for {@code id}, dropping the oldest when the per-item window is full. */
    public void add(String id, double buy, double sell, long time) {
        Deque<double[]> q = this.samples.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (q) {
            while (q.size() >= this.capacity) {
                q.removeFirst();
            }
            q.addLast(new double[]{time, buy, sell});
        }
    }

    /** Low/high/latest sell over the stored window, or null when nothing has been recorded for {@code id}. */
    public Stats stats(String id) {
        Deque<double[]> q = this.samples.get(id);
        if (q == null) {
            return null;
        }
        synchronized (q) {
            if (q.isEmpty()) {
                return null;
            }
            double low = Double.MAX_VALUE;
            double high = 0.0;
            double latest = 0.0;
            for (double[] s : q) {
                double sell = s[2];
                low = Math.min(low, sell);
                high = Math.max(high, sell);
                latest = sell;
            }
            return new Stats(low, high, latest, q.size());
        }
    }

    public void clear(String id) {
        this.samples.remove(id);
    }
}
