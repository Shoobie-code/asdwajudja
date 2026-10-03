package com.skyblockminer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a {@link BuildPlan}: places the build block (and water rows) by right-clicking the face of a solid
 * neighbour, nearest first, and walks with the pathfinder when nothing placeable is in reach. Water goes in
 * last so it does not flood spots that still need blocks.
 */
final class FarmBuilder implements Routine {
    private static final int MAX_AIM_TICKS = 25;
    private static final long SKIP_MS = 15_000L;

    private List<BuildPlan.Target> targets = List.of();
    private final java.util.Map<BuildPlan.Target, Long> skipped = new java.util.HashMap<>();
    private BuildPlan.Target current;
    private BlockPos support;
    private Direction face;
    private Vec3 aim;
    private int aimTicks;
    private long waitUntil;
    private boolean walking;
    private int placed;
    private int remaining;

    @Override
    public void reset() {
        this.targets = List.of();
        this.skipped.clear();
        this.current = null;
        this.walking = false;
        this.placed = 0;
        this.remaining = 0;
    }

    @Override
    public void resume() {
        this.current = null;
        this.walking = false;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        MinerConfig config = macro.config;
        if (config.buildPos1 == null || config.buildPos2 == null) {
            macro.stop("Set both build corners first (/sm build pos1, /sm build pos2)");
            return "No area";
        }
        if (this.targets.isEmpty()) {
            List<BuildPlan.Target> plan = new ArrayList<>(BuildPlan.plan(config.buildPos1, config.buildPos2, config.buildPattern, config.buildWaterEvery));
            plan.sort(Comparator.comparing(BuildPlan.Target::water));
            this.targets = plan;
        }
        long now = System.currentTimeMillis();
        if (now < this.waitUntil) {
            return this.progress();
        }

        if (this.walking) {
            if (this.pickPlaceable(player, level, macro.mining.reach(player, config))) {
                macro.walker.stop(mc);
                this.walking = false;
            } else {
                PathWalker.State state = macro.walker.tick(mc, player, macro.rotator, config);
                if (state == PathWalker.State.DONE || state == PathWalker.State.FAILED) {
                    this.walking = false;
                    if (state == PathWalker.State.FAILED && this.current != null) {
                        this.skipped.put(this.current, now);
                    }
                    this.current = null;
                }
                return "Walking to the next spot (" + this.remaining + " left)";
            }
        }

        if (this.current == null || this.done(level, this.current)) {
            this.current = null;
            if (!this.pickPlaceable(player, level, macro.mining.reach(player, config))) {
                BuildPlan.Target next = this.nearestUndone(player, level, now);
                if (next == null) {
                    macro.stop(this.remaining == 0 ? "Build finished (" + this.placed + " placed)" : "Nothing left that can be reached");
                    return "Done";
                }
                this.current = next;
                this.walking = true;
                macro.walker.go(mc, List.of(new Vec3(next.x() + 0.5, next.y() + 1, next.z() + 0.5)), 3.0);
                return "Walking to the next spot";
            }
        }

        String item = this.current.water() ? "water bucket" : config.buildBlock.toLowerCase();
        int slot = Inv.hotbarNamed(player, List.of(item));
        if (slot < 0) {
            macro.stop("Out of " + (this.current.water() ? "water buckets" : config.buildBlock) + " in the hotbar");
            return "Out of blocks";
        }
        if (player.getInventory().getSelectedSlot() != slot) {
            macro.selectSlot(slot);
            this.waitUntil = now + 80L;
            return this.progress();
        }

        macro.rotator.track(player, this.aim, config.rotationSpeed / 100.0);
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
            && block.getBlockPos().equals(this.support) && block.getDirection() == this.face) {
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, block);
            player.swing(InteractionHand.MAIN_HAND);
            this.placed++;
            this.current = null;
            this.waitUntil = now + Math.max(50, config.buildDelay);
        } else if (++this.aimTicks > MAX_AIM_TICKS) {
            this.skipped.put(this.current, now);
            this.current = null;
        }
        return this.progress();
    }

    private String progress() {
        return "Building (" + this.placed + " placed, " + this.remaining + " left)";
    }

    /** Picks the nearest unbuilt target in reach that has a visible solid face to place against. */
    private boolean pickPlaceable(LocalPlayer player, ClientLevel level, double reach) {
        Vec3 eye = player.getEyePosition();
        BlockPos feet = player.blockPosition();
        long now = System.currentTimeMillis();
        this.skipped.values().removeIf(at -> now - at > SKIP_MS);
        double best = Double.MAX_VALUE;
        int left = 0;
        BuildPlan.Target bestTarget = null;
        BlockPos bestSupport = null;
        Direction bestFace = null;
        Vec3 bestAim = null;
        for (BuildPlan.Target target : this.targets) {
            if (this.done(level, target)) {
                continue;
            }
            left++;
            BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
            if (this.skipped.containsKey(target) || pos.equals(feet) || pos.equals(feet.above())) {
                continue;
            }
            double distance = Vec3.atCenterOf(pos).distanceToSqr(eye);
            if (distance > (reach + 1.0) * (reach + 1.0) || distance >= best) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = pos.relative(direction);
                if (!solid(level, neighbour)) {
                    continue;
                }
                Direction face = direction.getOpposite();
                Vec3 point = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.49, face.getStepY() * 0.49, face.getStepZ() * 0.49);
                if (point.distanceToSqr(eye) <= reach * reach && TargetFinder.reaches(player, level, eye, point, neighbour)) {
                    best = distance;
                    bestTarget = target;
                    bestSupport = neighbour;
                    bestFace = face;
                    bestAim = point;
                    break;
                }
            }
        }
        this.remaining = left;
        if (bestTarget == null) {
            return false;
        }
        this.current = bestTarget;
        this.support = bestSupport;
        this.face = bestFace;
        this.aim = bestAim;
        this.aimTicks = 0;
        return true;
    }

    private BuildPlan.Target nearestUndone(LocalPlayer player, ClientLevel level, long now) {
        Vec3 pos = player.position();
        BuildPlan.Target best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BuildPlan.Target target : this.targets) {
            if (this.skipped.containsKey(target) || this.done(level, target)) {
                continue;
            }
            double d = pos.distanceToSqr(new Vec3(target.x() + 0.5, target.y(), target.z() + 0.5));
            if (d < bestDistance) {
                bestDistance = d;
                best = target;
            }
        }
        return best;
    }

    private boolean done(ClientLevel level, BuildPlan.Target target) {
        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
        if (target.water()) {
            return TargetFinder.blockId(level, pos).equals("minecraft:water");
        }
        return solid(level, pos);
    }

    private static boolean solid(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    @Override
    public String hudLine(Macro macro) {
        return String.format("%d placed, %d left (%.0f/min)", this.placed, this.remaining, this.placed / (macro.hours() * 60.0));
    }

    @Override
    public void render(Macro macro, LocalPlayer player) {
        if (macro.config.showTarget && this.current != null) {
            BlockPos pos = new BlockPos(this.current.x(), this.current.y(), this.current.z());
            Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(this.current.water() ? 0xFF3B82F6 : 0xFF55FF55, 2.0F, 0x2055FF55));
        }
    }
}
