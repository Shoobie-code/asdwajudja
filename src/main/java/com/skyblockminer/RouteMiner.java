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
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class RouteMiner {
    private static final double ETHERWARP_RANGE = 57.0;
    private static final int MAX_WARP_TRIES = 3;
    private static final long POINT_LIMIT_MS = 120000L;
    private final Random random = new Random();
    private RouteMiner.Phase phase = RouteMiner.Phase.START;
    private long phaseAt;
    private int index = -1;
    private int laps;
    private int warpTries;
    private Vec3 aim;
    private Vec3 warpFrom;
    private int settleTicks;
    private int idleTicks;
    private boolean sneakHeld;

    void reset() {
        this.phase = RouteMiner.Phase.START;
        this.index = -1;
        this.laps = 0;
        this.aim = null;
    }

    int index() {
        return this.index;
    }

    int laps() {
        return this.laps;
    }

    RouteMiner.Phase phase() {
        return this.phase;
    }

    void releaseKeys(Minecraft mc) {
        if (this.sneakHeld) {
            mc.options.keyShift.setDown(false);
            this.sneakHeld = false;
        }
    }

    private void phase(RouteMiner.Phase next) {
        this.phase = next;
        this.phaseAt = System.currentTimeMillis();
        this.settleTicks = 0;
        this.idleTicks = 0;
    }

    String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        List<BlockPos> route = macro.routes.points();
        if (route.size() < 2) {
            macro.stop("The route needs at least 2 points: stand on each one and run /miner route add");
            return "Stopped";
        } else {
            if (this.index >= route.size()) {
                this.index = 0;
            }
            return switch (this.phase) {
                case START -> {
                    this.index = macro.routes.nearest(player.position());
                    this.travel(macro, mc, player, level);
                    yield "Starting at point " + (this.index + 1);
                }
                case AIM -> this.aim(macro, mc, player, level);
                case WARPING -> this.warping(macro, mc, player, level);
                case WALKING -> this.walking(macro, mc, player, level);
                case MINING -> this.mine(macro, mc, player, level);
            };
        }
    }

    private BlockPos point(Macro macro) {
        return macro.routes.points().get(this.index);
    }

    private void travel(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        BlockPos point = this.point(macro);
        macro.mining.reset(mc);
        if (standingOn(player, point)) {
            this.phase(RouteMiner.Phase.MINING);
        } else {
            this.warpTries = 0;
            this.aim = null;
            if (macro.config.etherwarp && warpSlot(player) >= 0) {
                this.phase(RouteMiner.Phase.AIM);
            } else {
                this.walk(macro, mc);
            }
        }
    }

    private void walk(Macro macro, Minecraft mc) {
        this.releaseKeys(mc);
        macro.walker.go(mc, List.of(Vec3.atBottomCenterOf(this.point(macro).above())), 1.5);
        this.phase(RouteMiner.Phase.WALKING);
    }

    private void next(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        List<BlockPos> route = macro.routes.points();
        macro.mining.release(mc);

        for (int tries = 0; tries < route.size(); tries++) {
            this.index = (this.index + 1) % route.size();
            if (this.index == 0) {
                this.laps++;
            }

            if (macro.config.avoidRadius <= 0 || !macro.playerNear(mc, Vec3.atCenterOf((Vec3i)route.get(this.index)), macro.config.avoidRadius)) {
                break;
            }
        }

        this.travel(macro, mc, player, level);
    }

    private String aim(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        BlockPos point = this.point(macro);
        int slot = warpSlot(player);
        if (slot < 0) {
            this.walk(macro, mc);
            return "Walking to point " + (this.index + 1);
        } else {
            if (player.getInventory().getSelectedSlot() != slot) {
                macro.selectSlot(slot);
            }

            mc.options.keyShift.setDown(true);
            this.sneakHeld = true;
            if (this.aim == null) {
                this.aim = warpPoint(player, level, point);
                if (this.aim == null) {
                    this.walk(macro, mc);
                    return "Point " + (this.index + 1) + " is out of sight, walking";
                }
            }

            macro.rotator.track(player, this.aim, Math.min(1.0, macro.config.rotationSpeed / 100.0 * 1.3));
            boolean ready = player.isShiftKeyDown() && macro.rotator.settled(player, 1.0) && looksAt(player, level, point);
            if (ready && ++this.settleTicks >= 2 + this.random.nextInt(4)) {
                macro.expectTeleport(2500L);
                this.warpFrom = player.position();
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                this.phase(RouteMiner.Phase.WARPING);
                return "Etherwarping to point " + (this.index + 1);
            } else {
                if (System.currentTimeMillis() - this.phaseAt > 4000L) {
                    this.retryWarp(macro, mc, player, level);
                }

                return "Aiming at point " + (this.index + 1);
            }
        }
    }

    private String warping(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        BlockPos point = this.point(macro);
        if (player.position().distanceTo(this.warpFrom) > 1.0) {
            if (standingOn(player, point)) {
                this.releaseKeys(mc);
                this.phase(RouteMiner.Phase.MINING);
                return "Arrived at point " + (this.index + 1);
            } else {
                this.retryWarp(macro, mc, player, level);
                return "Missed point " + (this.index + 1);
            }
        } else {
            if (System.currentTimeMillis() - this.phaseAt > 1500L) {
                this.retryWarp(macro, mc, player, level);
            }

            return "Etherwarping to point " + (this.index + 1);
        }
    }

    private void retryWarp(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (++this.warpTries >= 3) {
            MinerMod.LOGGER.info("Etherwarp to point {} failed {} times, walking", this.index + 1, this.warpTries);
            this.walk(macro, mc);
        } else {
            this.aim = warpPoint(player, level, this.point(macro));
            if (this.aim == null) {
                this.walk(macro, mc);
            } else {
                this.phase(RouteMiner.Phase.AIM);
            }
        }
    }

    private String walking(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        PathWalker.State state = macro.walker.tick(mc, player, macro.rotator, macro.config);
        if (state == PathWalker.State.DONE) {
            this.phase(RouteMiner.Phase.MINING);
            return "Arrived at point " + (this.index + 1);
        } else if (state == PathWalker.State.FAILED) {
            macro.message("Couldn't reach point " + (this.index + 1) + " (" + macro.walker.failure() + "), skipping it");
            this.next(macro, mc, player, level);
            return "Skipping point";
        } else {
            return (state == PathWalker.State.SEARCHING ? "Finding a path to point " : "Walking to point ") + (this.index + 1);
        }
    }

    private String mine(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        int tool = Inv.hotbar(player, Inv::isMiningTool);
        if (tool < 0) {
            macro.stop("No pickaxe or drill in your hotbar");
            return "Stopped";
        } else {
            if (player.getInventory().getSelectedSlot() != tool) {
                macro.selectSlot(tool);
            }

            Map<String, Integer> costs = Targets.costs(macro.config.routeBlocks, macro.config);
            String status = macro.mining.tick(mc, player, level, costs, macro.config, macro.rotator);
            if (macro.mining.target() != null) {
                this.idleTicks = 0;
            } else if (++this.idleTicks > 10) {
                this.next(macro, mc, player, level);
                return "Point " + (this.index + 1) + " next";
            }

            if (System.currentTimeMillis() - this.phaseAt > 120000L) {
                this.next(macro, mc, player, level);
                return "Moving on";
            } else {
                return "Point " + (this.index + 1) + "/" + macro.routes.points().size() + ": " + status;
            }
        }
    }

    private static boolean standingOn(LocalPlayer player, BlockPos point) {
        Vec3 pos = player.position();
        double dx = pos.x - (point.getX() + 0.5);
        double dz = pos.z - (point.getZ() + 0.5);
        return dx * dx + dz * dz < 2.25 && Math.abs(pos.y - (point.getY() + 1)) < 1.1;
    }

    static int warpSlot(LocalPlayer player) {
        return Inv.hotbar(player, stack -> {
            String name = Inv.name(stack);
            return name.contains("Aspect of the Void") || name.contains("Etherwarp Conduit");
        });
    }

    static Vec3 warpPoint(LocalPlayer player, ClientLevel level, BlockPos point) {
        Vec3 eye = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(point);
        if (center.distanceTo(eye) > 57.0) {
            return null;
        } else {
            List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
            Collections.shuffle(faces);
            faces.remove(Direction.UP);
            faces.add(0, Direction.UP);
            Random random = new Random();

            for (Direction face : faces) {
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                Vec3 faceCenter = center.add(normal.scale(0.5));
                if (!(eye.subtract(faceCenter).dot(normal) <= 0.0)) {
                    for (int i = 0; i < 6; i++) {
                        double u = (random.nextDouble() - 0.5) * 0.6;
                        double v = (random.nextDouble() - 0.5) * 0.6;

                        Vec3 onFace = switch (face.getAxis()) {
                            case X -> faceCenter.add(0.0, u, v);
                            case Y -> faceCenter.add(u, 0.0, v);
                            case Z -> faceCenter.add(u, v, 0.0);
                        };
                        Vec3 spot = onFace.subtract(normal.scale(0.02));
                        if (spot.distanceTo(eye) <= 57.0 && clips(player, level, eye, spot, point)) {
                            return spot;
                        }
                    }
                }
            }

            return null;
        }
    }

    private static boolean looksAt(LocalPlayer player, ClientLevel level, BlockPos point) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(TargetFinder.look(player).scale(59.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, player));
        return hit.getType() == Type.BLOCK && hit.getBlockPos().equals(point);
    }

    private static boolean clips(LocalPlayer player, ClientLevel level, Vec3 from, Vec3 to, BlockPos point) {
        Vec3 past = to.add(to.subtract(from).normalize().scale(0.05));
        BlockHitResult hit = level.clip(new ClipContext(from, past, Block.OUTLINE, Fluid.NONE, player));
        return hit.getType() == Type.BLOCK && hit.getBlockPos().equals(point);
    }

    static enum Phase {
        START,
        AIM,
        WARPING,
        WALKING,
        MINING;
    }
}
