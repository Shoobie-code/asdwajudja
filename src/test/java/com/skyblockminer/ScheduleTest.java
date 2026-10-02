package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleTest {
    @Test
    void parsesAndRejectsWindows() {
        assertNull(Schedule.parse(""));
        assertNull(Schedule.parse("8-23"));
        assertNull(Schedule.parse("24:00-06:00"));
        assertNull(Schedule.parse("10:00-10:00"));
        assertTrue(Schedule.valid(""));
        assertTrue(Schedule.valid("08:00 - 23:30"));
        assertFalse(Schedule.valid("soon"));
        assertEquals("08:00", Schedule.parse("8:00-23:30").start());
    }

    @Test
    void dayWindow() {
        Schedule day = Schedule.parse("08:00-23:30");
        assertFalse(day.active(7 * 60 + 59));
        assertTrue(day.active(8 * 60));
        assertTrue(day.active(23 * 60 + 29));
        assertFalse(day.active(23 * 60 + 30));
        assertEquals(60, day.minutesUntilActive(7 * 60));
        assertEquals(0, day.minutesUntilActive(12 * 60));
        assertEquals(8 * 60 + 30, day.minutesUntilActive(23 * 60 + 30));
    }

    @Test
    void overnightWindow() {
        Schedule night = Schedule.parse("22:00-06:00");
        assertTrue(night.active(23 * 60));
        assertTrue(night.active(3 * 60));
        assertFalse(night.active(12 * 60));
        assertEquals(10 * 60, night.minutesUntilActive(12 * 60));
    }

    @Test
    void sellMatching() {
        assertTrue(AutoSell.matches("Enchanted Raw Fish", List.of("raw fish")));
        assertTrue(AutoSell.matches("Ectoplasm", List.of(" ", "ecto")));
        assertFalse(AutoSell.matches("Hyperion", List.of("fish", "")));
        assertFalse(AutoSell.matches("Anything", List.of()));
    }
}
