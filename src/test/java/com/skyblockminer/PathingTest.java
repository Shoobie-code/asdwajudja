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

        Pathfinder.Shapes shapes() {
            double[] full = {0.0, 1.0};
            return (x, y, z) -> {
                long key = LongMap.pack(x, y, z);
                return this.hazard.contains(key) ? Pathfinder.HAZARD : this.solid.contains(key) ? full : Pathfinder.EMPTY;
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
}
