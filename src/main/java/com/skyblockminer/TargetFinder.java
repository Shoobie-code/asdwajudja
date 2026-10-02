package com.skyblockminer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class TargetFinder {
    private static final int EVALUATION_BUDGET = 24;
    private static final int SAMPLES_PER_FACE = 7;
    private static final double FACE_INSET = 0.02;
    private final Random random = new Random();
    private boolean randomize = true;
    private double spread = 0.35;

    void configure(boolean randomize, double spread) {
        this.randomize = randomize;
        this.spread = Math.max(0.0, Math.min(0.45, spread));
    }

    static String blockId(ClientLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }

    TargetFinder.Target find(LocalPlayer player, ClientLevel level, Map<String, Integer> costs, double reach, Set<BlockPos> excluded, Predicate<BlockPos> allow) {
        if (costs.isEmpty()) {
            return null;
        } else {
            Vec3 eye = player.getEyePosition();
            Vec3 look = look(player);
            double candidateReach = reach + 0.87;
            double candidateReachSq = candidateReach * candidateReach;
            int radius = (int)Math.ceil(candidateReach);
            BlockPos center = BlockPos.containing(eye);
            AABB body = player.getBoundingBox();
            int floorY = (int)Math.floor(body.minY - 0.01);
            List<TargetFinder.Candidate> candidates = new ArrayList<>();
            MutableBlockPos cursor = new MutableBlockPos();

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        String id = blockId(level, cursor);
                        Integer base = costs.get(id);
                        if (base != null) {
                            BlockPos pos = cursor.immutable();
                            if (!excluded.contains(pos) && !supports(body, floorY, pos) && (allow == null || allow.test(pos))) {
                                Vec3 toCenter = Vec3.atCenterOf(pos).subtract(eye);
                                double distSq = toCenter.lengthSqr();
                                if (!(distSq > candidateReachSq)) {
                                    double dist = Math.sqrt(distSq);
                                    double dot = dist > 0.0 ? toCenter.dot(look) / dist : 1.0;
                                    candidates.add(new TargetFinder.Candidate(pos, id, base, cost(base, dist, dot)));
                                }
                            }
                        }
                    }
                }
            }

            candidates.sort(Comparator.comparingDouble(TargetFinder.Candidate::cheapCost));
            TargetFinder.Target best = null;
            int evaluated = 0;

            for (TargetFinder.Candidate candidate : candidates) {
                if (evaluated++ >= 24) {
                    break;
                }

                Vec3 aim = this.aimPoint(player, level, candidate.pos(), reach);
                if (aim != null) {
                    Vec3 toAim = aim.subtract(eye);
                    double dist = toAim.length();
                    double cost = cost(candidate.base(), dist, dist > 0.0 ? toAim.dot(look) / dist : 1.0);
                    if (best == null || cost < best.cost()) {
                        best = new TargetFinder.Target(candidate.pos(), aim, candidate.block(), cost);
                    }
                }
            }

            return best;
        }
    }

    Vec3 aimPoint(LocalPlayer player, ClientLevel level, BlockPos pos, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = look(player);
        Vec3 center = Vec3.atCenterOf(pos);
        double reachSq = reach * reach;
        List<TargetFinder.Point> visible = new ArrayList<>();

        for (Direction face : Direction.values()) {
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            Vec3 faceCenter = center.add(normal.scale(0.5));
            if (!(eye.subtract(faceCenter).dot(normal) <= 0.0)) {
                for (int i = 0; i < 7; i++) {
                    double u = i == 0 ? 0.0 : (this.random.nextDouble() * 2.0 - 1.0) * (this.randomize ? this.spread : 0.3);
                    double v = i == 0 ? 0.0 : (this.random.nextDouble() * 2.0 - 1.0) * (this.randomize ? this.spread : 0.3);
                    Vec3 point = facePoint(faceCenter, face, u, v).subtract(normal.scale(0.02));
                    Vec3 toPoint = point.subtract(eye);
                    if (!(toPoint.lengthSqr() > reachSq) && reaches(player, level, eye, point, pos)) {
                        visible.add(new TargetFinder.Point(point, toPoint.normalize().dot(look)));
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            return null;
        } else {
            visible.sort(Comparator.comparingDouble(TargetFinder.Point::dot).reversed());
            if (!this.randomize) {
                return visible.get(0).at();
            } else {
                int pool = Math.max(1, (visible.size() + 1) / 2);
                return visible.get(this.random.nextInt(pool)).at();
            }
        }
    }

    private static boolean supports(AABB body, int floorY, BlockPos pos) {
        return pos.getY() == floorY && pos.getX() < body.maxX && pos.getX() + 1 > body.minX && pos.getZ() < body.maxZ && pos.getZ() + 1 > body.minZ;
    }

    static boolean reaches(LocalPlayer player, ClientLevel level, Vec3 from, Vec3 to, BlockPos pos) {
        Vec3 past = to.add(to.subtract(from).normalize().scale(0.05));
        BlockHitResult hit = level.clip(new ClipContext(from, past, Block.OUTLINE, Fluid.NONE, player));
        return hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private static Vec3 facePoint(Vec3 faceCenter, Direction face, double u, double v) {
        return switch (face.getAxis()) {
            case X -> faceCenter.add(0.0, u, v);
            case Y -> faceCenter.add(u, 0.0, v);
            case Z -> faceCenter.add(u, v, 0.0);
        };
    }

    static Vec3 look(LocalPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        double pitch = Math.toRadians(player.getXRot());
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private static double cost(int base, double distance, double dot) {
        return base + distance * 2.0 - dot * 50.0;
    }

    private record Candidate(BlockPos pos, String block, int base, double cheapCost) {
    }

    private record Point(Vec3 at, double dot) {
    }

    record Target(BlockPos pos, Vec3 aim, String block, double cost) {
    }
}
