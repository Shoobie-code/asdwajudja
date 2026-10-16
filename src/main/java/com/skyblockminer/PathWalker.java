package com.skyblockminer;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Follows a {@link Pathfinder} path: walks, cuts corners, jumps gaps, re-plans when stuck or knocked off, and
 * (when the movement setting allows and the item is in the hotbar) teleports along the path with etherwarp
 * or Instant Transmission to skip long stretches.
 */
final class PathWalker {
    private static final long STUCK_MS = 3500L;
    private static final long UNSTICK_MS = 1500L;
    private static final int MAX_REPATHS = 6;
    private static final int MAX_LEGS = 25;
    private static final double OFF_PATH = 3.0;
    private static final double WAYPOINT_REACHED = 0.8;
    /** Before a gap jump the walker lines up on the take-off cell more closely. */
    private static final double TAKEOFF_REACHED = 0.4;
    /** How many waypoints ahead the walker may cut straight to. */
    private static final int MAX_SKIP = 10;
    private static final int SKIP_CHECK_TICKS = 3;
    private static final int HOP_CHECK_TICKS = 5;
    private static final long HOP_AIM_MS = 2500L;
    private static final long HOP_WAIT_MS = 1200L;
    private static final int MAX_HOP_FAILS = 3;
    private final WorldMap map;
    private final Host host;
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
    private boolean sneakHeld;
    private int skipCheckIn;
    private String failure = "";
    // Teleporting
    private Hop hop;
    private int hopCheckIn;
    private int rawIndex;
    private int hopFails;
    private boolean hopsOff;
    private final Set<Integer> failedHops = new HashSet<>();
    private int hops;

    /** What the walker needs from the running macro. */
    interface Host {
        /** The next teleport within this many ms is ours, not a staff check. */
        void expectTeleport(long ms);

        void selectSlot(int slot);
    }

    PathWalker(WorldMap map, Host host) {
        this.map = map;
        this.host = host;
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

    /** Teleports used since the last {@link #go}. */
    int hops() {
        return this.hops;
    }

    boolean teleporting() {
        return this.hop != null;
    }

    void go(Minecraft mc, List<Vec3> goals, double radius) {
        this.goals = goals;
        this.radius = radius;
        this.repaths = 0;
        this.legs = 0;
        this.repathRemaining = Double.MAX_VALUE;
        this.finder.clearAvoid();
        this.hopFails = 0;
        this.hopsOff = false;
        this.hops = 0;
        this.endHop(mc);
        this.search(mc, "start");
    }

    void stop(Minecraft mc) {
        this.endHop(mc);
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
        }
        this.pendingStart = false;
        if (!this.finder.start(this.map.shapes(mc.level), player.position(), this.goals, this.radius)) {
            this.fail(mc, "not standing on solid ground");
            return false;
        }
        return true;
    }

    PathWalker.State tick(Minecraft mc, LocalPlayer player, Rotator rotator, MinerConfig config) {
        this.finder.setSafety(config.pathSafety);
        if (this.state == PathWalker.State.SEARCHING) {
            if (this.pendingStart && !this.beginSearch(mc, player)) {
                return this.state;
            }
            if (!this.finder.step()) {
                return this.state;
            }
            this.path = this.finder.result();
            MinerMod.LOGGER.info(
                "Path: {} after exploring {} cells in {} ms{}",
                this.path == null ? "none" : this.path.size() + " waypoints",
                this.finder.explored(),
                this.finder.elapsedMs(),
                this.finder.partial() ? " (partial: the goal is beyond what is known)" : ""
            );
            if (this.path == null || this.path.isEmpty()) {
                this.fail(mc, "no path found");
                return this.state;
            }
            this.index = 0;
            this.rawIndex = 0;
            this.skipCheckIn = 0;
            this.hopCheckIn = 0;
            this.failedHops.clear();
            this.segmentStart = player.position();
            this.bestRemaining = Double.MAX_VALUE;
            this.lastProgressAt = System.currentTimeMillis();
            this.lookDrop = 0.25 + this.random.nextDouble() * 0.6;
            this.state = PathWalker.State.WALKING;
        }

        if (this.state != PathWalker.State.WALKING) {
            return this.state;
        }
        if (this.hop != null) {
            this.tickHop(mc, player, rotator, config);
            return this.state;
        }
        ClientLevel level = mc.level;
        Vec3 pos = player.position();
        if (player.onGround() && this.inGoal(pos) && !this.finder.partial()) {
            this.releaseKeys(mc);
            this.state = PathWalker.State.DONE;
            return this.state;
        }
        int before = this.index;
        while (this.index < this.path.size() - 1
            && reached(pos, this.path.get(this.index), this.finder.gapAt(this.index + 1) ? TAKEOFF_REACHED : WAYPOINT_REACHED)) {
            this.index++;
        }
        Pathfinder.Shapes live = null;
        if (player.onGround() && --this.skipCheckIn <= 0) {
            this.skipCheckIn = SKIP_CHECK_TICKS;
            live = this.map.shapes(level);
            this.index = this.farthestReachable(live, pos);
        }
        if (this.index != before) {
            this.segmentStart = pos;
        }

        if (player.onGround() && --this.hopCheckIn <= 0) {
            this.hopCheckIn = HOP_CHECK_TICKS;
            if (this.planHop(mc, player, level, live == null ? this.map.shapes(level) : live, config)) {
                this.releaseKeys(mc);
                this.tickHop(mc, player, rotator, config);
                return this.state;
            }
        }

        Vec3 target = this.path.get(this.index);
        boolean last = this.index == this.path.size() - 1;
        boolean gap = this.finder.gapAt(this.index);
        double horizontal = horizontal(pos, target);
        if (last && horizontal < 0.5 && Math.abs(pos.y - target.y) < 1.3) {
            this.releaseKeys(mc);
            if (this.finder.partial() && this.legs < MAX_LEGS) {
                this.legs++;
                this.search(mc, "continuing a partial path");
            } else {
                this.state = PathWalker.State.DONE;
            }
            return this.state;
        }
        Vec3 ahead = horizontal < 1.5 && !last && !gap ? this.path.get(this.index + 1) : target;
        Vec3 look = new Vec3(ahead.x, player.getEyePosition().y - this.lookDrop, ahead.z);
        rotator.follow(player, look, Math.min(1.0, config.rotationSpeed / 100.0 * 1.2));
        float wantYaw = Rotator.anglesTo(player, target)[0];
        double yawError = Math.abs(Mth.wrapDegrees(wantYaw - player.getYRot()));
        boolean forward = yawError < (gap ? 20.0 : 60.0);
        long now = System.currentTimeMillis();
        boolean climb = player.onGround() && target.y - pos.y > 0.6 && horizontal < 1.8;
        // A gap jump: sprint and jump from the edge once lined up with the landing.
        boolean leap = gap && player.onGround() && horizontal < 2.4 && yawError < 12.0;
        if (now - this.lastProgressAt > UNSTICK_MS && player.onGround() && this.jumpTicks == 0) {
            this.jumpTicks = 4;
        }
        boolean jump = climb || leap || player.onGround() && player.horizontalCollision && forward || this.jumpTicks > 0;
        if (this.jumpTicks > 0) {
            this.jumpTicks--;
        }
        boolean sprint = config.sprint && forward && (leap || gap || yawError < 15.0 && !jump && horizontal > 4.0);
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
        boolean offPath = offSegment(pos, this.segmentStart, target) > OFF_PATH || pos.y < Math.min(this.segmentStart.y, target.y) - 3.0;
        if (offPath || now - this.lastProgressAt > STUCK_MS) {
            String why = offPath ? "knocked off the path" : "stuck";
            if (++this.repaths > MAX_REPATHS) {
                this.fail(mc, "kept getting " + why);
            } else {
                if (!offPath) {
                    // Remember where we got stuck so the next path goes another way.
                    double dx = target.x - pos.x;
                    double dz = target.z - pos.z;
                    double length = Math.max(0.01, Math.hypot(dx, dz));
                    this.finder.avoid((int) Math.floor(pos.x + dx / length * 0.9), (int) Math.floor(pos.y + 0.01),
                        (int) Math.floor(pos.z + dz / length * 0.9));
                }
                this.repathRemaining = remaining;
                this.search(mc, why);
            }
        }
        return this.state;
    }

    // ---- teleports ----

    private enum HopKind {
        ETHERWARP("Etherwarp"),
        TRANSMISSION("Instant Transmission");

        final String label;

        HopKind(String label) {
            this.label = label;
        }
    }

    private static final class Hop {
        final HopKind kind;
        final int rawIndex;
        final BlockPos block;
        final Vec3 aim;
        final int slot;
        int previousSlot = -1;
        boolean waiting;
        long at = System.currentTimeMillis();
        int settle;
        Vec3 from;

        Hop(HopKind kind, int rawIndex, BlockPos block, Vec3 aim, int slot) {
            this.kind = kind;
            this.rawIndex = rawIndex;
            this.block = block;
            this.aim = aim;
            this.slot = slot;
        }
    }

    static int etherwarpSlot(LocalPlayer player) {
        return Inv.hotbar(player, stack -> {
            String name = Inv.name(stack);
            return name.contains("Aspect of the Void") || name.contains("Etherwarp Conduit");
        });
    }

    static int transmissionSlot(LocalPlayer player) {
        return Inv.hotbar(player, stack -> {
            String name = Inv.name(stack);
            return name.contains("Aspect of the Void") || name.contains("Aspect of the End");
        });
    }

    /** Looks for a teleport along the path that saves enough walking, and starts it if one is found. */
    private boolean planHop(Minecraft mc, LocalPlayer player, ClientLevel level, Pathfinder.Shapes shapes, MinerConfig config) {
        String mode = config.movement;
        if (this.hopsOff || mode.equals("walk") || this.finder.raw() == null) {
            return false;
        }
        List<double[]> raw = this.finder.raw();
        Vec3 pos = player.position();
        double[] feet = {pos.x, pos.y, pos.z};
        this.rawIndex = WarpPlanner.nearest(raw, feet, this.rawIndex, 80);
        boolean ether = mode.equals("etherwarp") || mode.equals("both");
        boolean transmission = mode.equals("transmission") || mode.equals("both");
        if (ether) {
            int slot = etherwarpSlot(player);
            if (slot >= 0) {
                Vec3 eyePos = player.getEyePosition();
                double[] eye = {eyePos.x, eyePos.y, eyePos.z};
                int j = WarpPlanner.etherwarpHop(shapes, eye, raw, this.rawIndex, config.etherwarpRange, config.warpMinGain,
                    this.failedHops::contains);
                if (j >= 0) {
                    int[] b = WarpPlanner.blockUnder(raw.get(j));
                    BlockPos block = new BlockPos(b[0], b[1], b[2]);
                    // Confirm against the real world: grass, flowers and other blocks without collision still stop the ray.
                    Vec3 aim = RouteMiner.warpPoint(player, level, block, config.etherwarpRange);
                    if (aim != null) {
                        this.hop = new Hop(HopKind.ETHERWARP, j, block, aim, slot);
                        return true;
                    }
                    this.failedHops.add(j);
                }
            }
        }
        if (transmission) {
            int slot = transmissionSlot(player);
            double[] along = WarpPlanner.lengths(raw);
            double left = along[along.length - 1] - along[this.rawIndex];
            if (slot >= 0 && left > config.transmissionRange + 3.0) {
                double[] land = WarpPlanner.transmissionHop(shapes, feet, raw, this.rawIndex, config.transmissionRange);
                if (land != null) {
                    // Look level, toward the landing point, so the hop keeps its height.
                    Vec3 aim = new Vec3(land[0], player.getEyePosition().y, land[2]);
                    this.hop = new Hop(HopKind.TRANSMISSION, -1, null, aim, slot);
                    return true;
                }
            }
        }
        return false;
    }

    private void tickHop(Minecraft mc, LocalPlayer player, Rotator rotator, MinerConfig config) {
        Hop hop = this.hop;
        long now = System.currentTimeMillis();
        if (hop.waiting) {
            if (player.position().distanceTo(hop.from) > 2.0) {
                this.hops++;
                this.hopFails = 0;
                MinerMod.LOGGER.info("Path: {} hop {} ({} blocks)", hop.kind.label, this.hops, String.format("%.1f", player.position().distanceTo(hop.from)));
                this.endHop(mc);
                this.search(mc, "after a teleport");
            } else if (now - hop.at > HOP_WAIT_MS) {
                this.hopFailed(mc, "no teleport happened");
            }
            return;
        }
        if (player.getInventory().getSelectedSlot() != hop.slot) {
            if (hop.previousSlot < 0) {
                hop.previousSlot = player.getInventory().getSelectedSlot();
            }
            this.host.selectSlot(hop.slot);
            return;
        }
        boolean ether = hop.kind == HopKind.ETHERWARP;
        if (ether) {
            mc.options.keyShift.setDown(true);
            this.sneakHeld = true;
        }
        rotator.track(player, hop.aim, Math.min(1.0, config.rotationSpeed / 100.0 * 1.3));
        boolean ready = rotator.settled(player, ether ? 1.0 : 2.0)
            && (!ether || player.isShiftKeyDown() && RouteMiner.looksAt(player, mc.level, hop.block, config.etherwarpRange + 2.0));
        if (ready && ++hop.settle >= 2 + this.random.nextInt(3)) {
            this.host.expectTeleport(2500L);
            hop.from = player.position();
            hop.waiting = true;
            hop.at = now;
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        } else if (now - hop.at > HOP_AIM_MS) {
            if (hop.rawIndex >= 0) {
                this.failedHops.add(hop.rawIndex);
            }
            MinerMod.LOGGER.info("Path: could not line up the {}, walking on", hop.kind.label);
            this.endHop(mc);
        }
    }

    private void hopFailed(Minecraft mc, String why) {
        Hop hop = this.hop;
        if (hop.rawIndex >= 0) {
            this.failedHops.add(hop.rawIndex);
        }
        if (++this.hopFails >= MAX_HOP_FAILS) {
            this.hopsOff = true;
            MinerMod.message(hop.kind.label + " is not working (out of mana?), walking the rest of the way", net.minecraft.ChatFormatting.YELLOW);
        }
        MinerMod.LOGGER.info("Path: {} failed ({})", hop.kind.label, why);
        this.endHop(mc);
    }

    private void endHop(Minecraft mc) {
        if (this.sneakHeld) {
            mc.options.keyShift.setDown(false);
            this.sneakHeld = false;
        }
        if (this.hop != null && this.hop.previousSlot >= 0) {
            this.host.selectSlot(this.hop.previousSlot);
        }
        this.hop = null;
        this.hopCheckIn = HOP_CHECK_TICKS;
        this.lastProgressAt = System.currentTimeMillis();
    }

    // ---- walking helpers ----

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

    private static boolean reached(Vec3 pos, Vec3 node, double within) {
        return horizontal(pos, node) < within && Math.abs(pos.y - node.y) < 1.3;
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
        if (this.sneakHeld) {
            mc.options.keyShift.setDown(false);
            this.sneakHeld = false;
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
