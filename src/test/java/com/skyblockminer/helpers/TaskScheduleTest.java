package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskScheduleTest {
    @Test
    void parsesValidEntries() {
        TaskSchedule.Task t = TaskSchedule.parse("08:30 mithril");
        assertEquals(8 * 60 + 30, t.minuteOfDay());
        assertEquals("mithril", t.macroId());
    }

    @Test
    void rejectsBadEntries() {
        assertNull(TaskSchedule.parse("8am mithril"));
        assertNull(TaskSchedule.parse("25:00 mithril"));
        assertNull(TaskSchedule.parse("08:30"));
        assertNull(TaskSchedule.parse("08:99 x"));
        assertNull(TaskSchedule.parse(null));
    }

    @Test
    void firesOncePerMinute() {
        List<String> entries = List.of("08:00 mithril", "20:00 fishing");
        assertEquals("mithril", TaskSchedule.due(entries, 8 * 60, -1));
        // Same minute already fired -> no repeat.
        assertNull(TaskSchedule.due(entries, 8 * 60, 8 * 60));
        assertEquals("fishing", TaskSchedule.due(entries, 20 * 60, 8 * 60));
        assertNull(TaskSchedule.due(entries, 9 * 60, 8 * 60));
    }
}
