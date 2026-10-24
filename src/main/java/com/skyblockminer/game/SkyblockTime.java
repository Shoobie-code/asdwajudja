package com.skyblockminer.game;

import java.util.ArrayList;
import java.util.List;

/**
 * The SkyBlock calendar and its recurring events, worked out from the clock (no API needed). A SkyBlock day is
 * 20 real minutes, a month 31 days and a year 12 months, counted from Year 1, Early Spring 1st.
 *
 * <p>Idea from SkyHanni's timer features (LGPL-2.1, github.com/hannibal002/SkyHanni); this is an independent
 * implementation from the published calendar rules.
 */
public final class SkyblockTime {
    static final long EPOCH = 1560275700000L;
    static final long HOUR = 50_000L;
    static final long DAY = 24 * HOUR;
    static final long MONTH = 31 * DAY;
    static final long YEAR = 12 * MONTH;
    static final String[] MONTHS = {
        "Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
        "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"
    };
    private static final long REAL_HOUR = 3_600_000L;
    private static final long REAL_MINUTE = 60_000L;

    private SkyblockTime() {
    }

    /** {year, month index 0-11, day 1-31, hour 0-23, minute 0-59}. */
    static int[] date(long now) {
        long elapsed = now - EPOCH;
        int year = (int) (elapsed / YEAR) + 1;
        long inYear = elapsed % YEAR;
        int month = (int) (inYear / MONTH);
        long inMonth = inYear % MONTH;
        int day = (int) (inMonth / DAY) + 1;
        long inDay = inMonth % DAY;
        int hour = (int) (inDay / HOUR);
        int minute = (int) (inDay % HOUR * 60 / HOUR);
        return new int[]{year, month, day, hour, minute};
    }

    /** For example "Late Winter 24th, Year 391 · 6:40pm" (minutes in steps of ten, as the game shows them). */
    public static String format(long now) {
        int[] d = date(now);
        int hour12 = d[3] % 12 == 0 ? 12 : d[3] % 12;
        return String.format("%s %d%s, Year %d · %d:%02d%s", MONTHS[d[1]], d[2], ordinal(d[2]), d[0], hour12, d[4] / 10 * 10,
            d[3] < 12 ? "am" : "pm");
    }

    static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) {
            return "th";
        }
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    /** Start of the SkyBlock month containing {@code now}. */
    static long monthStart(long now) {
        long elapsed = now - EPOCH;
        return EPOCH + elapsed - elapsed % MONTH;
    }

    /**
     * The next (or current) window that recurs every hour of real time, starting {@code minute} minutes past
     * the hour and lasting {@code minutes}. Returns {start, end}.
     */
    static long[] hourly(long now, int minute, int minutes) {
        long offset = minute * REAL_MINUTE;
        long start = Math.floorDiv(now - offset, REAL_HOUR) * REAL_HOUR + offset;
        if (now >= start + minutes * REAL_MINUTE) {
            start += REAL_HOUR;
        }
        return new long[]{start, start + minutes * REAL_MINUTE};
    }

    /** The next (or current) window starting on {@code day} of {@code month} each SkyBlock year. Returns {start, end}. */
    static long[] yearly(long now, int month, int day, long length) {
        long start = EPOCH + (now - EPOCH) / YEAR * YEAR + month * MONTH + (day - 1) * DAY;
        while (now >= start + length) {
            start += YEAR;
        }
        return new long[]{start, start + length};
    }

    /** The next (or current) window starting on {@code day} of every SkyBlock month. Returns {start, end}. */
    static long[] monthly(long now, int day, long length) {
        long start = monthStart(now) + (day - 1) * DAY;
        while (now >= start + length) {
            start += MONTH;
        }
        return new long[]{start, start + length};
    }

    /** The Cult of the Fallen Star meets on the 7th, 14th, 21st and 28th of each month, from midnight to 6am. */
    static long[] cult(long now) {
        long best = Long.MAX_VALUE;
        long[] window = null;
        for (int day : new int[]{7, 14, 21, 28}) {
            long[] w = monthly(now, day, 6 * HOUR);
            if (w[0] < best) {
                best = w[0];
                window = w;
            }
        }
        return window;
    }

    public record Event(String name, long start, long end) {
        public boolean active(long now) {
            return now >= this.start && now < this.end;
        }
    }

    /** The tracked events, soonest first. */
    public static List<Event> events(long now) {
        List<Event> events = new ArrayList<>();
        long[] w = hourly(now, 15, 20);
        events.add(new Event("Jacob's Contest", w[0], w[1]));
        w = hourly(now, 55, 5);
        events.add(new Event("Dark Auction", w[0], w[1]));
        w = cult(now);
        events.add(new Event("Cult of the Fallen Star", w[0], w[1]));
        w = yearly(now, 7, 29, 3 * DAY);
        events.add(new Event("Spooky Festival", w[0], w[1]));
        w = yearly(now, 11, 24, 3 * DAY);
        events.add(new Event("Season of Jerry", w[0], w[1]));
        w = yearly(now, 11, 29, 3 * DAY);
        events.add(new Event("New Year Celebration", w[0], w[1]));
        events.sort((a, b) -> Long.compare(a.active(now) ? Long.MIN_VALUE : a.start(), b.active(now) ? Long.MIN_VALUE : b.start()));
        return events;
    }

    /** "1h 05m", "4m 09s" or "12s". */
    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 86400) {
            return String.format("%dd %02dh", s / 86400, s % 86400 / 3600);
        }
        if (s >= 3600) {
            return String.format("%dh %02dm", s / 3600, s % 3600 / 60);
        }
        if (s >= 60) {
            return String.format("%dm %02ds", s / 60, s % 60);
        }
        return s + "s";
    }
}
