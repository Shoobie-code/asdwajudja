package com.skyblockminer;

import java.util.List;

/**
 * Plans teleports along a walking path, using only the block map (so it is unit tested without the game):
 * <ul>
 *   <li><b>Etherwarp</b> (sneak + use an Aspect of the Void or Etherwarp Conduit): lands on top of the block
 *       you look at, up to about 57 blocks away, if two blocks of air are above it.</li>
 *   <li><b>Instant Transmission</b> (use an Aspect of the End or Void): moves you a fixed 8 to 12 blocks along
 *       your view. Planned only along flat, clear stretches with the view level, so you land on the path.</li>
 * </ul>
 * Points are {x, feet height, z}. The walker checks each planned etherwarp against the real game before using
 * it, since some blocks without collision (grass, flowers) still stop the etherwarp ray.
 */
final class WarpPlanner {
    static final double EYE = 1.62;
    /** Path points more than this many times the hop length along the path cannot be on a straight hop. */
    private static final double PATH_SLACK = 1.6;

    private WarpPlanner() {
    }

    /** A block the player can etherwarp onto: it has a shape, is not a hazard, and two empty cells are above it. */
    static boolean landable(Pathfinder.Shapes source, int x, int y, int z) {
        double[] block = source.get(x, y, z);
        if (block == Pathfinder.HAZARD || block.length == 0) {
            return false;
        }
        double[] above = source.get(x, y + 1, z);
        double[] above2 = source.get(x, y + 2, z);
        return above != Pathfinder.HAZARD && above.length == 0 && above2 != Pathfinder.HAZARD && above2.length == 0;
    }

    /** The block a player standing at {@code feet} stands on. */
    static int[] blockUnder(double[] feet) {
        return new int[]{(int) Math.floor(feet[0]), (int) Math.floor(feet[1] - 0.01), (int) Math.floor(feet[2])};
    }

    /**
     * Walks the ray from {@code origin} along {@code dir} (need not be normalised) through the grid and returns
     * the first cell whose shape the ray passes through, or null within {@code max} blocks. Hazards block.
     */
    static int[] trace(Pathfinder.Shapes source, double[] origin, double[] dir, double max) {
        double length = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        if (length < 1.0E-9) {
            return null;
        }
        double dx = dir[0] / length;
        double dy = dir[1] / length;
        double dz = dir[2] / length;
        int x = (int) Math.floor(origin[0]);
        int y = (int) Math.floor(origin[1]);
        int z = (int) Math.floor(origin[2]);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double deltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double deltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double deltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double maxX = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? x + 1 - origin[0] : origin[0] - x) * deltaX);
        double maxY = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? y + 1 - origin[1] : origin[1] - y) * deltaY);
        double maxZ = dz == 0 ? Double.MAX_VALUE : ((dz > 0 ? z + 1 - origin[2] : origin[2] - z) * deltaZ);
        double t = 0.0;
        while (t <= max) {
            double exit = Math.min(maxX, Math.min(maxY, maxZ));
            double[] shape = source.get(x, y, z);
            if (shape == Pathfinder.HAZARD) {
                return new int[]{x, y, z};
            }
            if (shape.length > 0) {
                // The ray is inside this cell between t and exit: does its height range meet the shape's?
                double y0 = origin[1] + dy * t - y;
                double y1 = origin[1] + dy * Math.min(exit, max) - y;
                double low = Math.min(y0, y1);
                double high = Math.max(y0, y1);
                if (high >= shape[0] - 1.0E-6 && low <= shape[1] + 1.0E-6) {
                    return new int[]{x, y, z};
                }
            }
            t = exit;
            if (maxX <= maxY && maxX <= maxZ) {
                x += stepX;
                maxX += deltaX;
            } else if (maxY <= maxZ) {
                y += stepY;
                maxY += deltaY;
            } else {
                z += stepZ;
                maxZ += deltaZ;
            }
        }
        return null;
    }

    /**
     * A point on the given block the player can see from {@code eye} within {@code range}, preferring the top
     * face, or null. Several points on each visible face are tried.
     */
    static double[] aimAt(Pathfinder.Shapes source, double[] eye, int bx, int by, int bz, double range) {
        double[] shape = source.get(bx, by, bz);
        if (shape.length == 0 || shape == Pathfinder.HAZARD) {
            return null;
        }
        double top = by + shape[1];
        double[][] candidates = {
            {bx + 0.5, top - 0.02, bz + 0.5},
            {bx + 0.25, top - 0.02, bz + 0.25},
            {bx + 0.75, top - 0.02, bz + 0.75},
            {bx + 0.25, top - 0.02, bz + 0.75},
            {bx + 0.75, top - 0.02, bz + 0.25},
            {bx + 0.02, by + shape[1] * 0.5, bz + 0.5},
            {bx + 0.98, by + shape[1] * 0.5, bz + 0.5},
            {bx + 0.5, by + shape[1] * 0.5, bz + 0.02},
            {bx + 0.5, by + shape[1] * 0.5, bz + 0.98},
        };
        for (int i = 0; i < candidates.length; i++) {
            double[] point = candidates[i];
            // Side faces only count when the eye is on their side of the block.
            if (i == 5 && eye[0] > bx || i == 6 && eye[0] < bx + 1 || i == 7 && eye[2] > bz || i == 8 && eye[2] < bz + 1) {
                continue;
            }
            if (i < 5 && eye[1] < top) {
                continue;
            }
            double[] dir = {point[0] - eye[0], point[1] - eye[1], point[2] - eye[2]};
            double distance = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            if (distance > range) {
                continue;
            }
            int[] hit = trace(source, eye, dir, distance + 0.1);
            if (hit != null && hit[0] == bx && hit[1] == by && hit[2] == bz) {
                return point;
            }
        }
        return null;
    }

    /**
     * The farthest point of {@code path} after {@code from} the player can etherwarp to from {@code eye}, if it
     * saves at least {@code minGain} blocks of walking; otherwise -1. Points whose index is in {@code skip}
     * (already failed) are passed over.
     */
    static int etherwarpHop(Pathfinder.Shapes source, double[] eye, List<double[]> path, int from, double range, double minGain,
        java.util.function.IntPredicate skip) {
        double[] along = lengths(path);
        for (int j = path.size() - 1; j > from; j--) {
            if (along[j] - along[from] < minGain) {
                return -1;
            }
            if (skip != null && skip.test(j)) {
                continue;
            }
            double[] p = path.get(j);
            double dx = p[0] - eye[0];
            double dy = p[1] - eye[1];
            double dz = p[2] - eye[2];
            if (dx * dx + dy * dy + dz * dz > (range + 1.0) * (range + 1.0)) {
                continue;
            }
            int[] block = blockUnder(p);
            if (landable(source, block[0], block[1], block[2]) && aimAt(source, eye, block[0], block[1], block[2], range) != null) {
                return j;
            }
        }
        return -1;
    }

    /**
     * Where an Instant Transmission with a level view toward {@code path[j]} would put the player, choosing
     * the farthest j for which the whole {@code distance}-long hop is flat floor with head room. Returns the
     * landing point, or null if no stretch ahead is straight and flat enough.
     */
    static double[] transmissionHop(Pathfinder.Shapes source, double[] feet, List<double[]> path, int from, double distance) {
        double[] along = lengths(path);
        for (int j = path.size() - 1; j > from; j--) {
            double[] p = path.get(j);
            if (along[j] - along[from] > distance * PATH_SLACK + 2.0) {
                continue;
            }
            double dx = p[0] - feet[0];
            double dz = p[2] - feet[2];
            double flat = Math.hypot(dx, dz);
            if (flat < distance - 0.01) {
                return null;
            }
            double[] land = {feet[0] + dx / flat * distance, feet[1], feet[2] + dz / flat * distance};
            if (Math.abs(p[1] - feet[1]) < 0.01 && Pathfinder.walkable(source, feet, land, 0.01)) {
                return land;
            }
        }
        return null;
    }

    /** Index of the path point nearest to {@code pos}, searched from {@code from} onwards within a window. */
    static int nearest(List<double[]> path, double[] pos, int from, int window) {
        int best = Math.max(0, Math.min(from, path.size() - 1));
        double bestDistance = Double.MAX_VALUE;
        for (int i = Math.max(0, from); i < Math.min(path.size(), from + window); i++) {
            double[] p = path.get(i);
            double dx = p[0] - pos[0];
            double dy = p[1] - pos[1];
            double dz = p[2] - pos[2];
            double d = dx * dx + dy * dy * 4.0 + dz * dz;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /** Running walking distance along the path at each point. */
    static double[] lengths(List<double[]> path) {
        double[] along = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1);
            double[] b = path.get(i);
            along[i] = along[i - 1] + Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
        }
        return along;
    }
}
