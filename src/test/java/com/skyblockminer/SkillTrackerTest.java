package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillTrackerTest {
    private static final String BAR = "§c1000/1000❤     §3+%s Farming (%s)     §b500/500✎ Mana";

    @Test
    void readsGainsAndProgressFromTheActionBar() {
        SkillTracker tracker = new SkillTracker(new HashMap<>());
        assertTrue(tracker.onActionBar(BAR.formatted("12.5", "1,234.5/50,000"), 0L, "farming"));
        assertFalse(tracker.onActionBar("§c1000/1000❤", 0L, "farming"));
        SkillTracker.Skill skill = tracker.active(0L).get(0);
        assertEquals("Farming", skill.name());
        assertEquals(12.5, skill.gained());
        assertEquals(1234.5, skill.current());
        assertEquals(50000.0, skill.needed());
    }

    @Test
    void rateNeedsAMinuteOfHistoryThenProjectsTheNextLevel() {
        SkillTracker tracker = new SkillTracker(new HashMap<>());
        tracker.onActionBar(BAR.formatted("100", "0/10k"), 0L, "farming");
        assertEquals(0.0, tracker.active(30_000L).get(0).perHour(), "too little history");
        tracker.onActionBar(BAR.formatted("100", "200/10k"), 120_000L, "farming");
        SkillTracker.Skill skill = tracker.active(120_000L).get(0);
        assertEquals(200 / 120_000.0 * 3_600_000.0, skill.perHour(), 1e-6);
        assertEquals(10_000.0, skill.needed());
        assertEquals((long) ((10_000 - 200) / skill.perHour() * 3_600_000.0), skill.etaMs());
    }

    @Test
    void percentProgressHasNoEta() {
        SkillTracker tracker = new SkillTracker(new HashMap<>());
        tracker.onActionBar(BAR.formatted("4", "45.2%"), 0L, null);
        SkillTracker.Skill skill = tracker.active(0L).get(0);
        assertEquals(45.2, skill.current(), 1e-9);
        assertEquals(-1L, skill.etaMs());
    }

    @Test
    void remembersTheBestRatePerMacroAndForgetsIdleSkills() {
        Map<String, Double> best = new HashMap<>();
        SkillTracker tracker = new SkillTracker(best);
        tracker.onActionBar(BAR.formatted("100", "0/10k"), 0L, "farming");
        tracker.onActionBar(BAR.formatted("100", "200/10k"), 60_000L, "farming");
        assertEquals(12_000.0, best.get("Farming|farming"), 1e-6);
        List<Map.Entry<String, Double>> ranked = tracker.bestFor("Farming");
        assertEquals("farming", ranked.get(0).getKey());
        assertTrue(tracker.active(60_000L + 6 * 60_000L).isEmpty(), "idle for over 5 minutes");
    }

    @Test
    void parsesSuffixedNumbers() {
        assertEquals(12_300.0, SkillTracker.parse("12.3k"), 1e-9);
        assertEquals(4_000_000.0, SkillTracker.parse("4M"), 1e-9);
        assertEquals(1234.5, SkillTracker.parse("1,234.5"), 1e-9);
    }
}
