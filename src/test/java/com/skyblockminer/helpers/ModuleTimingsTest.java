package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleTimingsTest {
    @Test
    void averagesConvergeTowardTheSample() {
        ModuleTimings t = new ModuleTimings();
        for (int i = 0; i < 200; i++) {
            t.record("a", 2_000_000L); // 2 ms
        }
        assertEquals(2.0, t.average("a"), 0.05);
    }

    @Test
    void linesAreSortedMostExpensiveFirst() {
        ModuleTimings t = new ModuleTimings();
        for (int i = 0; i < 200; i++) {
            t.record("cheap", 1_000_000L);
            t.record("pricey", 5_000_000L);
        }
        List<String> lines = t.lines();
        assertTrue(lines.get(0).startsWith("pricey"), lines.toString());
        assertTrue(lines.get(1).startsWith("cheap"), lines.toString());
    }

    @Test
    void unknownModuleIsZero() {
        assertEquals(0.0, new ModuleTimings().average("nope"), 1e-9);
    }
}
