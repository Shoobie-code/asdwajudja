package com.skyblockminer;

import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class PathWalker {
    private static final long UNSTICK_MS = 1500L;
    private static final long STUCK_MS = 3500L;
    private static final int MAX_REPATHS = 6;
    private static final int MAX_LEGS = 25;
    private static final double OFF_PATH = 3.0;
    private static final double WAYPOINT_REACHED = 0.8;
    /** How many waypoints ahead the walker may cut straight to. */
    private static final int MAX_SKIP = 10;
    private static final int SKIP_CHECK_TICKS = 3;
    private final WorldMap map;
    private final Pathfinder finder = new Pathfinder();
    private final Random random = new Random();
    private PathWalker.State state = PathWalker.State.IDLE;
    private boolean pendingStart;
    private int searchWaitTicks;
    private List<Vec3> goals;
    private double radius;
    private List<Vec3> path;
    private int index;
    private int repaths;
    private int legs;
    private double repathRemaining;
    private Vec3 segmentStart;
    private double bestRemaining;
    private long lastProgressAt;
    private int jumpTicks;
    private double lookDrop;
    private boolean keysHeld;
    private int skipCheckIn;
    private String failure = "";

    PathWalker(WorldMap map) {
        this.map = map;
    }

    PathWalker.State state() {
        return this.state;
    }

    boolean busy() {
        return this.state == PathWalker.State.SEARCHING || this.state == PathWalker.State.WALKING;
    }

    String failure() {
        return this.failure;
    }

    int remaining() {
        return this.path == null ? 0 : this.path.size() - this.index;
    }

    void go(Minecraft mc, List<Vec3> goals, double radius) {
        this.goals = goals;
        this.radius = radius;
        this.repaths = 0;
        this.legs = 0;
        this.repathRemaining = Double.MAX_VALUE;
        this.search(mc, "start");
    }

    void stop(Minecraft mc) {
        this.releaseKeys(mc);
        this.state = PathWalker.State.IDLE;
        this.path = null;
    }

    private void search(Minecraft mc, String why) {
        this.releaseKeys(mc);
        this.state = PathWalker.State.SEARCHING;
        this.pendingStart = true;
        this.searchWaitTicks = 0;
        if (!why.equals("start")) {
            MinerMod.LOGGER.info("Path: planning again ({})", why);
        }
    }

    private boolean beginSearch(Minecraft mc, LocalPlayer player) {
        if (mc.level == null) {
            return false;
        } else if (!player.onGround() && ++this.searchWaitTicks < 40) {
            return false;
        } else {
            this.pendingStart = false;
            if (!this.finder.start(this.map.shapes(mc.level), player.position(), this.goals, this.radius)) {
                this.fail(mc, "not standing on solid ground");
                return false;
            } else {
                return true;
            }
        }
    }

    PathWalker.State tick(Minecraft mc, LocalPlayer player, Rotator rotator, MinerConfig config) {
        if (this.state == PathWalker.State.SEARCHING) {
            if (this.pendingStart && !this.beginSearch(mc, player)) {
                return this.state;
            }

            if (!this.finder.step()) {
                return this.state;
            }

            this.path = this.finder.result();
            MinerMod.LOGGER
                .info(
                    "Path: {} after exploring {} cells in {} ms{}",
                    new Object[]{
                        this.path == null ? "none" : this.path.size() + " waypoints",
                        this.finder.explored(),
                        this.finder.elapsedMs(),
                        this.finder.partial() ? " (partial: the goal is beyond what is known)" : ""
                    }
                );
            if (this.path == null || this.path.isEmpty()) {
                this.fail(mc, "no path found");
                return this.state;
            }

            this.index = 0;
            this.skipCheckIn = 0;
            this.segmentStart = player.position();
            this.bestRemaining = Double.MAX_VALUE;
            this.lastProgressAt = System.currentTimeMillis();
            this.lookDrop = 0.25 + this.random.nextDouble() * 0.6;
            this.state = PathWalker.State.WALKING;
        }

        if (this.state != PathWalker.State.WALKING) {
            return this.state;
        } else {
            ClientLevel level = mc.level;
            Vec3 pos = player.position();
            if (player.onGround() && this.inGoal(pos) && !this.finder.partial()) {
                this.releaseKeys(mc);
                this.state = PathWalker.State.DONE;
                return this.state;
            } else {
                int before = this.index;

                while (this.index < this.path.size() - 1 && reached(pos, this.path.get(this.index))) {
                    this.index++;
                }

                if (player.onGround() && --this.skipCheckIn <= 0) {
                    this.skipCheckIn = SKIP_CHECK_TICKS;
                    this.index = this.farthestReachable(Pathfinder.world(level), pos);
                }

                if (this.index != before) {
                    this.segmentStart = pos;
                }

                Vec3 target = this.path.get(this.index);
                boolean last = this.index == this.path.size() - 1;
                double horizontal = horizontal(pos, target);
                if (last && horizontal < 0.5 && Math.abs(pos.y - target.y) < 1.3) {
                    this.releaseKeys(mc);
                    if (this.finder.partial() && this.legs < 25) {
                        this.legs++;
                        this.search(mc, "continuing a partial path");
                        return this.state;
                    } else {
                        this.state = PathWalker.State.DONE;
                        return this.state;
                    }
                } else {
                    Vec3 ahead = horizontal < 1.5 && !last ? this.path.get(this.index + 1) : target;
                    Vec3 look = new Vec3(ahead.x, player.getEyePosition().y - this.lookDrop, ahead.z);
                    rotator.follow(player, look, Math.min(1.0, config.rotationSpeed / 100.0 * 1.2));
                    float wantYaw = Rotator.anglesTo(player, target)[0];
                    double yawError = Math.abs(Mth.wrapDegrees(wantYaw - player.getYRot()));
                    boolean forward = yawError < 60.0;
                    long now = System.currentTimeMillis();
                    boolean climb = player.onGround() && target.y - pos.y > 0.6 && horizontal < 1.8;
                    if (now - this.lastProgressAt > 1500L && player.onGround() && this.jumpTicks == 0) {
                        this.jumpTicks = 4;
                    }

                    boolean jump = climb || player.onGround() && player.horizontalCollision && forward || this.jumpTicks > 0;
                    if (this.jumpTicks > 0) {
                        this.jumpTicks--;
                    }

                    boolean sprint = config.sprint && forward && yawError < 15.0 && !jump && horizontal > 4.0;
                    this.setKeys(mc, forward, sprint, jump);
                    double remaining = this.remainingLength(pos);
                    if (remaining < this.bestRemaining - 0.5) {
                        this.bestRemaining = remaining;
                        this.lastProgressAt = now;
                    }

                    if (remaining < this.repathRemaining - 10.0) {
                        this.repaths = 0;
                        this.repathRemaining = remaining;
                    }

                    boolean offPath = offSegment(pos, this.segmentStart, target) > 3.0 || pos.y < Math.min(this.segmentStart.y, target.y) - 3.0;
                    if (offPath || now - this.lastProgressAt > 3500L) {
                        String why = offPath ? "knocked off the path" : "stuck";
                        if (++this.repaths > 6) {
                            this.fail(mc, "kept getting " + why);
                        } else {
                            this.repathRemaining = remaining;
                            this.search(mc, why);
                        }
                    }

                    return this.state;
                }
            }
        }
    }

    /**
     * The farthest upcoming waypoint the player can walk to in a straight line from where they stand
     * (solid floor the whole way, head room, no hazards, no drops), checked farthest first. Waypoints are
     * only skipped when that line is actually walkable; otherwise the current one is kept.
     */
    private int farthestReachable(Pathfinder.Shapes live, Vec3 pos) {
        int last = Math.min(this.path.size() - 1, this.index + MAX_SKIP);
        for (int candidate = last; candidate > this.index; candidate--) {
            if (Pathfinder.walkable(live, pos, this.path.get(candidate))) {
                return candidate;
            }
        }
        return this.index;
    }

    private void fail(Minecraft mc, String why) {
        this.releaseKeys(mc);
        this.failure = why;
        this.state = PathWalker.State.FAILED;
        MinerMod.LOGGER.info("Path: gave up ({})", why);
    }

    private boolean inGoal(Vec3 pos) {
        double r = Math.max(this.radius, 0.6);

        for (Vec3 goal : this.goals) {
            if (horizontal(pos, goal) <= r && Math.abs(pos.y - goal.y) <= 1.5) {
                return true;
            }
        }

        return false;
    }

    private static boolean reached(Vec3 pos, Vec3 node) {
        return horizontal(pos, node) < 0.8 && Math.abs(pos.y - node.y) < 1.3;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private double remainingLength(Vec3 pos) {
        double length = pos.distanceTo(this.path.get(this.index));

        for (int i = this.index; i < this.path.size() - 1; i++) {
            length += this.path.get(i).distanceTo(this.path.get(i + 1));
        }

        return length;
    }

    static double offSegment(Vec3 pos, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, ((pos.x - from.x) * dx + (pos.z - from.z) * dz) / lengthSq));
        return Math.hypot(pos.x - (from.x + dx * t), pos.z - (from.z + dz * t));
    }

    private void setKeys(Minecraft mc, boolean forward, boolean sprint, boolean jump) {
        mc.options.keyUp.setDown(forward);
        mc.options.keySprint.setDown(sprint);
        mc.options.keyJump.setDown(jump);
        this.keysHeld = true;
    }

    void releaseKeys(Minecraft mc) {
        if (this.keysHeld) {
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyJump.setDown(false);
            this.keysHeld = false;
        }
    }

    static enum State {
        IDLE,
        SEARCHING,
        WALKING,
        DONE,
        FAILED;
    }
}
