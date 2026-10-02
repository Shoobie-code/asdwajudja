package com.skyblockminer;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class Combat {
    private static final double MELEE = 3.2;
    private static final double SEARCH = 40.0;
    private LivingEntity target;
    private int retargetIn;
    private int repathIn;
    private int attackIn;

    void reset(Minecraft mc, PathWalker walker) {
        this.target = null;
        walker.stop(mc);
    }

    String tick(
        Minecraft mc, LocalPlayer player, ClientLevel level, CommissionData.Mob mob, Rotator rotator, PathWalker walker, MinerConfig config, Random random
    ) {
        if (this.target == null || !this.target.isAlive() || this.target.isRemoved() || --this.retargetIn <= 0) {
            LivingEntity next = find(player, level, mob);
            if (next != this.target) {
                this.repathIn = 0;
            }

            this.target = next;
            this.retargetIn = 20;
        }

        String name = mob.names().get(0);
        if (this.target == null) {
            walker.stop(mc);
            rotator.stop();
            rotator.sync(player);
            return "Looking for " + name;
        } else {
            double distance = Math.sqrt(player.distanceToSqr(this.target));
            if (!(distance > 3.2)) {
                walker.stop(mc);
                Vec3 aim = this.target.getBoundingBox().getCenter().add(0.0, this.target.getBbHeight() * 0.2, 0.0);
                rotator.follow(player, aim, Math.min(1.0, config.rotationSpeed / 100.0 * 1.3));
                HitResult hit = mc.hitResult;
                if (--this.attackIn <= 0 && hit instanceof EntityHitResult entityHit && hit.getType() == Type.ENTITY && entityHit.getEntity() == this.target) {
                    mc.gameMode.attack(player, this.target);
                    player.swing(InteractionHand.MAIN_HAND);
                    this.attackIn = 2 + random.nextInt(4);
                }

                return "Fighting " + name;
            } else {
                if (!walker.busy() || --this.repathIn <= 0) {
                    walker.go(mc, List.of(this.target.position()), 2.0);
                    this.repathIn = 30;
                }

                walker.tick(mc, player, rotator, config);
                return String.format("Chasing %s (%.0fm)", name, distance);
            }
        }
    }

    private static LivingEntity find(LocalPlayer player, ClientLevel level, CommissionData.Mob mob) {
        LivingEntity best = null;
        LivingEntity hidden = null;
        double bestDistance = 1600.0;
        double hiddenDistance = 1600.0;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand && stand.hasCustomName()) {
                String tag = ChatFormatting.stripFormatting(stand.getCustomName().getString());
                if (tag != null && tag.contains("❤") && !mob.names().stream().noneMatch(tag::contains)) {
                    LivingEntity body = bodyUnder(level, player, stand);
                    if (body != null && mob.bounds().contains(body.getX(), body.getY(), body.getZ())) {
                        double distance = player.distanceToSqr(body);
                        if (visible(player, level, body)) {
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                best = body;
                            }
                        } else if (distance < hiddenDistance) {
                            hiddenDistance = distance;
                            hidden = body;
                        }
                    }
                }
            }
        }

        return best != null ? best : hidden;
    }

    private static LivingEntity bodyUnder(ClientLevel level, LocalPlayer player, ArmorStand stand) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand) && entity != player && living.isAlive()) {
                if (entity instanceof Player) {
                    UUID id = entity.getUUID();
                    if (id.version() == 4) {
                        continue;
                    }
                }

                double dx = entity.getX() - stand.getX();
                double dz = entity.getZ() - stand.getZ();
                double below = stand.getY() - entity.getY();
                if (!(dx * dx + dz * dz > 1.0) && !(below < 0.0) && !(below > 3.5)) {
                    double distance = dx * dx + dz * dz + below * below * 0.1;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = living;
                    }
                }
            }
        }

        return best;
    }

    private static boolean visible(LocalPlayer player, ClientLevel level, LivingEntity body) {
        Vec3 eye = player.getEyePosition();
        Vec3 center = body.getBoundingBox().getCenter();
        return level.clip(new ClipContext(eye, center, Block.COLLIDER, Fluid.NONE, player)).getType() == Type.MISS;
    }
}
