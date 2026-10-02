package com.skyblockminer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

final class Pathfinder {
    private static final int MAX_NODES = 250000;
    private static final long SLICE_NANOS = 5000000L;
    private static final double BODY = 1.8;
    private static final double STEP = 0.6;
    private static final double JUMP = 1.25;
    private static final double MAX_DROP = 4.0;
    private static final double HEURISTIC_WEIGHT = 2.0;
    private static final int MAX_STRAIGHT = 48;
    private static final int[][] DIRECTIONS = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final Set<String> HAZARDS = Set.of(
        "lava", "fire", "soul_fire", "magma_block", "cactus", "sweet_berry_bush", "powder_snow", "cobweb", "wither_rose", "campfire", "soul_campfire"
    );
    static final double[] EMPTY = new double[0];
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Extra cost per neighbouring hazard (lava, fire, cactus...) and per neighbouring deep drop. */
    private static final double HAZARD_COST = 1.5;
    private static final double DROP_COST = 0.5;
    private boolean safety = true;
    static final double[] HAZARD = new double[]{Double.NaN, Double.NaN};
    private Pathfinder.Shapes source;
    private PriorityQueue<Pathfinder.Node> open;
    private Long2ObjectOpenHashMap<Pathfinder.Node> nodes;
    private List<Vec3> goals;
    private double goalRadius;
    private Pathfinder.Node best;
    private double bestDistance;
    private double startDistance;
    private int expanded;
    private long startedAt;
    private long elapsedMs;
    private boolean finished;
    private List<Vec3> result;
    private boolean partial;

    boolean start(ClientLevel level, Vec3 from, List<Vec3> goals, double goalRadius) {
        return this.start(world(level), from, goals, goalRadius);
    }

    /** When on, routes keep away from hazards and cliff edges if that costs little extra distance. */
    void setSafety(boolean safety) {
        this.safety = safety;
    }

    boolean start(Pathfinder.Shapes source, Vec3 from, List<Vec3> goals, double goalRadius) {
        this.source = source;
        this.goals = goals;
        this.goalRadius = goalRadius;
        this.open = new PriorityQueue<>();
        this.nodes = new Long2ObjectOpenHashMap();
        this.result = null;
        this.partial = false;
        this.finished = false;
        this.expanded = 0;
        this.elapsedMs = 0L;
        this.startedAt = System.nanoTime();
        Pathfinder.Node start = this.startNode(from);
        if (start == null) {
            this.finished = true;
            return false;
        } else {
            start.g = 0.0;
            start.f = 2.0 * this.heuristic(start);
            this.best = start;
            this.bestDistance = this.distanceToGoal(start);
            this.startDistance = this.bestDistance;
            this.open.add(start);
            return true;
        }
    }

    boolean step() {
        if (this.finished) {
            return true;
        } else {
            long deadline = System.nanoTime() + 5000000L;

            for (int i = 0; (i & 63) != 0 || i <= 0 || System.nanoTime() <= deadline; i++) {
                Pathfinder.Node node = this.open.poll();
                if (node == null) {
                    return this.finish(null);
                }

                if (!node.closed) {
                    node.closed = true;
                    double distance = this.distanceToGoal(node);
                    if (distance < this.bestDistance) {
                        this.bestDistance = distance;
                        this.best = node;
                    }

                    if (this.isGoal(node)) {
                        return this.finish(node);
                    }

                    if (++this.expanded > 250000) {
                        return this.finish(null);
                    }

                    this.expand(node);
                }
            }

            return false;
        }
    }

    void run() {
        while (!this.step()) {
        }
    }

    List<Vec3> result() {
        return this.result;
    }

    boolean partial() {
        return this.partial;
    }

    int explored() {
        return this.expanded;
    }

    long elapsedMs() {
        return this.elapsedMs;
    }

    private boolean finish(Pathfinder.Node goal) {
        this.finished = true;
        this.elapsedMs = (System.nanoTime() - this.startedAt) / 1000000L;
        Pathfinder.Node end = goal;
        if (goal == null && this.best != null && this.bestDistance < this.startDistance - 4.0) {
            end = this.best;
            this.partial = true;
        }

        if (end == null) {
            return true;
        } else {
            List<Vec3> path = new ArrayList<>();

            for (Pathfinder.Node node = end; node != null; node = node.parent) {
                path.add(node.point());
            }

            Collections.reverse(path);
            this.result = this.straighten(path);
            return true;
        }
    }

    private List<Vec3> straighten(List<Vec3> path) {
        if (path.size() <= 2) {
            return path;
        } else {
            List<Vec3> waypoints = new ArrayList<>();
            waypoints.add(path.get(0));
            int at = 0;

            while (at < path.size() - 1) {
                int next = at + 1;

                for (int far = Math.min(path.size() - 1, at + 48); far > at + 1; far--) {
                    if (walkable(this.source, path.get(at), path.get(far))) {
                        next = far;
                        break;
                    }
                }

                waypoints.add(path.get(next));
                at = next;
            }

            return waypoints;
        }
    }

    private void expand(Pathfinder.Node node) {
        label94:
        for (int[] direction : DIRECTIONS) {
            int nx = node.x + direction[0];
            int nz = node.z + direction[1];
            boolean diagonal = direction[0] != 0 && direction[1] != 0;
            int dy = 1;

            int ny;
            double height;
            double rise;
            boolean jump;
            boolean drop;
            while (true) {
                if (dy < -4) {
                    continue label94;
                }

                ny = node.y + dy;
                double floor = this.floor(nx, ny, nz);
                if (!Double.isNaN(floor)) {
                    height = ny + floor;
                    rise = height - node.h;
                    if (!(rise > 1.250001) && !(rise < -4.0)) {
                        jump = rise > 0.6;
                        drop = rise < -0.6;
                        if (!diagonal || !jump && !drop) {
                            double top = Math.max(node.h, height) + 1.8;
                            if (this.clear(nx, nz, height, top) && this.clear(node.x, node.z, node.h, jump ? node.h + 1.25 + 1.8 : top)) {
                                if (!diagonal) {
                                    break;
                                }

                                double low = Math.max(node.h, height);
                                if (this.clear(nx, node.z, low, low + 1.8) && this.clear(node.x, nz, low, low + 1.8)) {
                                    break;
                                }
                            }
                        }
                    }
                }

                dy--;
            }

            double cost = (diagonal ? 1.4142 : 1.0) + (jump ? 0.8 : 0.0) + (drop ? 0.2 * -rise : 0.0);
            if (this.safety) {
                cost += this.danger(nx, ny, nz);
            }
            this.relax(node, nx, ny, nz, height, cost);
        }
    }

    private void relax(Pathfinder.Node from, int x, int y, int z, double h, double cost) {
        long key = BlockPos.asLong(x, y, z);
        Pathfinder.Node node = (Pathfinder.Node)this.nodes.get(key);
        if (node == null) {
            node = new Pathfinder.Node(x, y, z, h);
            this.nodes.put(key, node);
        }

        if (!node.closed) {
            double g = from.g + cost;
            if (!(g >= node.g)) {
                node.g = g;
                node.parent = from;
                node.f = g + 2.0 * this.heuristic(node);
                this.open.add(node);
            }
        }
    }

    private Pathfinder.Node startNode(Vec3 from) {
        int x = (int)Math.floor(from.x);
        int z = (int)Math.floor(from.z);
        int y = (int)Math.floor(from.y + 0.01);

        for (int dy : new int[]{0, -1, 1}) {
            Pathfinder.Node node = this.standable(x, y + dy, z);
            if (node != null) {
                return node;
            }
        }

        Pathfinder.Node closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int[] direction : DIRECTIONS) {
            for (int dyx : new int[]{0, -1}) {
                Pathfinder.Node node = this.standable(x + direction[0], y + dyx, z + direction[1]);
                if (node != null) {
                    double distance = node.point().distanceToSqr(from);
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = node;
                    }
                }
            }
        }

        if (closest != null) {
            this.nodes.put(BlockPos.asLong(closest.x, closest.y, closest.z), closest);
        }

        return closest;
    }

    private Pathfinder.Node standable(int x, int y, int z) {
        double floor = this.floor(x, y, z);
        if (Double.isNaN(floor)) {
            return null;
        } else {
            double h = y + floor;
            if (!this.clear(x, z, h, h + 1.8)) {
                return null;
            } else {
                Pathfinder.Node node = new Pathfinder.Node(x, y, z, h);
                this.nodes.put(BlockPos.asLong(x, y, z), node);
                return node;
            }
        }
    }

    /**
     * Cost of standing at (x, y, z) because of what is beside it: hazards at foot, head or floor level, and
     * open air with no floor within four blocks (a fall). Unknown (unloaded) cells cost nothing extra.
     */
    private double danger(int x, int y, int z) {
        double penalty = 0.0;
        for (int[] d : CARDINALS) {
            int ax = x + d[0];
            int az = z + d[1];
            double[] feet = this.source.get(ax, y, az);
            double[] head = this.source.get(ax, y + 1, az);
            double[] below = this.source.get(ax, y - 1, az);
            if (feet == HAZARD || head == HAZARD || below == HAZARD) {
                penalty += HAZARD_COST;
            } else if (feet != null && feet.length == 0 && below != null && below.length == 0 && this.deepDrop(ax, y, az)) {
                penalty += DROP_COST;
            }
        }
        return penalty;
    }

    private boolean deepDrop(int x, int y, int z) {
        for (int dy = 2; dy <= 4; dy++) {
            double[] cell = this.source.get(x, y - dy, z);
            if (cell == null) {
                return false;
            }
            if (cell == HAZARD) {
                return true;
            }
            if (cell.length > 0) {
                return false;
            }
        }
        return true;
    }

    private double floor(int x, int y, int z) {
        return floor(this.source, x, y, z);
    }

    private static double floor(Pathfinder.Shapes source, int x, int y, int z) {
        double[] here = source.get(x, y, z);
        if (here == HAZARD) {
            return Double.NaN;
        } else if (here.length > 0) {
            return here[1] <= 0.500001 ? here[1] : Double.NaN;
        } else {
            double[] below = source.get(x, y - 1, z);
            return below != HAZARD && below.length != 0 && !(below[1] < 0.9) ? Math.max(0.0, below[1] - 1.0) : Double.NaN;
        }
    }

    private boolean clear(int x, int z, double from, double to) {
        return clear(this.source, x, z, from, to);
    }

    private static boolean clear(Pathfinder.Shapes source, int x, int z, double from, double to) {
        for (int cy = (int)Math.floor(from); cy < to; cy++) {
            double[] s = source.get(x, cy, z);
            if (s == HAZARD) {
                return false;
            }

            if (s.length != 0) {
                double low = cy + s[0];
                double high = cy + s[1];
                if (high > from + 0.001 && low < to - 0.001) {
                    return false;
                }
            }
        }

        return true;
    }

    static boolean hazard(BlockState state) {
        return HAZARDS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    static Pathfinder.Shapes world(ClientLevel level) {
        Map<Long, double[]> cache = new HashMap<>();
        return (x, y, z) -> cache.computeIfAbsent(BlockPos.asLong(x, y, z), key -> {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (hazard(state)) {
                return HAZARD;
            } else {
                VoxelShape shape = state.getCollisionShape(level, pos);
                return shape.isEmpty() ? EMPTY : new double[]{shape.min(Axis.Y), shape.max(Axis.Y)};
            }
        });
    }

    private boolean isGoal(Pathfinder.Node node) {
        for (Vec3 goal : this.goals) {
            double dx = node.x + 0.5 - goal.x;
            double dz = node.z + 0.5 - goal.z;
            if (dx * dx + dz * dz <= this.goalRadius * this.goalRadius && Math.abs(node.h - goal.y) <= 2.5) {
                return true;
            }
        }

        return false;
    }

    private double heuristic(Pathfinder.Node node) {
        double min = Double.MAX_VALUE;

        for (Vec3 goal : this.goals) {
            double dx = Math.abs(node.x + 0.5 - goal.x);
            double dz = Math.abs(node.z + 0.5 - goal.z);
            double flat = Math.max(dx, dz) + 0.4142 * Math.min(dx, dz);
            min = Math.min(min, Math.max(0.0, flat - this.goalRadius) + 0.5 * Math.abs(node.h - goal.y));
        }

        return min;
    }

    private double distanceToGoal(Pathfinder.Node node) {
        double min = Double.MAX_VALUE;

        for (Vec3 goal : this.goals) {
            double dx = node.x + 0.5 - goal.x;
            double dy = node.h - goal.y;
            double dz = node.z + 0.5 - goal.z;
            min = Math.min(min, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }

        return min;
    }

    static boolean walkable(Pathfinder.Shapes source, Vec3 from, Vec3 to) {
        if (Math.abs(from.y - to.y) > 28.799999999999997) {
            return false;
        } else {
            double dx = to.x - from.x;
            double dz = to.z - from.z;
            double length = Math.hypot(dx, dz);
            if (length < 0.1) {
                return Math.abs(from.y - to.y) <= 0.6;
            } else {
                double px = -dz / length * 0.3;
                double pz = dx / length * 0.3;
                double height = from.y;
                double t = 0.0;

                while (true) {
                    double x = from.x + dx * t / length;
                    double z = from.z + dz * t / length;
                    double ground = groundNear(source, (int)Math.floor(x), (int)Math.floor(z), height);
                    if (Double.isNaN(ground)) {
                        return false;
                    }

                    for (int side = -1; side <= 1; side += 2) {
                        if (Double.isNaN(groundNear(source, (int)Math.floor(x + px * side), (int)Math.floor(z + pz * side), ground))) {
                            return false;
                        }
                    }

                    height = ground;
                    if (t >= length) {
                        return Math.abs(ground - to.y) <= 0.6;
                    }

                    t = Math.min(length, t + 0.3);
                }
            }
        }
    }

    private static double groundNear(Pathfinder.Shapes source, int x, int z, double near) {
        int base = (int)Math.floor(near + 0.01);
        double best = Double.NaN;

        for (int cy = base - 1; cy <= base + 1; cy++) {
            double floor = floor(source, x, cy, z);
            if (!Double.isNaN(floor)) {
                double h = cy + floor;
                if (!(Math.abs(h - near) > 0.600001) && clear(source, x, z, h, h + 1.8) && (Double.isNaN(best) || Math.abs(h - near) < Math.abs(best - near))) {
                    best = h;
                }
            }
        }

        return best;
    }

    static boolean standsAt(Pathfinder.Shapes source, int x, int y, int z, double h) {
        double floor = floor(source, x, y, z);
        return !Double.isNaN(floor) && Math.abs(y + floor - h) < 0.01 && clear(source, x, z, h, h + 1.8);
    }

    private static final class Node implements Comparable<Pathfinder.Node> {
        final int x;
        final int y;
        final int z;
        final double h;
        double g = Double.MAX_VALUE;
        double f;
        Pathfinder.Node parent;
        boolean closed;

        Node(int x, int y, int z, double h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.h = h;
        }

        Vec3 point() {
            return new Vec3(this.x + 0.5, this.h, this.z + 0.5);
        }

        public int compareTo(Pathfinder.Node other) {
            return Double.compare(this.f, other.f);
        }
    }

    interface Shapes {
        double[] get(int x, int y, int z);
    }
}
