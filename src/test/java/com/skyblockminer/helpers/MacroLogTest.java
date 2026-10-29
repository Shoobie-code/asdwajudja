package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MacroLogTest {
    @Test
    void keepsOnlyTheMostRecentLines() {
        MacroLog log = new MacroLog(3);
        for (int i = 1; i <= 5; i++) {
            log.add("line " + i);
        }
        List<String> lines = log.snapshot();
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).endsWith("line 3"), lines.toString());
        assertTrue(lines.get(2).endsWith("line 5"), lines.toString());
    }

    @Test
    void dumpJoinsLinesInOrder() {
        MacroLog log = new MacroLog(10);
        log.add("first");
        log.add("second");
        String dump = log.dump();
        assertTrue(dump.indexOf("first") < dump.indexOf("second"));
        assertEquals(2, dump.split("\n").length);
    }

    @Test
    void linesCarryATimestampBeforeTheText() {
        MacroLog log = new MacroLog(1);
        log.add("hello");
        // "HH:mm:ss  hello"
        assertTrue(log.snapshot().get(0).matches("\\d{2}:\\d{2}:\\d{2}  hello"), log.snapshot().toString());
    }

    @Test
    void clearEmptiesTheBuffer() {
        MacroLog log = new MacroLog(5);
        log.add("x");
        log.clear();
        assertEquals(0, log.size());
    }
}
