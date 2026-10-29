package com.skyblockminer.helpers;

import java.util.List;

/**
 * Decides when a scheduled macro should auto-start. Each entry is {@code "HH:mm macroId"}; an entry fires once when the
 * clock reaches its minute. Pure logic so it can be unit tested; the caller supplies the current minute of day and
 * tracks the last minute it fired to avoid firing twice.
 */
public final class TaskSchedule {
    public record Task(int minuteOfDay, String macroId) {
    }

    private TaskSchedule() {
    }

    /** Parses {@code "HH:mm macroId"}, or null when the line is malformed. */
    public static Task parse(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+", 2);
        if (parts.length != 2) {
            return null;
        }
        String[] hm = parts[0].split(":");
        if (hm.length != 2) {
            return null;
        }
        try {
            int h = Integer.parseInt(hm[0]);
            int m = Integer.parseInt(hm[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59 || parts[1].isBlank()) {
                return null;
            }
            return new Task(h * 60 + m, parts[1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The macro id to start now, or null. Fires an entry whose minute equals {@code nowMinute} only when that minute is
     * not {@code lastFiredMinute}, so each scheduled time triggers once per day.
     */
    public static String due(List<String> entries, int nowMinute, int lastFiredMinute) {
        if (nowMinute == lastFiredMinute) {
            return null;
        }
        for (String line : entries) {
            Task task = parse(line);
            if (task != null && task.minuteOfDay() == nowMinute) {
                return task.macroId();
            }
        }
        return null;
    }
}
