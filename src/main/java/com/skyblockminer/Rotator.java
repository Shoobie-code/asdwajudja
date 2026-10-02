package com.skyblockminer;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class Rotator {
    private static final float RESET_SHIFT_DEGREES = 5.0F;
    private final Random random = new Random();
    private boolean randomize = true;
    private Vec3 target;
    private double speed;
    private float targetYaw;
    private float targetPitch;
    private double initialYaw;
    private double initialPitch;
    private double initialDistance;
    private double step;
    private double warmupSteps = 5.0;
    private double yawRemainder;
    private double pitchRemainder;
    private long lastUpdate;
    private double speedFactor = 1.0;
    private double curveSeed;
    private double curveStrength;
    private long startAt;
    private boolean wrote;
    private float writtenYaw;
    private float writtenPitch;

    void setRandomize(boolean randomize) {
        this.randomize = randomize;
    }

    boolean active() {
        return this.target != null;
    }

    boolean settled(LocalPlayer player, double degrees) {
        if (this.target == null) {
            return true;
        } else {
            float[] angles = anglesTo(player, this.target);
            return Math.abs(Mth.wrapDegrees(angles[0] - player.getYRot())) + Math.abs(angles[1] - player.getXRot()) <= degrees;
        }
    }

    void track(LocalPlayer player, Vec3 point, double speed) {
        this.aim(player, point, speed, true);
    }

    void follow(LocalPlayer player, Vec3 point, double speed) {
        this.aim(player, point, speed, false);
    }

    private void aim(LocalPlayer player, Vec3 point, double speed, boolean natural) {
        float[] angles = anglesTo(player, point);
        boolean jump = this.target == null || Math.abs(Mth.wrapDegrees(angles[0] - this.targetYaw)) + Math.abs(angles[1] - this.targetPitch) > 5.0F;
        this.target = point;
        this.speed = speed;
        this.targetYaw = angles[0];
        this.targetPitch = angles[1];
        if (jump) {
            this.initialYaw = Math.abs(Mth.wrapDegrees(this.targetYaw - player.getYRot()));
            this.initialPitch = Math.abs(this.targetPitch - player.getXRot());
            this.initialDistance = Math.hypot(this.initialYaw, this.initialPitch);
            this.warmupSteps = this.initialDistance > 60.0 ? 1.0 : (this.initialDistance > 20.0 ? 3.0 : 5.0);
            this.step = 0.0;
            this.yawRemainder = 0.0;
            this.pitchRemainder = 0.0;
            this.lastUpdate = 0L;
            if (this.randomize) {
                this.speedFactor = 0.85 + this.random.nextDouble() * 0.3;
                this.curveSeed = this.random.nextDouble() * Math.PI * 2.0;
                this.curveStrength = natural ? 0.08 + this.random.nextDouble() * 0.12 : 0.0;
                this.startAt = natural && this.initialDistance > 8.0 ? System.currentTimeMillis() + 40L + this.random.nextInt(120) : 0L;
            } else {
                this.speedFactor = 1.0;
                this.curveStrength = 0.0;
                this.startAt = 0L;
            }
        }
    }

    void stop() {
        this.target = null;
    }

    void sync(LocalPlayer player) {
        this.writtenYaw = player.getYRot();
        this.writtenPitch = player.getXRot();
        this.wrote = true;
    }

    double externalChange(LocalPlayer player) {
        return !this.wrote ? 0.0 : Math.abs(Mth.wrapDegrees(player.getYRot() - this.writtenYaw)) + Math.abs(player.getXRot() - this.writtenPitch);
    }

    void update(LocalPlayer player) {
        if (this.target != null) {
            long now = System.currentTimeMillis();
            if (now < this.startAt) {
                this.sync(player);
            } else {
                float[] angles = anglesTo(player, this.target);
                this.targetYaw = angles[0];
                this.targetPitch = angles[1];
                double elapsed = this.lastUpdate == 0L ? 16.666666666666668 : Mth.clamp(now - this.lastUpdate, 1L, 100L);
                this.lastUpdate = now;
                float yaw = player.getYRot();
                float pitch = player.getXRot();
                double deltaYaw = Mth.wrapDegrees(this.targetYaw - yaw);
                double deltaPitch = this.targetPitch - pitch;
                double distance = Math.hypot(deltaYaw, deltaPitch);
                if (distance <= 0.5) {
                    this.sync(player);
                } else {
                    if (this.curveStrength > 0.0 && this.initialDistance > 1.0) {
                        double progress = Mth.clamp(1.0 - distance / this.initialDistance, 0.0, 1.0);
                        double bend = this.initialDistance * this.curveStrength * Math.sin(progress * Math.PI) * (1.0 - progress);
                        deltaYaw += Math.cos(this.curveSeed) * bend;
                        deltaPitch += Math.sin(this.curveSeed) * bend;
                    }

                    this.initialYaw = Math.max(this.initialYaw, Math.abs(deltaYaw));
                    this.initialPitch = Math.max(this.initialPitch, Math.abs(deltaPitch));
                    double scale = elapsed / 50.0;
                    double warmup = Math.min((this.step += scale) / this.warmupSteps, 1.0);
                    double yawFactor = this.initialYaw > 0.1 ? Math.pow(this.initialYaw / Math.max(0.1, Math.abs(deltaYaw)), 0.1) : 1.0;
                    double pitchFactor = this.initialPitch > 0.1 ? Math.pow(this.initialPitch / Math.max(0.1, Math.abs(deltaPitch)), 0.3) : 1.0;
                    double base = this.speed * this.speedFactor * warmup;
                    double yawBlend = 1.0 - Math.pow(1.0 - Mth.clamp(base * yawFactor, 0.0, 0.95), scale);
                    double pitchBlend = 1.0 - Math.pow(1.0 - Mth.clamp(base * pitchFactor, 0.0, 0.95), scale);
                    double gcd = gcd();
                    double rawYaw = deltaYaw * yawBlend + this.yawRemainder;
                    double rawPitch = deltaPitch * pitchBlend + this.pitchRemainder;
                    double yawStep = Math.round(rawYaw / gcd) * gcd;
                    double pitchStep = Math.round(rawPitch / gcd) * gcd;
                    this.yawRemainder = rawYaw - yawStep;
                    this.pitchRemainder = rawPitch - pitchStep;
                    player.setYRot((float)(yaw + yawStep));
                    player.setXRot(Mth.clamp((float)(pitch + pitchStep), -90.0F, 90.0F));
                    this.sync(player);
                }
            }
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
