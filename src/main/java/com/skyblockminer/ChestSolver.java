package com.skyblockminer;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class ChestSolver {
    private static final long GIVE_UP_MS = 30000L;
    private static final long FIND_MS = 4000L;
    private static final long LOCK_FRESH_MS = 1500L;
    private static final int SEARCH = 4;
    private final Random random = new Random();
    private long spawnedAt;
    private BlockPos chest;
    private boolean seen;
    private Vec3 lock;
    private long lockAt;
    private long nextClickAt;
    private int opened;

    boolean active() {
        return this.spawnedAt != 0L;
    }

    int opened() {
        return this.opened;
    }

    void reset() {
        this.spawnedAt = 0L;
        this.opened = 0;
    }

    void spawned() {
        this.spawnedAt = System.currentTimeMillis();
        this.chest = null;
        this.seen = false;
        this.lock = null;
        this.nextClickAt = 0L;
    }

    void lockpicked() {
        if (this.active()) {
            this.opened++;
            this.spawnedAt = 0L;
        }
    }

    void onParticle(ParticleOptions particle, double x, double y, double z) {
        if (this.active() && this.chest != null && particle.getType() == ParticleTypes.CRIT) {
            Vec3 at = new Vec3(x, y, z);
            if (!(at.distanceToSqr(Vec3.atCenterOf(this.chest)) > 1.44)) {
                this.lock = at;
                this.lockAt = System.currentTimeMillis();
            }
        }
    }

    String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        long now = System.currentTimeMillis();
        if (now - this.spawnedAt > 30000L) {
            this.spawnedAt = 0L;
            return null;
        } else if (this.chest != null && !isChest(level.getBlockState(this.chest))) {
            if (this.seen) {
                this.opened++;
            }

            this.spawnedAt = 0L;
            return null;
        } else {
            if (this.chest == null) {
                this.chest = find(level, player.blockPosition());
                if (this.chest == null) {
                    if (now - this.spawnedAt > 4000L) {
                        this.spawnedAt = 0L;
                    }

                    return this.spawnedAt == 0L ? null : "Looking for the treasure chest";
                }

                this.seen = true;
            }

            macro.mining.release(mc);
            Vec3 center = Vec3.atCenterOf(this.chest);
            Vec3 aim;
            if (this.lock != null && now - this.lockAt < 1500L) {
                aim = this.lock.add(center.subtract(this.lock).normalize().scale(0.08));
            } else {
                Vec3 eye = player.getEyePosition();
                aim = center.add(eye.subtract(center).normalize().scale(0.35));
            }

            macro.rotator.follow(player, aim, Math.min(1.0, macro.config.rotationSpeed / 100.0 * 1.4));
            HitResult hit = mc.hitResult;
            boolean onChest = hit instanceof BlockHitResult block && hit.getType() == Type.BLOCK && block.getBlockPos().equals(this.chest);
            if (onChest && macro.rotator.settled(player, 1.5) && now >= this.nextClickAt) {
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, (BlockHitResult)hit);
                player.swing(InteractionHand.MAIN_HAND);
                this.nextClickAt = now + 220L + this.random.nextInt(260);
            }

            return "Opening a treasure chest";
        }
    }

    private static BlockPos find(ClientLevel level, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-4, -4, -4), from.offset(4, 4, 4))) {
            if (isChest(level.getBlockState(pos))) {
                double distance = pos.distSqr(from);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }

        return best;
    }

    private static boolean isChest(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST);
    }
}
