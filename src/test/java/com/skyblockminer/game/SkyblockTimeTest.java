package com.skyblockminer.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyblockTimeTest {
    private static final long E = SkyblockTime.EPOCH;

    @Test
    void calendarCountsFromYearOne() {
        assertArrayEquals(new int[]{1, 0, 1, 0, 0}, SkyblockTime.date(E));
        long t = E + SkyblockTime.YEAR + 2 * SkyblockTime.MONTH + 3 * SkyblockTime.DAY + 5 * SkyblockTime.HOUR + SkyblockTime.HOUR / 2;
        assertArrayEquals(new int[]{2, 2, 4, 5, 30}, SkyblockTime.date(t));
        assertEquals("Late Spring 4th, Year 2 · 5:30am", SkyblockTime.format(t));
        assertEquals("th", SkyblockTime.ordinal(11));
        assertEquals("nd", SkyblockTime.ordinal(22));
    }

    @Test
    void hourlyWindowsStartPastTheHour() {
        long noon = 1_800_000_000_000L / 3_600_000L * 3_600_000L;
        long[] jacob = SkyblockTime.hourly(noon, 15, 20);
        assertEquals(noon + 15 * 60_000L, jacob[0]);
        // During the contest the current window is returned; after it, the next hour's.
        assertEquals(jacob[0], SkyblockTime.hourly(noon + 20 * 60_000L, 15, 20)[0]);
        assertEquals(jacob[0] + 3_600_000L, SkyblockTime.hourly(noon + 40 * 60_000L, 15, 20)[0]);
    }

    @Test
    void cultMeetsEverySeventhDay() {
        long month = E + 5 * SkyblockTime.MONTH;
        long[] w = SkyblockTime.cult(month + 6 * SkyblockTime.DAY + SkyblockTime.HOUR);
        assertEquals(month + 6 * SkyblockTime.DAY, w[0]);
        assertEquals(6 * SkyblockTime.HOUR, w[1] - w[0]);
        long[] next = SkyblockTime.cult(month + 6 * SkyblockTime.DAY + 7 * SkyblockTime.HOUR);
        assertEquals(month + 13 * SkyblockTime.DAY, next[0]);
        long[] wrap = SkyblockTime.cult(month + 29 * SkyblockTime.DAY);
        assertEquals(month + SkyblockTime.MONTH + 6 * SkyblockTime.DAY, wrap[0]);
    }

    @Test
    void yearlyEventsRollOver() {
        long[] spooky = SkyblockTime.yearly(E, 7, 29, 3 * SkyblockTime.DAY);
        assertEquals(E + 7 * SkyblockTime.MONTH + 28 * SkyblockTime.DAY, spooky[0]);
        long[] next = SkyblockTime.yearly(spooky[1], 7, 29, 3 * SkyblockTime.DAY);
        assertEquals(spooky[0] + SkyblockTime.YEAR, next[0]);
        List<SkyblockTime.Event> events = SkyblockTime.events(spooky[0] + 1);
        assertEquals("Spooky Festival", events.get(0).name());
        assertEquals("1h 05m", SkyblockTime.duration(65 * 60_000L));
        assertEquals("4m 09s", SkyblockTime.duration(249_000L));
    }
}
