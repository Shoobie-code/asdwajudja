package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

class SessionHistoryTest {
    @Test
    void formatAndParseRoundTrip() {
        SessionHistory.Entry e = new SessionHistory.Entry(1700000000000L, "Mithril", 90.0, 1_000_000L);
        SessionHistory.Entry back = SessionHistory.parse(SessionHistory.format(e));
        assertEquals(e.endedAt(), back.endedAt());
        assertEquals("Mithril", back.macro());
        assertEquals(90.0, back.minutes(), 1e-9);
        assertEquals(1_000_000L, back.coins());
    }

    @Test
    void coinsPerHourFromMinutes() {
        assertEquals(2_000.0, new SessionHistory.Entry(0, "X", 30.0, 1_000L).coinsPerHour(), 1e-9);
        assertEquals(0.0, new SessionHistory.Entry(0, "X", 0.0, 1_000L).coinsPerHour(), 1e-9);
    }

    @Test
    void bestIsHighestCoinsPerHour() {
        SessionHistory.Entry slow = new SessionHistory.Entry(1, "A", 60.0, 1_000L);   // 1000/h
        SessionHistory.Entry fast = new SessionHistory.Entry(2, "B", 30.0, 2_000L);   // 4000/h
        assertSame(fast, SessionHistory.best(List.of(slow, fast)));
        assertNull(SessionHistory.best(List.of()));
    }

    @Test
    void parseRejectsMalformed() {
        assertNull(SessionHistory.parse("not a session"));
        assertNull(SessionHistory.parse(null));
        assertNull(SessionHistory.parse("1\tMacro\tNaNnum\t5"));
    }
}
