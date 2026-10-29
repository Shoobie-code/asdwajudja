package com.skyblockminer.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling per-module timing, so the user can see which part of the mod costs the most each tick. Each module keeps an
 * exponential moving average of its last run times in milliseconds.
 */
public final class ModuleTimings {
    /** The shared instance the client tick records into; read by {@code /sm perf}. */
    public static final ModuleTimings SHARED = new ModuleTimings();

    private static final double ALPHA = 0.1;

    private final Map<String, Double> averageMs = new ConcurrentHashMap<>();

    /** Records one run of {@code module} that took {@code nanos} nanoseconds. */
    public void record(String module, long nanos) {
        double ms = nanos / 1_000_000.0;
        this.averageMs.merge(module, ms, (old, sample) -> old + ALPHA * (sample - old));
    }

    /** Average milliseconds for a module, or 0 if it has not been recorded. */
    public double average(String module) {
        return this.averageMs.getOrDefault(module, 0.0);
    }

    /** "module: X.XX ms" lines, most expensive first. */
    public List<String> lines() {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(this.averageMs.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>(entries.size());
        for (Map.Entry<String, Double> e : entries) {
            out.add(String.format(Locale.ROOT, "%s: %.2f ms", e.getKey(), e.getValue()));
        }
        return out;
    }

    public void clear() {
        this.averageMs.clear();
    }
}
