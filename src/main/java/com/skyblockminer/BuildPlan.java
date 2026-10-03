package com.skyblockminer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which blocks a build needs, from two corners and a pattern. Pure data, so layouts can be checked without
 * the game: lanes put a water row every few rows across the longer side (farmland stays hydrated within four
 * blocks of water), walls only cover the outline, fill covers everything.
 */
final class BuildPlan {
    enum Pattern {
        FILL("fill", "Fill area"),
        LANES("lanes", "Crop lanes (water rows)"),
        WALLS("walls", "Walls only"),
        FLOOR("floor", "Floor (lowest layer)");

        static final List<Pattern> ALL = List.of(values());
        final String id;
        final String label;

        Pattern(String id, String label) {
            this.id = id;
            this.label = label;
        }

        static Pattern parse(String id) {
            for (Pattern pattern : ALL) {
                if (pattern.id.equalsIgnoreCase(id)) {
                    return pattern;
                }
            }
            return FILL;
        }
    }

    /** One block to place; {@code water} means a water source instead of the build block. */
    record Target(int x, int y, int z, boolean water) {
    }

    static final int MAX_BLOCKS = 20_000;

    private BuildPlan() {
    }

    static List<Target> plan(int[] a, int[] b, String pattern, int waterEvery) {
        int minX = Math.min(a[0], b[0]);
        int minY = Math.min(a[1], b[1]);
        int minZ = Math.min(a[2], b[2]);
        int maxX = Math.max(a[0], b[0]);
        int maxY = Math.max(a[1], b[1]);
        int maxZ = Math.max(a[2], b[2]);
        Pattern kind = Pattern.parse(pattern.toLowerCase(Locale.ROOT));
        if (kind == Pattern.FLOOR || kind == Pattern.LANES) {
            maxY = minY;
        }
        // Lanes run along the longer side; water rows are counted across the shorter one.
        boolean alongX = maxX - minX >= maxZ - minZ;
        int every = Math.max(2, waterEvery);
        List<Target> targets = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                    if (kind == Pattern.WALLS && !edge) {
                        continue;
                    }
                    boolean water = false;
                    if (kind == Pattern.LANES) {
                        int across = alongX ? z - minZ : x - minX;
                        water = across % every == every / 2;
                    }
                    targets.add(new Target(x, y, z, water));
                    if (targets.size() >= MAX_BLOCKS) {
                        return targets;
                    }
                }
            }
        }
        return targets;
    }
}
