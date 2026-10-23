package com.skyblockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Owns the camera while a macro runs: every macro turns the view through here, and it turns like a hand on a
 * mouse (see {@link MouseModel}) at the rotation speed setting.
 */
final class Rotator {
    private static final Vec3 FIXED = new Vec3(0.0, 0.0, 0.0);
    private final MouseModel mouse = new MouseModel();
    private Vec3 target;
    private boolean fixed;
    private float fixedYaw;
    private float fixedPitch;
    private float targetYaw;
    private float targetPitch;
    private long lastUpdate;
    private boolean wrote;
    private float writtenYaw;
    private float writtenPitch;

    void setRandomize(boolean randomize) {
        this.mouse.setRandomize(randomize);
    }

    void configure(MinerConfig config) {
        this.mouse.setRandomize(config.randomize);
        this.mouse.configure(config.overshoot / 100.0, config.reactionTime, config.mouseLifts, config.idleDrift);
    }

    boolean active() {
        return this.target != null;
    }

    boolean settled(LocalPlayer player, double degrees) {
        if (this.target == null) {
            return true;
        } else {
            float[] angles = this.goal(player);
            return Math.abs(Mth.wrapDegrees(angles[0] - player.getYRot())) + Math.abs(angles[1] - player.getXRot()) <= degrees;
        }
    }

    /** Aims at a point, reacting to it as to something newly seen. */
    void track(LocalPlayer player, Vec3 point, double speed) {
        this.aim(player, point, speed, true);
    }

    /** Aims at a point that moves along smoothly (a walking direction), without reaction pauses. */
    void follow(LocalPlayer player, Vec3 point, double speed) {
        this.aim(player, point, speed, false);
    }

    /** Turns to fixed angles (used by macros that face one direction, like farming). */
    void look(LocalPlayer player, float yaw, float pitch, double speed) {
        this.fixedYaw = yaw;
        this.fixedPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        boolean wasFixed = this.fixed;
        this.fixed = true;
        this.begin(player, wasFixed ? FIXED : null, FIXED, this.goal(player), speed, false);
    }

    private void aim(LocalPlayer player, Vec3 point, double speed, boolean natural) {
        Vec3 previous = this.fixed ? null : this.target;
        this.fixed = false;
        this.begin(player, previous, point, anglesTo(player, point), speed, natural);
    }

    private float[] goal(LocalPlayer player) {
        return this.fixed
            ? new float[]{player.getYRot() + Mth.wrapDegrees(this.fixedYaw - player.getYRot()), this.fixedPitch}
            : anglesTo(player, this.target);
    }

    private void begin(LocalPlayer player, Vec3 previous, Vec3 point, float[] angles, double speed, boolean natural) {
        boolean jump = previous == null || Math.abs(Mth.wrapDegrees(angles[0] - this.targetYaw)) + Math.abs(angles[1] - this.targetPitch) > 5.0F;
        this.target = point;
        this.mouse.setSpeed(speed);
        this.targetYaw = angles[0];
        this.targetPitch = angles[1];
        if (jump && natural) {
            // Something newly seen: the hand drops what it was doing and reacts first. A followed goal just
            // moves on, and the stroke under way bends toward it.
            double distance = Math.hypot(Mth.wrapDegrees(angles[0] - player.getYRot()), angles[1] - player.getXRot());
            this.mouse.interrupt(this.mouse.reaction(distance));
        }
    }

    void stop() {
        this.target = null;
        this.fixed = false;
        this.mouse.interrupt(0.0);
        this.lastUpdate = 0L;
    }

    void sync(LocalPlayer player) {
        this.writtenYaw = player.getYRot();
        this.writtenPitch = player.getXRot();
        this.wrote = true;
    }

    double externalChange(LocalPlayer player) {
        return !this.wrote ? 0.0 : Math.abs(Mth.wrapDegrees(player.getYRot() - this.writtenYaw)) + Math.abs(player.getXRot() - this.writtenPitch);
    }

    /** Called every frame. */
    void update(LocalPlayer player) {
        if (this.target != null) {
            long now = System.nanoTime();
            double elapsed = this.lastUpdate == 0L ? 16.666666666666668 : Mth.clamp((now - this.lastUpdate) / 1000000.0, 1.0, 100.0);
            this.lastUpdate = now;
            float[] angles = this.goal(player);
            this.targetYaw = angles[0];
            this.targetPitch = angles[1];
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            double[] turn = this.mouse.step(yaw, pitch, angles[0], angles[1], elapsed, gcd());
            if (turn[0] != 0.0 || turn[1] != 0.0) {
                player.setYRot((float) (yaw + turn[0]));
                player.setXRot(Mth.clamp((float) (pitch + turn[1]), -90.0F, 90.0F));
            }
            this.sync(player);
        }
    }

    static double gcd() {
        double sensitivity = (Double)Minecraft.getInstance().options.sensitivity().get();
        double f = sensitivity * 0.6 + 0.2;
        return f * f * f * 1.2;
    }

    static float[] anglesTo(LocalPlayer player, Vec3 point) {
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.hypot(dx, dz);
        float yaw = horizontal < 1.0E-4 ? player.getYRot() : (float)Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float)Math.toDegrees(Math.atan2(-dy, horizontal));
        return new float[]{player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()), Mth.clamp(pitch, -90.0F, 90.0F)};
    }
}
