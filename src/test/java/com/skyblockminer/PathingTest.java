package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PathingTest {
    /** A small voxel world: full blocks and hazards, everything else air. */
    private static final class World {
        final Set<Long> solid = new HashSet<>();
        final Set<Long> hazard = new HashSet<>();

        World floor(int x0, int z0, int x1, int z1, int y) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    this.solid.add(LongMap.pack(x, y, z));
                }
            }
            return this;
        }

        World set(int x, int y, int z) {
            this.solid.add(LongMap.pack(x, y, z));
            return this;
        }

        World clear(int x, int y, int z) {
            this.solid.remove(LongMap.pack(x, y, z));
            return this;
        }

        World lava(int x, int y, int z) {
            this.solid.remove(LongMap.pack(x, y, z));
            this.hazard.add(LongMap.pack(x, y, z));
            return this;
        }

        final java.util.Map<Long, double[]> special = new java.util.HashMap<>();

        World water(int x, int y, int z) {
            this.solid.remove(LongMap.pack(x, y, z));
            this.special.put(LongMap.pack(x, y, z), Pathfinder.WATER);
            return this;
        }

        World ladder(int x, int y, int z) {
            this.special.put(LongMap.pack(x, y, z), Pathfinder.CLIMB);
            return this;
        }

        Pathfinder.Shapes shapes() {
            double[] full = {0.0, 1.0};
            return (x, y, z) -> {
                long key = LongMap.pack(x, y, z);
                double[] kind = this.special.get(key);
                return kind != null ? kind : this.hazard.contains(key) ? Pathfinder.HAZARD : this.solid.contains(key) ? full : Pathfinder.EMPTY;
            };
        }
    }

    private static Pathfinder search(World world, double[] from, double[] goal, double radius) {
        Pathfinder finder = new Pathfinder();
        assertTrue(finder.start(world.shapes(), from[0], from[1], from[2], List.of(goal), radius));
        finder.run();
        return finder;
    }

    private static double length(List<double[]> path) {
        double[] along = WarpPlanner.lengths(path);
        return along[along.length - 1];
    }

    @Test
    void longMapStoresAndGrows() {
        LongMap<Integer> map = new LongMap<>(4);
        for (int i = -500; i < 500; i++) {
            map.put(LongMap.pack(i, i % 7, -i), i);
        }
        assertEquals(1000, map.size());
        for (int i = -500; i < 500; i++) {
            assertEquals(i, map.get(LongMap.pack(i, i % 7, -i)));
        }
        assertNull(map.get(LongMap.pack(1, 2, 3)));
        assertTrue(LongMap.pack(1, 0, 0) != LongMap.pack(0, 0, 1));
    }

    @Test
    void straightLineOnFlatGroundIsTwoWaypoints() {
        World world = new World().floor(0, 0, 40, 4, 64);
        Pathfinder finder = search(world, new double[]{0.5, 65, 2.5}, new double[]{38.5, 65, 2.5}, 0.5);
        assertFalse(finder.partial());
        assertEquals(2, finder.waypoints().size());
        assertEquals(38.5, finder.waypoints().get(1)[0], 1.0E-9);
    }

    @Test
    void goesAroundAWallNearlyOptimally() {
        World world = new World().floor(-10, -20, 30, 20, 64);
        // A wall at x = 10 from z = -10 to z = 10, two high.
        for (int z = -10; z <= 10; z++) {
            world.set(10, 65, z).set(10, 66, z);
        }
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{20.5, 65, 0.5}, 0.5);
        assertFalse(finder.partial());
        double walked = length(finder.raw());
        // Shortest way round: about 2 * hypot(10, 10.5) on the grid; allow the search weight's slack.
        double best = 2 * Math.hypot(10, 11);
        assertTrue(walked < best * 1.2, "path too long: " + walked);
    }

    @Test
    void jumpsAOneBlockGapOnABridge() {
        // A one-wide bridge over the void with one block missing.
        World world = new World().floor(0, 0, 12, 0, 64).clear(6, 64, 0);
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{12.5, 65, 0.5}, 0.5);
        assertFalse(finder.partial());
        List<double[]> waypoints = finder.waypoints();
        boolean anyGap = false;
        for (int i = 0; i < waypoints.size(); i++) {
            if (finder.gapAt(i)) {
                anyGap = true;
                assertEquals(7.5, waypoints.get(i)[0], 1.0E-9);
                assertEquals(5.5, waypoints.get(i - 1)[0], 1.0E-9);
            }
        }
        assertTrue(anyGap);
    }

    @Test
    void jumpsOverALavaStreamInsteadOfWadingIn() {
        World world = new World().floor(0, 0, 12, 0, 64).lava(6, 64, 0);
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{12.5, 65, 0.5}, 0.5);
        assertFalse(finder.partial());
        for (double[] cell : finder.raw()) {
            assertFalse(cell[0] == 6.5, "stepped into the lava");
        }
    }

    @Test
    void climbsAOneBlockStep() {
        World world = new World().floor(0, 0, 4, 0, 64).floor(5, 0, 10, 0, 65);
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{9.5, 66, 0.5}, 0.5);
        assertFalse(finder.partial());
        double[] end = finder.raw().get(finder.raw().size() - 1);
        assertEquals(66.0, end[1], 1.0E-9);
    }

    @Test
    void avoidedCellsAreRoutedAround() {
        // Two parallel corridors from x=0 to x=10 at z=0 and z=4, joined at both ends.
        World world = new World().floor(0, 0, 10, 0, 64).floor(0, 4, 10, 4, 64).floor(0, 0, 0, 4, 64).floor(10, 0, 10, 4, 64);
        Pathfinder finder = new Pathfinder();
        finder.setSafety(false);
        for (int x = 1; x < 10; x++) {
            finder.avoid(x, 65, 0);
        }
        assertTrue(finder.start(world.shapes(), 0.5, 65, 0.5, List.of(new double[]{10.5, 65, 0.5}), 0.5));
        finder.run();
        boolean usedOther = false;
        for (double[] cell : finder.raw()) {
            usedOther |= cell[2] == 4.5;
        }
        assertTrue(usedOther);
    }

    @Test
    void unreachableGoalGivesAPartialPathOrNone() {
        World world = new World().floor(0, 0, 20, 0, 64);
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{60.5, 65, 0.5}, 0.5);
        assertTrue(finder.partial());
        double[] end = finder.raw().get(finder.raw().size() - 1);
        assertEquals(20.5, end[0], 1.0E-9);
    }

    // ---- teleport planning ----

    @Test
    void rayStopsAtTheFirstBlock() {
        World world = new World().set(5, 64, 0);
        int[] hit = WarpPlanner.trace(world.shapes(), new double[]{0.5, 64.5, 0.5}, new double[]{1, 0, 0}, 20);
        assertArrayEquals(new int[]{5, 64, 0}, hit);
        assertNull(WarpPlanner.trace(world.shapes(), new double[]{0.5, 66.5, 0.5}, new double[]{1, 0, 0}, 20));
    }

    @Test
    void landableNeedsTwoAirAbove() {
        World world = new World().set(0, 64, 0).set(2, 64, 0).set(2, 66, 0);
        assertTrue(WarpPlanner.landable(world.shapes(), 0, 64, 0));
        assertFalse(WarpPlanner.landable(world.shapes(), 2, 64, 0));
        assertFalse(WarpPlanner.landable(world.shapes(), 1, 64, 0));
    }

    @Test
    void etherwarpPicksTheFarthestVisiblePoint() {
        World world = new World().floor(0, 0, 80, 0, 64);
        List<double[]> path = new ArrayList<>();
        for (int x = 0; x <= 80; x++) {
            path.add(new double[]{x + 0.5, 65, 0.5});
        }
        double[] eye = {0.5, 65 + WarpPlanner.EYE, 0.5};
        int j = WarpPlanner.etherwarpHop(world.shapes(), eye, path, 0, 57, 12, null);
        assertTrue(j >= 50 && j <= 57, "hop to " + j);
        // A tall wall at x = 20 hides everything behind it.
        for (int y = 65; y < 75; y++) {
            world.set(20, y, 0).set(20, y, 1).set(20, y, -1);
        }
        int k = WarpPlanner.etherwarpHop(world.shapes(), eye, path, 0, 57, 12, null);
        assertTrue(k >= 12 && k < 20, "hop to " + k);
        // Too little gain: no hop.
        assertEquals(-1, WarpPlanner.etherwarpHop(world.shapes(), eye, path, 0, 57, 30, null));
    }

    @Test
    void transmissionOnlyAlongFlatStretches() {
        World world = new World().floor(0, -1, 30, 1, 64);
        List<double[]> path = new ArrayList<>();
        for (int x = 0; x <= 30; x++) {
            path.add(new double[]{x + 0.5, 65, 0.5});
        }
        double[] land = WarpPlanner.transmissionHop(world.shapes(), new double[]{0.5, 65, 0.5}, path, 0, 8);
        assertNotNull(land);
        assertEquals(8.5, land[0], 1.0E-6);
        assertEquals(0.5, land[2], 1.0E-6);
        // A step in the way (slab-height changes are not allowed either).
        world.set(5, 65, 0).set(5, 65, -1).set(5, 65, 1);
        assertNull(WarpPlanner.transmissionHop(world.shapes(), new double[]{0.5, 65, 0.5}, path, 0, 8));
    }

    @Test
    void neverCutsDiagonallyBetweenTwoBlocks() {
        World world = new World().floor(0, 0, 6, 6, 64);
        // Two blocks touching at a corner: the diagonal between them is too narrow for the body.
        world.set(3, 65, 2).set(3, 66, 2).set(2, 65, 3).set(2, 66, 3);
        Pathfinder finder = new Pathfinder();
        finder.setSafety(false);
        assertTrue(finder.start(world.shapes(), 2.5, 65, 2.5, List.of(new double[]{3.5, 65, 3.5}), 0.1));
        finder.run();
        List<double[]> raw = finder.raw();
        for (int i = 1; i < raw.size(); i++) {
            double dx = raw.get(i)[0] - raw.get(i - 1)[0];
            double dz = raw.get(i)[2] - raw.get(i - 1)[2];
            boolean diagonal = dx != 0 && dz != 0;
            assertFalse(diagonal && raw.get(i - 1)[0] == 2.5 && raw.get(i - 1)[2] == 2.5 && raw.get(i)[0] == 3.5 && raw.get(i)[2] == 3.5,
                "squeezed through the corner");
        }
    }

    @Test
    void walksRoundAShortGapInsteadOfJumping() {
        // A hole in a wide floor: walking round it costs less than the gap jump.
        World world = new World().floor(0, -2, 12, 2, 64).clear(6, 64, 0);
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{12.5, 65, 0.5}, 0.5);
        for (int i = 0; i < finder.waypoints().size(); i++) {
            assertFalse(finder.gapAt(i));
        }
    }

    @Test
    void safePathsKeepAwayFromLava() {
        // A lava strip along z = 1 next to the direct line at z = 0; a detour at z = -2 is clear.
        World world = new World().floor(0, -3, 20, 1, 64);
        for (int x = 2; x <= 18; x++) {
            world.lava(x, 64, 1);
        }
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{20.5, 65, 0.5}, 0.5);
        int nextToLava = 0;
        for (double[] cell : finder.raw()) {
            if (cell[2] == 0.5 && cell[0] > 3 && cell[0] < 17) {
                nextToLava++;
            }
        }
        assertTrue(nextToLava < 3, "walked " + nextToLava + " cells beside lava");
    }

    @Test
    void rayPassesOverABottomSlab() {
        World world = new World();
        double[] slab = {0.0, 0.5};
        Pathfinder.Shapes shapes = (x, y, z) -> x == 3 && y == 64 && z == 0 ? slab : Pathfinder.EMPTY;
        assertNull(WarpPlanner.trace(shapes, new double[]{0.5, 64.75, 0.5}, new double[]{1, 0, 0}, 10));
        assertArrayEquals(new int[]{3, 64, 0}, WarpPlanner.trace(shapes, new double[]{0.5, 64.25, 0.5}, new double[]{1, 0, 0}, 10));
        assertTrue(world.solid.isEmpty());
    }

    @Test
    void etherwarpWithCachedLengthsMatches() {
        World world = new World().floor(0, 0, 60, 0, 64);
        List<double[]> path = new ArrayList<>();
        for (int x = 0; x <= 60; x++) {
            path.add(new double[]{x + 0.5, 65, 0.5});
        }
        double[] eye = {0.5, 65 + WarpPlanner.EYE, 0.5};
        int a = WarpPlanner.etherwarpHop(world.shapes(), eye, path, 0, 57, 12, null);
        int b = WarpPlanner.etherwarpHop(world.shapes(), eye, path, WarpPlanner.lengths(path), 0, 57, 12, j -> j > 50);
        assertTrue(a > 50);
        assertEquals(50, b);
    }

    // ---- speed, falls and rough terrain ----

    private static Pathfinder search(World world, double[] from, double[] goal, double speed, double fallBudget) {
        Pathfinder finder = new Pathfinder();
        finder.setSafety(false);
        finder.setSpeed(speed);
        finder.setFallBudget(fallBudget);
        assertTrue(finder.start(world.shapes(), from[0], from[1], from[2], List.of(goal), 0.5));
        finder.run();
        return finder;
    }

    private static double lowestStep(List<double[]> raw) {
        double lowest = 0.0;
        for (int i = 1; i < raw.size(); i++) {
            lowest = Math.min(lowest, raw.get(i)[1] - raw.get(i - 1)[1]);
        }
        return lowest;
    }

    @Test
    void fallDamageFollowsVanillaRules() {
        assertEquals(0.0, Pathfinder.fallDamage(3.0, Pathfinder.EMPTY, new double[]{0, 1}), 1.0E-9);
        assertEquals(1.0, Pathfinder.fallDamage(4.0, Pathfinder.EMPTY, new double[]{0, 1}), 1.0E-9);
        assertEquals(7.0, Pathfinder.fallDamage(10.0, Pathfinder.EMPTY, new double[]{0, 1}), 1.0E-9);
        assertEquals(0.0, Pathfinder.fallDamage(30.0, Pathfinder.WATER, Pathfinder.EMPTY), 1.0E-9);
        assertEquals(2.0, Pathfinder.fallDamage(10.0, Pathfinder.EMPTY, Pathfinder.SOFT), 1.0E-9);
        // About 10 ticks for four blocks, and falls speed up.
        assertEquals(10, Pathfinder.fallTicks(4.0));
        assertTrue(Pathfinder.fallTicks(20.0) < 3 * Pathfinder.fallTicks(4.0));
    }

    @Test
    void dropsOffACliffOnlyWhenHealthAllows() {
        // A ledge 8 above the ground, with a long staircase down at the far end.
        World world = new World().floor(0, 0, 30, 0, 64).floor(0, 0, 4, 0, 72);
        for (int i = 0; i < 8; i++) {
            world.floor(5 + i * 3, 0, 7 + i * 3, 0, 71 - i);
        }
        world.floor(0, 1, 30, 1, 64);
        double[] from = {0.5, 73, 0.5};
        double[] goal = {0.5, 65, 1.5};
        Pathfinder hurt = search(world, from, goal, 5.6, 0.0);
        assertFalse(hurt.partial());
        assertTrue(lowestStep(hurt.raw()) >= -3.0 - 1.0E-9, "dropped too far without health to spare");
        Pathfinder healthy = search(world, from, goal, 5.6, 10.0);
        assertFalse(healthy.partial());
        assertTrue(lowestStep(healthy.raw()) < -3.0, "walked the long way round");
        assertTrue(length(healthy.raw()) < length(hurt.raw()) / 2);
    }

    @Test
    void jumpsIntoWaterFromAnyHeight() {
        // A tower 20 above a pool, and no other way down.
        World world = new World().floor(0, 0, 1, 0, 84).floor(2, 0, 10, 0, 63);
        for (int x = 2; x <= 6; x++) {
            world.water(x, 64, 0);
        }
        Pathfinder finder = search(world, new double[]{0.5, 85, 0.5}, new double[]{9.5, 64, 0.5}, 5.6, 0.0);
        assertFalse(finder.partial());
        assertTrue(lowestStep(finder.raw()) < -15.0);
    }

    @Test
    void widerGapsAreJumpedAtHigherSpeed() {
        // A bridge with a three-block hole: too wide for a plain sprint, fine at 8 blocks per second.
        World world = new World().floor(0, 0, 16, 0, 64).clear(6, 64, 0).clear(7, 64, 0).clear(8, 64, 0);
        double[] from = {0.5, 65, 0.5};
        double[] goal = {16.5, 65, 0.5};
        assertTrue(search(world, from, goal, 5.6, 0.0).partial());
        Pathfinder fast = search(world, from, goal, 8.0, 0.0);
        assertFalse(fast.partial());
        boolean found = false;
        for (int i = 0; i < fast.waypoints().size(); i++) {
            if (fast.gapAt(i)) {
                found = true;
                assertEquals(9.5, fast.waypoints().get(i)[0], 1.0E-9);
                assertEquals(5.5, fast.waypoints().get(i - 1)[0], 1.0E-9);
            }
        }
        assertTrue(found);
    }

    @Test
    void climbsALadderUpAWall() {
        // A wall 6 high at x = 3 with a ladder on its near side, and a platform on top.
        World world = new World().floor(0, 0, 8, 0, 64);
        for (int y = 65; y <= 70; y++) {
            world.set(3, y, 0).ladder(2, y, 0);
        }
        world.floor(3, 0, 8, 0, 70);
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{6.5, 71, 0.5}, 5.6, 0.0);
        assertFalse(finder.partial());
        double[] end = finder.raw().get(finder.raw().size() - 1);
        assertEquals(71.0, end[1], 1.0E-9);
        for (double[] cell : finder.raw()) {
            if (cell[1] > 66.0 && cell[1] < 70.5) {
                assertEquals(2.5, cell[0], 1.0E-9, "left the ladder mid-climb");
            }
        }
    }

    @Test
    void swimsOnlyWhenWalkingRoundIsMuchLonger() {
        // A pond 3 wide across the direct line, with dry ground round both ends close by.
        World world = new World().floor(0, -3, 12, 3, 63).floor(0, -3, 12, 3, 64);
        for (int x = 5; x <= 7; x++) {
            for (int z = -2; z <= 2; z++) {
                world.water(x, 64, z);
            }
        }
        Pathfinder finder = search(world, new double[]{0.5, 65, 0.5}, new double[]{12.5, 65, 0.5}, 5.6, 0.0);
        assertFalse(finder.partial());
        for (double[] cell : finder.raw()) {
            assertFalse(Math.abs(cell[2]) < 2.6 && cell[0] > 5 && cell[0] < 8, "swam at " + cell[0] + ", " + cell[2]);
        }
    }

    @Test
    void gapWidthGrowsWithSpeed() {
        assertEquals(2, Pathfinder.gapsAt(5.6));
        assertEquals(3, Pathfinder.gapsAt(8.0));
        assertEquals(1, Pathfinder.gapsAt(3.0));
    }

    @Test
    void packedKeysKeepNegativeCoordinatesApart() {
        assertTrue(LongMap.pack(-1, 64, 0) != LongMap.pack(0, 64, -1));
        assertTrue(LongMap.pack(-1, -1, -1) != LongMap.pack(1, 1, 1));
        assertTrue(LongMap.pack(0, -64, 0) != LongMap.pack(0, 64, 0));
    }
}
