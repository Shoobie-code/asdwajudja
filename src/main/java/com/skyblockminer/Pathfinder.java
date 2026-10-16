package com.skyblockminer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Time-sliced A* over standable cells. Moves are walking steps (8 directions), step-ups, jumps of up to 1.25,
 * drops of up to 4 and jumps across one-block gaps (holes, lava streams). Points are {x, feet height, z}.
 *
 * <p>The search starts close to optimal (weight {@value #WEIGHT_FINE}) and, if it is still running after
 * {@value #SWITCH_AT} cells, switches to a greedier weight so long trips finish quickly. Cells the walker got
 * stuck at can be marked with {@link #avoid}, and later searches route around them.
 */
final class Pathfinder {
    private static final int MAX_NODES = 250000;
    private static final long SLICE_NANOS = 4000000L;
    private static final double BODY = 1.8;
    private static final double STEP = 0.6;
    private static final double JUMP = 1.25;
    private static final double MAX_DROP = 4.0;
    private static final double WEIGHT_FINE = 1.15;
    private static final double WEIGHT_FAST = 2.0;
    private static final int SWITCH_AT = 30000;
    private static final int MAX_STRAIGHT = 48;
    private static final int[][] DIRECTIONS = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final Set<String> HAZARDS = Set.of(
        "lava", "fire", "soul_fire", "magma_block", "cactus", "sweet_berry_bush", "powder_snow", "cobweb", "wither_rose", "campfire", "soul_campfire"
    );
    private static final IdentityHashMap<Block, Boolean> HAZARD_CACHE = new IdentityHashMap<>();
    static final double[] EMPTY = new double[0];
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Extra cost per neighbouring hazard (lava, fire, cactus...) and per neighbouring deep drop. */
    private static final double HAZARD_COST = 1.5;
    private static final double DROP_COST = 0.5;
    /** A gap jump costs its distance plus this, so it is only used when walking round is clearly longer. */
    private static final double GAP_COST = 2.5;
    private static final double AVOID_COST = 12.0;
    private boolean safety = true;
    static final double[] HAZARD = new double[]{Double.NaN, Double.NaN};
    private final LongMap<Boolean> avoid = new LongMap<>(64);
    private Pathfinder.Shapes source;
    private Node[] heap = new Node[1024];
    private int heapSize;
    private LongMap<Node> nodes;
    private double[][] goals;
    private double goalRadius;
    private double weight;
    private Pathfinder.Node best;
    private double bestDistance;
    private double startDistance;
    private int expanded;
    private long startedAt;
    private long elapsedMs;
    private boolean finished;
    private List<double[]> raw;
    private List<double[]> waypoints;
    private boolean[] gaps;
    private List<Vec3> result;
    private boolean partial;

    boolean start(ClientLevel level, Vec3 from, List<Vec3> goals, double goalRadius) {
        return this.start(world(level), from, goals, goalRadius);
    }

    /** When on, routes keep away from hazards and cliff edges if that costs little extra distance. */
    void setSafety(boolean safety) {
        this.safety = safety;
    }

    /** Makes later searches avoid the cell (x, y, z), for example where the walker kept getting stuck. */
    void avoid(int x, int y, int z) {
        this.avoid.put(LongMap.pack(x, y, z), Boolean.TRUE);
    }

    void clearAvoid() {
        this.avoid.clear();
    }

    boolean start(Pathfinder.Shapes source, Vec3 from, List<Vec3> goals, double goalRadius) {
        List<double[]> points = new ArrayList<>(goals.size());
        for (Vec3 goal : goals) {
            points.add(new double[]{goal.x, goal.y, goal.z});
        }
        return this.start(source, from.x, from.y, from.z, points, goalRadius);
    }

    boolean start(Pathfinder.Shapes source, double x, double y, double z, List<double[]> goals, double goalRadius) {
        this.source = cached(source);
        this.goals = goals.toArray(new double[0][]);
        this.goalRadius = goalRadius;
        this.heapSize = 0;
        this.nodes = new LongMap<>(4096);
        this.weight = WEIGHT_FINE;
        this.raw = null;
        this.waypoints = null;
        this.gaps = null;
        this.result = null;
        this.partial = false;
        this.finished = false;
        this.expanded = 0;
        this.elapsedMs = 0L;
        this.startedAt = System.nanoTime();
        Pathfinder.Node start = this.startNode(x, y, z);
        if (start == null || this.goals.length == 0) {
            this.finished = true;
            return false;
        }
        start.g = 0.0;
        start.f = this.weight * this.heuristic(start);
        this.best = start;
        this.bestDistance = this.distanceToGoal(start);
        this.startDistance = this.bestDistance;
        this.push(start);
        return true;
    }

    boolean step() {
        if (this.finished) {
            return true;
        }
        long deadline = System.nanoTime() + SLICE_NANOS;
        for (int i = 1; ; i++) {
            if ((i & 63) == 0 && System.nanoTime() > deadline) {
                return false;
            }
            Pathfinder.Node node = this.pop();
            if (node == null) {
                return this.finish(null);
            }
            node.closed = true;
            double distance = this.distanceToGoal(node);
            if (distance < this.bestDistance) {
                this.bestDistance = distance;
                this.best = node;
            }
            if (this.isGoal(node)) {
                return this.finish(node);
            }
            if (++this.expanded > MAX_NODES) {
                return this.finish(null);
            }
            if (this.expanded == SWITCH_AT) {
                this.reweigh(WEIGHT_FAST);
            }
            this.expand(node);
        }
    }

    void run() {
        while (!this.step()) {
        }
    }

    List<Vec3> result() {
        if (this.result == null && this.waypoints != null) {
            List<Vec3> out = new ArrayList<>(this.waypoints.size());
            for (double[] p : this.waypoints) {
                out.add(new Vec3(p[0], p[1], p[2]));
            }
            this.result = out;
        }
        return this.result;
    }

    /** The straightened waypoints as {x, feet height, z}, or null when no path was found. */
    List<double[]> waypoints() {
        return this.waypoints;
    }

    /** Every cell of the path, before straightening (used to plan teleports along it). */
    List<double[]> raw() {
        return this.raw;
    }

    /** Whether reaching waypoint {@code i} needs a jump across a gap. */
    boolean gapAt(int i) {
        return this.gaps != null && i >= 0 && i < this.gaps.length && this.gaps[i];
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
        }
        List<Pathfinder.Node> cells = new ArrayList<>();
        for (Pathfinder.Node node = end; node != null; node = node.parent) {
            cells.add(node);
        }
        Collections.reverse(cells);
        this.raw = new ArrayList<>(cells.size());
        for (Pathfinder.Node node : cells) {
            this.raw.add(node.point());
        }
        this.straighten(cells);
        return true;
    }

    /** Keeps the farthest cell reachable in a straight walk from each kept cell, and every gap landing. */
    private void straighten(List<Pathfinder.Node> cells) {
        List<double[]> out = new ArrayList<>();
        List<Boolean> gapFlags = new ArrayList<>();
        out.add(cells.get(0).point());
        gapFlags.add(false);
        int at = 0;
        while (at < cells.size() - 1) {
            int next = at + 1;
            if (!cells.get(next).gap) {
                int limit = Math.min(cells.size() - 1, at + MAX_STRAIGHT);
                for (int i = at + 2; i <= limit; i++) {
                    if (cells.get(i).gap) {
                        limit = i - 1;
                        break;
                    }
                }
                double[] from = cells.get(at).point();
                for (int far = limit; far > at + 1; far--) {
                    if (walkable(this.source, from, cells.get(far).point(), STEP)) {
                        next = far;
                        break;
                    }
                }
            }
            out.add(cells.get(next).point());
            gapFlags.add(cells.get(next).gap);
            at = next;
        }
        this.waypoints = out;
        this.gaps = new boolean[gapFlags.size()];
        for (int i = 0; i < this.gaps.length; i++) {
            this.gaps[i] = gapFlags.get(i);
        }
    }

    private void expand(Pathfinder.Node node) {
        for (int[] direction : DIRECTIONS) {
            int nx = node.x + direction[0];
            int nz = node.z + direction[1];
            boolean diagonal = direction[0] != 0 && direction[1] != 0;
            boolean level = false;
            for (int dy = 1; dy >= -4; dy--) {
                int ny = node.y + dy;
                double floor = this.floor(nx, ny, nz);
                if (Double.isNaN(floor)) {
                    continue;
                }
                double height = ny + floor;
                double rise = height - node.h;
                if (rise > JUMP + 1.0E-6 || rise < -MAX_DROP) {
                    continue;
                }
                boolean jump = rise > STEP;
                boolean drop = rise < -STEP;
                if (diagonal && (jump || drop)) {
                    continue;
                }
                double top = Math.max(node.h, height) + BODY;
                if (!this.clear(nx, nz, height, top) || !this.clear(node.x, node.z, node.h, jump ? node.h + JUMP + BODY : top)) {
                    continue;
                }
                if (diagonal) {
                    double low = Math.max(node.h, height);
                    if (!this.clear(nx, node.z, low, low + BODY) || !this.clear(node.x, nz, low, low + BODY)) {
                        continue;
                    }
                }
                double cost = (diagonal ? 1.4142 : 1.0) + (jump ? 0.8 : 0.0) + (drop ? 0.2 * -rise : 0.0);
                this.relax(node, nx, ny, nz, height, cost, false);
                level = !drop;
                break;
            }
            // A hole can also be dropped into, so a gap jump is still worth trying when the only step was down.
            if (!diagonal && !level) {
                this.gapJump(node, direction[0], direction[1]);
            }
        }
    }

    /**
     * Jump over a one-block gap (a hole at least two deep, or a hazard) to a floor two cells away that is at
     * most 0.5 higher and at most 2 lower. Only tried where no ordinary step was possible.
     */
    private void gapJump(Pathfinder.Node node, int dx, int dz) {
        int mx = node.x + dx;
        int mz = node.z + dz;
        if (!Double.isNaN(this.floor(mx, node.y, mz)) || !Double.isNaN(this.floor(mx, node.y - 1, mz))) {
            return;
        }
        double arc = node.h + JUMP + BODY;
        if (!this.clear(node.x, node.z, node.h, arc) || !this.clear(mx, mz, node.h, arc)) {
            return;
        }
        int lx = node.x + 2 * dx;
        int lz = node.z + 2 * dz;
        for (int dy = 0; dy >= -2; dy--) {
            int ly = node.y + dy;
            double floor = this.floor(lx, ly, lz);
            if (Double.isNaN(floor)) {
                continue;
            }
            double height = ly + floor;
            double rise = height - node.h;
            if (rise > 0.5 + 1.0E-6 || rise < -2.0) {
                continue;
            }
            if (this.clear(lx, lz, height, Math.max(height + BODY, arc))) {
                this.relax(node, lx, ly, lz, height, 2.0 + GAP_COST, true);
            }
            return;
        }
    }

    private void relax(Pathfinder.Node from, int x, int y, int z, double h, double cost, boolean gap) {
        long key = LongMap.pack(x, y, z);
        Pathfinder.Node node = this.nodes.get(key);
        if (node == null) {
            node = new Pathfinder.Node(x, y, z, h);
            this.nodes.put(key, node);
        }
        if (node.closed) {
            return;
        }
        if (this.safety) {
            if (Double.isNaN(node.danger)) {
                node.danger = this.danger(x, y, z);
            }
            cost += node.danger;
        }
        if (this.avoid.size() > 0 && this.avoid.get(key) != null) {
            cost += AVOID_COST;
        }
        double g = from.g + cost;
        if (g >= node.g) {
            return;
        }
        node.g = g;
        node.parent = from;
        node.gap = gap;
        if (Double.isNaN(node.hCost)) {
            node.hCost = this.heuristic(node);
        }
        node.f = g + this.weight * node.hCost;
        if (node.heapIndex >= 0) {
            this.siftUp(node.heapIndex);
        } else {
            this.push(node);
        }
    }

    private Pathfinder.Node startNode(double fx, double fy, double fz) {
        int x = (int) Math.floor(fx);
        int z = (int) Math.floor(fz);
        int y = (int) Math.floor(fy + 0.01);
        for (int dy : new int[]{0, -1, 1}) {
            Pathfinder.Node node = this.standable(x, y + dy, z);
            if (node != null) {
                return node;
            }
        }
        Pathfinder.Node closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (int[] direction : DIRECTIONS) {
            for (int dy : new int[]{0, -1}) {
                Pathfinder.Node node = this.standable(x + direction[0], y + dy, z + direction[1]);
                if (node != null) {
                    double ddx = node.x + 0.5 - fx;
                    double ddy = node.h - fy;
                    double ddz = node.z + 0.5 - fz;
                    double distance = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = node;
                    }
                }
            }
        }
        return closest;
    }

    private Pathfinder.Node standable(int x, int y, int z) {
        double floor = this.floor(x, y, z);
        if (Double.isNaN(floor)) {
            return null;
        }
        double h = y + floor;
        if (!this.clear(x, z, h, h + BODY)) {
            return null;
        }
        Pathfinder.Node node = new Pathfinder.Node(x, y, z, h);
        this.nodes.put(LongMap.pack(x, y, z), node);
        return node;
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

    static double floor(Pathfinder.Shapes source, int x, int y, int z) {
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

    static boolean clear(Pathfinder.Shapes source, int x, int z, double from, double to) {
        for (int cy = (int) Math.floor(from); cy < to; cy++) {
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

    /** Whether a block is one to keep away from. Cached per block, since registry lookups build strings. */
    static boolean hazard(BlockState state) {
        Block block = state.getBlock();
        Boolean cached = HAZARD_CACHE.get(block);
        if (cached == null) {
            cached = HAZARDS.contains(BuiltInRegistries.BLOCK.getKey(block).getPath());
            HAZARD_CACHE.put(block, cached);
        }
        return cached;
    }

    static Pathfinder.Shapes world(ClientLevel level) {
        LongMap<double[]> cache = new LongMap<>(4096);
        return (x, y, z) -> {
            long key = LongMap.pack(x, y, z);
            double[] shape = cache.get(key);
            if (shape == null) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (hazard(state)) {
                    shape = HAZARD;
                } else {
                    VoxelShape collision = state.getCollisionShape(level, pos);
                    shape = collision.isEmpty() ? EMPTY : new double[]{collision.min(Axis.Y), collision.max(Axis.Y)};
                }
                cache.put(key, shape);
            }
            return shape;
        };
    }

    /** Remembers every cell read during one search: the same cells are read many times by neighbouring moves. */
    static Pathfinder.Shapes cached(Pathfinder.Shapes source) {
        LongMap<double[]> cache = new LongMap<>(8192);
        return (x, y, z) -> {
            long key = LongMap.pack(x, y, z);
            double[] shape = cache.get(key);
            if (shape == null) {
                shape = source.get(x, y, z);
                if (shape == null) {
                    shape = EMPTY;
                }
                cache.put(key, shape);
            }
            return shape;
        };
    }

    private boolean isGoal(Pathfinder.Node node) {
        for (double[] goal : this.goals) {
            double dx = node.x + 0.5 - goal[0];
            double dz = node.z + 0.5 - goal[2];
            if (dx * dx + dz * dz <= this.goalRadius * this.goalRadius && Math.abs(node.h - goal[1]) <= 2.5) {
                return true;
            }
        }
        return false;
    }

    private double heuristic(Pathfinder.Node node) {
        double min = Double.MAX_VALUE;
        for (double[] goal : this.goals) {
            double dx = Math.abs(node.x + 0.5 - goal[0]);
            double dz = Math.abs(node.z + 0.5 - goal[2]);
            double flat = Math.max(dx, dz) + 0.4142 * Math.min(dx, dz);
            min = Math.min(min, Math.max(0.0, flat - this.goalRadius) + 0.5 * Math.abs(node.h - goal[1]));
        }
        return min;
    }

    private double distanceToGoal(Pathfinder.Node node) {
        double min = Double.MAX_VALUE;
        for (double[] goal : this.goals) {
            double dx = node.x + 0.5 - goal[0];
            double dy = node.h - goal[1];
            double dz = node.z + 0.5 - goal[2];
            min = Math.min(min, dx * dx + dy * dy + dz * dz);
        }
        return Math.sqrt(min);
    }

    // ---- binary heap with decrease-key (no duplicate entries) ----

    private void push(Pathfinder.Node node) {
        if (this.heapSize == this.heap.length) {
            this.heap = java.util.Arrays.copyOf(this.heap, this.heapSize * 2);
        }
        this.heap[this.heapSize] = node;
        node.heapIndex = this.heapSize;
        this.siftUp(this.heapSize++);
    }

    private Pathfinder.Node pop() {
        if (this.heapSize == 0) {
            return null;
        }
        Pathfinder.Node top = this.heap[0];
        top.heapIndex = -1;
        Pathfinder.Node last = this.heap[--this.heapSize];
        this.heap[this.heapSize] = null;
        if (this.heapSize > 0) {
            this.heap[0] = last;
            last.heapIndex = 0;
            this.siftDown(0);
        }
        return top;
    }

    private void siftUp(int i) {
        Pathfinder.Node node = this.heap[i];
        while (i > 0) {
            int parent = i - 1 >> 1;
            Pathfinder.Node above = this.heap[parent];
            if (!before(node, above)) {
                break;
            }
            this.heap[i] = above;
            above.heapIndex = i;
            i = parent;
        }
        this.heap[i] = node;
        node.heapIndex = i;
    }

    private void siftDown(int i) {
        Pathfinder.Node node = this.heap[i];
        int half = this.heapSize >> 1;
        while (i < half) {
            int child = 2 * i + 1;
            int right = child + 1;
            if (right < this.heapSize && before(this.heap[right], this.heap[child])) {
                child = right;
            }
            if (!before(this.heap[child], node)) {
                break;
            }
            this.heap[i] = this.heap[child];
            this.heap[i].heapIndex = i;
            i = child;
        }
        this.heap[i] = node;
        node.heapIndex = i;
    }

    /** Lower f first; on ties the cell closer to the goal, which keeps A* from fanning out over flat ground. */
    private static boolean before(Pathfinder.Node a, Pathfinder.Node b) {
        return a.f < b.f || a.f == b.f && a.hCost < b.hCost;
    }

    private void reweigh(double weight) {
        this.weight = weight;
        for (int i = 0; i < this.heapSize; i++) {
            Pathfinder.Node node = this.heap[i];
            node.f = node.g + weight * node.hCost;
        }
        for (int i = (this.heapSize >> 1) - 1; i >= 0; i--) {
            this.siftDown(i);
        }
    }

    // ---- straight-line checks shared with the walker ----

    static boolean walkable(Pathfinder.Shapes source, Vec3 from, Vec3 to) {
        return walkable(source, new double[]{from.x, from.y, from.z}, new double[]{to.x, to.y, to.z}, STEP);
    }

    /**
     * Whether a straight walk from one point to another stays on floor the whole way, with head room, no
     * hazards, and height changes of at most {@code stepTolerance} between samples 0.3 apart. Both sides of
     * the body (0.3 off the line) are checked too.
     */
    static boolean walkable(Pathfinder.Shapes source, double[] from, double[] to, double stepTolerance) {
        if (Math.abs(from[1] - to[1]) > MAX_STRAIGHT * STEP) {
            return false;
        }
        double dx = to[0] - from[0];
        double dz = to[2] - from[2];
        double length = Math.hypot(dx, dz);
        if (length < 0.1) {
            return Math.abs(from[1] - to[1]) <= stepTolerance;
        }
        double px = -dz / length * 0.3;
        double pz = dx / length * 0.3;
        double height = from[1];
        double t = 0.0;
        while (true) {
            double x = from[0] + dx * t / length;
            double z = from[2] + dz * t / length;
            double ground = groundNear(source, (int) Math.floor(x), (int) Math.floor(z), height, stepTolerance);
            if (Double.isNaN(ground)) {
                return false;
            }
            for (int side = -1; side <= 1; side += 2) {
                if (Double.isNaN(groundNear(source, (int) Math.floor(x + px * side), (int) Math.floor(z + pz * side), ground, stepTolerance))) {
                    return false;
                }
            }
            height = ground;
            if (t >= length) {
                return Math.abs(ground - to[1]) <= stepTolerance;
            }
            t = Math.min(length, t + 0.3);
        }
    }

    private static double groundNear(Pathfinder.Shapes source, int x, int z, double near, double tolerance) {
        int base = (int) Math.floor(near + 0.01);
        double best = Double.NaN;
        for (int cy = base - 1; cy <= base + 1; cy++) {
            double floor = floor(source, x, cy, z);
            if (!Double.isNaN(floor)) {
                double h = cy + floor;
                if (Math.abs(h - near) <= tolerance + 1.0E-6 && clear(source, x, z, h, h + BODY)
                    && (Double.isNaN(best) || Math.abs(h - near) < Math.abs(best - near))) {
                    best = h;
                }
            }
        }
        return best;
    }

    static boolean standsAt(Pathfinder.Shapes source, int x, int y, int z, double h) {
        double floor = floor(source, x, y, z);
        return !Double.isNaN(floor) && Math.abs(y + floor - h) < 0.01 && clear(source, x, z, h, h + BODY);
    }

    private static final class Node {
        final int x;
        final int y;
        final int z;
        final double h;
        double g = Double.MAX_VALUE;
        double f;
        double hCost = Double.NaN;
        double danger = Double.NaN;
        int heapIndex = -1;
        Pathfinder.Node parent;
        boolean closed;
        boolean gap;

        Node(int x, int y, int z, double h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.h = h;
        }

        double[] point() {
            return new double[]{this.x + 0.5, this.h, this.z + 0.5};
        }
    }

    interface Shapes {
        double[] get(int x, int y, int z);
    }
}
