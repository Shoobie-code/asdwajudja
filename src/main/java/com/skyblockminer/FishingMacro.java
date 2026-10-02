package com.skyblockminer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Casts, waits for SkyBlock's "!!!" bite marker over the bobber, reels in and recasts. When enough sea
 * creatures gather it can switch to a weapon, clear them, and go back to fishing.
 */
final class FishingMacro implements Routine {
    private static final List<String> RODS = List.of("rod");
    static final List<String> WEAPONS = List.of("hyperion", "astraea", "scylla", "valkyrie", "sword", "blade", "katana", "scythe", "dagger");
    private static final double BITE_SEARCH = 32.0;
    private static final double BITE_OFF_LINE = 3.0;
    private static final double CREATURE_RADIUS = 6.0;
    private static final long MIN_WAIT_MS = 800L;

    private enum State {
        CAST,
        WAITING,
        REEL,
        FIGHT
    }

    private State state = State.CAST;
    private long castAt;
    private long actAt;
    private boolean bite;
    private float yaw = Float.NaN;
    private float pitch;
    private int scanIn;
    private final List<LivingEntity> creatures = new ArrayList<>();
    private int attackIn;
    private int caught;
    private int fights;

    @Override
    public void reset() {
        this.state = State.CAST;
        this.yaw = Float.NaN;
        this.actAt = 0L;
        this.caught = 0;
        this.fights = 0;
        this.creatures.clear();
    }

    @Override
    public void resume() {
        this.state = State.CAST;
        this.yaw = Float.NaN;
        this.actAt = 0L;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        MinerConfig config = macro.config;
        long now = System.currentTimeMillis();
        if (Float.isNaN(this.yaw)) {
            this.chooseAngles(player, config);
        }

        if (config.fishKillCreatures && --this.scanIn <= 0) {
            this.scanIn = 10;
            this.findCreatures(player, level);
            if (this.state != State.FIGHT && this.creatures.size() >= Math.max(1, config.fishCreatureLimit)) {
                this.state = State.FIGHT;
            }
        }
        if (this.state == State.FIGHT) {
            return this.fight(macro, mc, player, config);
        }

        int rod = Inv.hotbarNamed(player, RODS);
        if (rod < 0) {
            macro.stop("No fishing rod in the hotbar");
            return "No rod";
        }
        if (player.getInventory().getSelectedSlot() != rod) {
            macro.selectSlot(rod);
            this.state = State.CAST;
            this.actAt = now + 250L;
        }

        macro.rotator.look(player, this.yaw, this.pitch, config.rotationSpeed / 100.0);
        switch (this.state) {
            case CAST -> {
                if (now >= this.actAt && macro.rotator.settled(player, 2.0)) {
                    mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    this.castAt = now;
                    this.state = State.WAITING;
                }
                return "Casting";
            }
            case WAITING -> {
                long waited = now - this.castAt;
                if (waited > MIN_WAIT_MS && biteVisible(player, level)) {
                    this.bite = true;
                    this.state = State.REEL;
                    this.actAt = now + Math.max(0, config.fishReelDelay);
                } else if (waited > Math.max(5, config.fishTimeout) * 1000L) {
                    this.bite = false;
                    this.state = State.REEL;
                    this.actAt = now;
                }
                return String.format("Waiting for a bite (%ds)", waited / 1000L);
            }
            case REEL -> {
                if (now >= this.actAt) {
                    mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    if (this.bite) {
                        this.caught++;
                    }
                    this.state = State.CAST;
                    this.actAt = now + Math.max(100, config.fishRecastDelay);
                }
                return "Reeling in";
            }
            default -> {
                return "Fishing";
            }
        }
    }

    private void chooseAngles(LocalPlayer player, MinerConfig config) {
        double[] spot = config.fishSpot;
        if (spot != null && player.position().distanceToSqr(new Vec3(spot[0], spot[1], spot[2])) < 9.0) {
            this.yaw = (float) spot[3];
            this.pitch = (float) spot[4];
        } else {
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
        }
    }

    /** The "!!!" stand SkyBlock shows above a bobber with a bite, roughly along our line of cast. */
    private boolean biteVisible(LocalPlayer player, ClientLevel level) {
        Vec3 eye = player.getEyePosition();
        double radians = Math.toRadians(this.yaw);
        double dirX = -Math.sin(radians);
        double dirZ = Math.cos(radians);
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand && stand.hasCustomName()) {
                String name = ChatFormatting.stripFormatting(stand.getCustomName().getString());
                if (name == null || !name.trim().equals("!!!")) {
                    continue;
                }
                double dx = stand.getX() - eye.x;
                double dz = stand.getZ() - eye.z;
                double along = dx * dirX + dz * dirZ;
                double across = Math.abs(dx * dirZ - dz * dirX);
                if (along > -1.0 && along < BITE_SEARCH && across < BITE_OFF_LINE) {
                    return true;
                }
            }
        }
        return false;
    }

    private void findCreatures(LocalPlayer player, ClientLevel level) {
        this.creatures.clear();
        List<ArmorStand> tags = new ArrayList<>();
        double search = (CREATURE_RADIUS + 3.0) * (CREATURE_RADIUS + 3.0);
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand && stand.hasCustomName() && player.distanceToSqr(stand) < search) {
                String name = stand.getCustomName().getString();
                if (name.contains("❤")) {
                    tags.add(stand);
                }
            }
        }
        if (tags.isEmpty()) {
            return;
        }
        double radiusSq = CREATURE_RADIUS * CREATURE_RADIUS;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand) && !(entity instanceof Player)
                && living.isAlive() && player.distanceToSqr(entity) < radiusSq && tagged(living, tags)) {
                this.creatures.add(living);
            }
        }
    }

    private static boolean tagged(LivingEntity body, List<ArmorStand> tags) {
        for (ArmorStand tag : tags) {
            double dx = body.getX() - tag.getX();
            double dz = body.getZ() - tag.getZ();
            double above = tag.getY() - body.getY();
            if (dx * dx + dz * dz < 1.0 && above >= 0.0 && above < 3.5) {
                return true;
            }
        }
        return false;
    }

    private String fight(Macro macro, Minecraft mc, LocalPlayer player, MinerConfig config) {
        this.creatures.removeIf(creature -> !creature.isAlive() || creature.isRemoved());
        if (this.creatures.isEmpty()) {
            this.fights++;
            this.state = State.CAST;
            this.actAt = System.currentTimeMillis() + 300L;
            return "Back to fishing";
        }
        int weapon = Inv.toolSlot(player, config.fishWeaponSlot, WEAPONS);
        if (weapon < 0) {
            macro.stop("Sea creatures nearby but no weapon in the hotbar");
            return "No weapon";
        }
        if (player.getInventory().getSelectedSlot() != weapon) {
            macro.selectSlot(weapon);
        }

        LivingEntity target = this.creatures.get(0);
        double best = player.distanceToSqr(target);
        for (LivingEntity creature : this.creatures) {
            double distance = player.distanceToSqr(creature);
            if (distance < best) {
                best = distance;
                target = creature;
            }
        }
        Vec3 aim = target.getBoundingBox().getCenter();
        macro.rotator.follow(player, aim, Math.min(1.0, config.rotationSpeed / 100.0 * 1.3));
        if (--this.attackIn <= 0) {
            if (config.fishAttackMode.equals("use")) {
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                this.attackIn = 5;
            } else {
                HitResult hit = mc.hitResult;
                if (hit instanceof EntityHitResult entityHit && hit.getType() == HitResult.Type.ENTITY && entityHit.getEntity() == target) {
                    mc.gameMode.attack(player, target);
                    player.swing(InteractionHand.MAIN_HAND);
                    this.attackIn = 3;
                }
            }
        }
        return "Fighting " + this.creatures.size() + " sea creature" + (this.creatures.size() == 1 ? "" : "s");
    }

    @Override
    public String hudLine(Macro macro) {
        return String.format("%d caught (%.0f/h), %d fights", this.caught, this.caught / macro.hours(), this.fights);
    }

    @Override
    public String rejoinWarp(MinerConfig config) {
        return config.fishWarpCommand.isBlank() ? null : config.fishWarpCommand;
    }

    @Override
    public Vec3 home(MinerConfig config) {
        double[] spot = config.fishSpot;
        return spot == null ? null : new Vec3(spot[0], spot[1], spot[2]);
    }
}
