package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class CookieBuffTest {
    @Test
    void readsDurationAfterCookieContext() {
        assertEquals("5d 3h 20m", CookieBuff.timeLeft(List.of("Booster Cookie", "Time Left: 5d 3h 20m")));
        assertEquals("23h 59m", CookieBuff.timeLeft(List.of("Cookie Buff active!", "You have 23h 59m remaining")));
    }

    @Test
    void nullWithoutCookieContext() {
        assertNull(CookieBuff.timeLeft(List.of("Some Sword", "Deals damage over 5d")));
    }

    @Test
    void nullWhenNoDuration() {
        assertNull(CookieBuff.timeLeft(List.of("Booster Cookie", "No buff active")));
        assertNull(CookieBuff.timeLeft(null));
    }
}
