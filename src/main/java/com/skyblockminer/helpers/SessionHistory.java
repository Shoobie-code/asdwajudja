package com.skyblockminer.helpers;

import java.util.List;

/**
 * Formats and reads past macro sessions so a user can compare runs (for example, which gave the best coins/hour).
 * One session is one tab-separated line: {@code endedAtMs \t macro \t minutes \t coins}.
 */
public final class SessionHistory {
    public record Entry(long endedAt, String macro, double minutes, long coins) {
        /** Coins per hour for this session, or 0 for a zero-length run. */
        public double coinsPerHour() {
            return this.minutes <= 0.0 ? 0.0 : this.coins / (this.minutes / 60.0);
        }
    }

    private SessionHistory() {
    }

    public static String format(Entry e) {
        return e.endedAt() + "\t" + e.macro().replace('\t', ' ') + "\t" + String.format(java.util.Locale.ROOT, "%.1f", e.minutes()) + "\t" + e.coins();
    }

    /** Parses one line written by {@link #format}, or null when it is malformed. */
    public static Entry parse(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.split("\t");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new Entry(Long.parseLong(parts[0].trim()), parts[1], Double.parseDouble(parts[2].trim()), Long.parseLong(parts[3].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The entry with the highest coins/hour, or null when the list is empty. */
    public static Entry best(List<Entry> entries) {
        Entry best = null;
        for (Entry e : entries) {
            if (best == null || e.coinsPerHour() > best.coinsPerHour()) {
                best = e;
            }
        }
        return best;
    }
}
