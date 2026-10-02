package com.skyblockminer;

import java.time.LocalTime;

/**
 * Daily active hours such as "08:00-23:30". Outside the window the macro waits in place; a window that
 * ends before it starts ("22:00-06:00") runs across midnight. An empty spec means always active.
 */
final class Schedule {
    private final int from;
    private final int to;

    private Schedule(int from, int to) {
        this.from = from;
        this.to = to;
    }

    /** Parses "HH:MM-HH:MM"; returns null for an empty, malformed or zero-length window. */
    static Schedule parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String[] parts = spec.trim().split("\\s*-\\s*");
        if (parts.length != 2) {
            return null;
        }
        int from = minutes(parts[0]);
        int to = minutes(parts[1]);
        return from < 0 || to < 0 || from == to ? null : new Schedule(from, to);
    }

    static boolean valid(String spec) {
        return spec == null || spec.isBlank() || parse(spec) != null;
    }

    private static int minutes(String time) {
        String[] parts = time.trim().split(":");
        if (parts.length != 2) {
            return -1;
        }
        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            return hours < 0 || hours > 23 || minutes < 0 || minutes > 59 ? -1 : hours * 60 + minutes;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    boolean active(int minuteOfDay) {
        return this.from < this.to
            ? minuteOfDay >= this.from && minuteOfDay < this.to
            : minuteOfDay >= this.from || minuteOfDay < this.to;
    }

    /** Minutes until the window opens again (0 when already active). */
    int minutesUntilActive(int minuteOfDay) {
        return this.active(minuteOfDay) ? 0 : Math.floorMod(this.from - minuteOfDay, 1440);
    }

    String start() {
        return String.format("%02d:%02d", this.from / 60, this.from % 60);
    }

    static int now() {
        LocalTime time = LocalTime.now();
        return time.getHour() * 60 + time.getMinute();
    }
}
