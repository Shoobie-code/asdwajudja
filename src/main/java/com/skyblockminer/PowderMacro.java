package com.skyblockminer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class PowderMacro {
    private static final int AHEAD = 3;
    private static final long STEP_TIMEOUT_MS = 2000L;
    private static final int MAX_TURNS = 4;
    private final Random random = new Random();
    private Direction heading;
    private BlockPos origin;
    private int radius = 32;
    private BlockPos stepTo;
    private long stepAt;
    private int turns;
    private int idleTicks;
    private int steps;
    private boolean keysHeld;

    void reset() {
        this.heading = null;
        this.origin = null;
        this.stepTo = null;
        this.turns = 0;
        this.steps = 0;
    }

    Direction heading() {
        return this.heading;
    }

    void releaseKeys(Minecraft mc) {
        if (this.keysHeld) {
            mc.options.keyUp.setDown(false);
            this.keysHeld = false;
        }
    }

    String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        this.radius = Math.max(4, macro.config.powderRadius);
        if (this.origin == null) {
            this.heading = player.getDirection();
            this.origin = player.blockPosition();
            macro.message("Tunnelling " + this.heading.getName() + ", staying within " + macro.config.powderRadius + " blocks of here.");
        }

        int tool = Inv.hotbar(player, Inv::isMiningTool);
        if (tool < 0) {
            macro.stop("No pickaxe or drill in your hotbar");
            return "Stopped";
        } else {
            if (player.getInventory().getSelectedSlot() != tool) {
                macro.selectSlot(tool);
            }

            BlockPos here = player.blockPosition();
            if (this.stepTo != null) {
                return this.step(macro, mc, player, here);
            } else {
                Map<String, Integer> costs = Targets.powder();
                int width = Math.max(0, Math.min(2, macro.config.powderWidth));
                String status = macro.mining
                    .tick(mc, player, level, costs, macro.config, macro.rotator, pos -> this.inTunnel(here, pos, width) && !touchesLava(level, pos));
                if (macro.mining.target() != null) {
                    this.idleTicks = 0;
                    return "Tunnelling " + this.heading.getName() + ": " + status;
                } else if (++this.idleTicks < 3) {
                    return "Tunnelling " + this.heading.getName();
                } else {
                    this.idleTicks = 0;
                    BlockPos next = here.relative(this.heading);
                    if (canStep(level, next) && this.inside(next)) {
                        macro.mining.release(mc);
                        this.stepTo = next;
                        this.stepAt = System.currentTimeMillis();
                        return "Stepping " + this.heading.getName();
                    } else {
                        return this.turn(macro, level, here);
                    }
                }
            }
        }
    }

    private String step(Macro macro, Minecraft mc, LocalPlayer player, BlockPos here) {
        Vec3 pos = player.position();
        Vec3 target = Vec3.atBottomCenterOf(this.stepTo);
        double along = (target.x - pos.x) * this.heading.getStepX() + (target.z - pos.z) * this.heading.getStepZ();
        if (!(along < 0.15) && System.currentTimeMillis() - this.stepAt <= 2000L) {
            Vec3 look = new Vec3(target.x + this.heading.getStepX() * 2, player.getEyePosition().y - 0.35, target.z + this.heading.getStepZ() * 2);
            macro.rotator.follow(player, look, Math.min(1.0, macro.config.rotationSpeed / 100.0 * 1.2));
            mc.options.keyUp.setDown(true);
            this.keysHeld = true;
            return "Stepping " + this.heading.getName();
        } else {
            this.releaseKeys(mc);
            if (here.equals(this.stepTo) || along < 0.15) {
                this.steps++;
                this.turns = 0;
            }

            this.stepTo = null;
            return "Tunnelling " + this.heading.getName();
        }
    }

    private String turn(Macro macro, ClientLevel level, BlockPos here) {
        if (++this.turns > 4) {
            macro.stop("The powder macro is boxed in (walls, holes or the region edge on every side)");
            return "Stopped";
        } else {
            List<Direction> sides = new ArrayList<>(List.of(this.heading.getClockWise(), this.heading.getCounterClockWise()));
            Collections.shuffle(sides, this.random);
            sides.sort((a, b) -> Boolean.compare(!this.towardOrigin(here, a), !this.towardOrigin(here, b)));
            sides.add(this.heading.getOpposite());
            Map<String, Integer> costs = Targets.powder();

            for (Direction side : sides) {
                BlockPos next = here.relative(side);
                if (this.inside(next) && !touchesLava(level, next) && !touchesLava(level, next.above())) {
                    boolean mineable = costs.containsKey(TargetFinder.blockId(level, next)) || costs.containsKey(TargetFinder.blockId(level, next.above()));
                    if (mineable || canStep(level, next)) {
                        this.heading = side;
                        return "Turning " + side.getName();
                    }
                }
            }

            this.heading = this.heading.getOpposite();
            return "Turning around";
        }
    }

    private boolean inTunnel(BlockPos here, BlockPos pos, int width) {
        int dx = pos.getX() - here.getX();
        int dz = pos.getZ() - here.getZ();
        int forward = dx * this.heading.getStepX() + dz * this.heading.getStepZ();
        int side = Math.abs(dx * this.heading.getStepZ() - dz * this.heading.getStepX());
        int up = pos.getY() - here.getY();
        if (forward < 0 || forward > 3 || side > width) {
            return false;
        } else {
            return forward == 0 && side == 0 ? false : up >= 0 && up <= (width > 0 ? 2 : 1);
        }
    }

    private boolean inside(BlockPos pos) {
        return Math.abs(pos.getX() - this.origin.getX()) <= this.radius && Math.abs(pos.getZ() - this.origin.getZ()) <= this.radius;
    }

    private boolean towardOrigin(BlockPos here, Direction side) {
        int dx = this.origin.getX() - here.getX();
        int dz = this.origin.getZ() - here.getZ();
        return dx * side.getStepX() + dz * side.getStepZ() > 0;
    }

    private static boolean canStep(ClientLevel level, BlockPos pos) {
        return open(level, pos) && open(level, pos.above()) && solidFloor(level, pos.below()) && !touchesLava(level, pos) && !touchesLava(level, pos.above());
    }

    private static boolean open(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean solidFloor(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() && !state.is(Blocks.MAGMA_BLOCK);
    }

    private static boolean touchesLava(ClientLevel level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.LAVA)) {
            return true;
        } else {
            for (Direction direction : Direction.values()) {
                if (level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                    return true;
                }
            }

            return false;
        }
    }

    int steps() {
        return this.steps;
    }
}
