package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuildPlanTest {
    @Test
    void fillCoversTheWholeBoxInAnyCornerOrder() {
        List<BuildPlan.Target> plan = BuildPlan.plan(new int[]{3, 5, 3}, new int[]{0, 4, 1}, "fill", 9);
        assertEquals(4 * 2 * 3, plan.size());
        assertTrue(plan.stream().noneMatch(BuildPlan.Target::water));
    }

    @Test
    void wallsSkipTheInside() {
        assertEquals(16, BuildPlan.plan(new int[]{0, 0, 0}, new int[]{4, 0, 4}, "walls", 9).size());
    }

    @Test
    void lanesAreOneLayerWithWaterRowsAcrossTheShortSide() {
        List<BuildPlan.Target> plan = BuildPlan.plan(new int[]{0, 70, 0}, new int[]{19, 75, 17}, "lanes", 9);
        assertEquals(20 * 18, plan.size(), "lanes only use the lowest layer");
        assertTrue(plan.stream().allMatch(t -> t.y() == 70));
        List<Integer> waterRows = plan.stream().filter(BuildPlan.Target::water).map(BuildPlan.Target::z).distinct().sorted().toList();
        assertEquals(List.of(4, 13), waterRows, "every 9 rows, in the middle of each band");
        assertEquals(2 * 20, plan.stream().filter(BuildPlan.Target::water).count());
    }

    @Test
    void hugeAreasAreCapped() {
        assertEquals(BuildPlan.MAX_BLOCKS, BuildPlan.plan(new int[]{0, 0, 0}, new int[]{999, 9, 999}, "fill", 9).size());
    }
}
