package com.skyblockminer.rotation;

import java.util.Random;

/**
 * A hand moving a mouse across a pad, in camera degrees. Every turn is made of strokes:
 *
 * <ul>
 *   <li>A stroke follows a minimum-jerk speed curve (smooth start, peak, smooth stop), slightly skewed so the
 *       peak comes early, and bows sideways a little like a wrist pivoting.</li>
 *   <li>Its length and time follow Fitts' law, and its peak speed never exceeds the turn limit.</li>
 *   <li>It lands off target: usually a little past it (overflick), sometimes short (underflick), and slightly
 *       to one side. A short pause later a smaller corrective stroke fixes most of the miss, and so on until the
 *       view is within the tolerance.</li>
 *   <li>Turns longer than one pad width are split into strokes with a pause between them, as when the mouse is
 *       lifted and put back down.</li>
 *   <li>A faint tremor rides on top while the hand moves.</li>
 *   <li>Up and down is slower and less precise than side to side, as for a wrist on a pad.</li>
 *   <li>Held still on a target, the hand drifts a few tenths of a degree now and then.</li>
 * </ul>
 *
 * <p>Each model is one "hand": its habits (how far it tends to overshoot, how precise and quick it is, how wide
 * its strokes are, how fast it reacts) are drawn once when it is made, so they hold for a game session and
 * differ between sessions.
 *
 * <p>The goal may move while a stroke is under way (a walking path, a mob): each stroke aims at where the goal
 * is now, keeping its own miss, so tracking stays smooth. Output is rounded to whole mouse counts.
 *
 * <p>No Minecraft types here, so it can be unit tested; {@link Rotator} drives it every frame.
 */
final class MouseModel {
    private final Random random;
    private boolean randomize = true;
    /** 0..1, from the rotation speed setting. */
    private double speed = 0.5;
    private double tolerance = 0.4;

    private boolean moving;
    private double startYaw;
    private double startPitch;
    /** Where this stroke ends up relative to the goal, in degrees. */
    private double missYaw;
    private double missPitch;
    /** Share of the remaining turn this stroke covers (below 1 when the turn is wider than the pad). */
    private double portion = 1.0;
    /** Sideways bow at mid-stroke, in degrees (signed). */
    private double bow;
    private double durationMs;
    private double elapsedMs;
    /** Below 1 moves the speed peak earlier, as in real flicks. */
    private double skew = 1.0;
    private double waitMs;
    private double tremorPhase;
    private double tremorHz;
    private double idleMs;

    // Settings.
    private double overshootScale = 1.0;
    private double reactionMs = 150.0;
    private boolean lifts = true;
    private boolean drift = true;

    // This hand's habits.
    private final double overshootBias;
    private final double accuracy;
    private final double tempo;
    private final double padWidth;
    private final double reactionBias;
    /** How much slower and less precise vertical moves are than horizontal ones. */
    private final double pitchCost;

    MouseModel() {
        this(new Random());
    }

    MouseModel(Random random) {
        this.random = random;
        this.overshootBias = 0.015 + random.nextDouble() * 0.04;
        this.accuracy = 0.045 + random.nextDouble() * 0.035;
        this.tempo = 0.88 + random.nextDouble() * 0.24;
        this.padWidth = 100.0 + random.nextDouble() * 50.0;
        this.reactionBias = 0.85 + random.nextDouble() * 0.3;
        this.pitchCost = 1.15 + random.nextDouble() * 0.25;
    }

    /**
     * Settings: {@code overshoot} scales how far strokes miss (1 is natural, 0 lands exactly), {@code reactionMs}
     * is the typical pause before turning to something new, {@code lifts} splits wide turns, {@code drift} lets the
     * hand wander slightly while holding still.
     */
    void configure(double overshoot, double reactionMs, boolean lifts, boolean drift) {
        this.overshootScale = Math.max(0.0, Math.min(3.0, overshoot));
        this.reactionMs = Math.max(0.0, Math.min(600.0, reactionMs));
        this.lifts = lifts;
        this.drift = drift;
    }

    void setRandomize(boolean randomize) {
        this.randomize = randomize;
    }

    void setSpeed(double speed) {
        this.speed = Math.max(0.01, Math.min(1.0, speed));
    }

    /** Peak turn speed in degrees per second at the current speed setting. */
    double maxVelocity() {
        return 250.0 + 1750.0 * this.speed;
    }

    /** Drops the current stroke (the target changed) and waits {@code ms} before reacting. */
    void interrupt(double ms) {
        this.moving = false;
        this.waitMs = Math.max(this.waitMs, ms);
    }

    /** A human reaction time to a new target, or 0 without randomization or for small changes. */
    double reaction(double degrees) {
        if (!this.randomize || degrees <= 8.0 || this.reactionMs <= 0.0) {
            return 0.0;
        }
        // Reaction times are skewed: mostly near the typical value, now and then a good deal slower.
        double typical = this.reactionMs * this.reactionBias;
        return typical * (0.6 + this.random.nextDouble() * 0.5 + Math.pow(this.random.nextDouble(), 4.0) * 0.9);
    }

    /**
     * Advances {@code dtMs} and returns the turn to apply this frame, {yaw, pitch} in degrees, each a whole
     * number of {@code gcd} (the degrees one mouse count turns). {@code goalYaw} must be unwrapped near {@code yaw}.
     */
    double[] step(double yaw, double pitch, double goalYaw, double goalPitch, double dtMs, double gcd) {
        if (!this.moving) {
            double distance = Math.hypot(goalYaw - yaw, goalPitch - pitch);
            if (distance > this.tolerance) {
                this.idleMs = 300.0 + this.random.nextDouble() * 900.0;
                if (this.waitMs > 0.0) {
                    this.waitMs -= dtMs;
                    return new double[]{0.0, 0.0};
                }
                this.plan(yaw, pitch, goalYaw, goalPitch, distance);
            } else if (this.randomize && this.drift && (this.idleMs -= dtMs) <= 0.0) {
                this.idleMs = 600.0 + this.random.nextDouble() * 1800.0;
                this.planDrift(yaw, pitch, goalYaw, goalPitch);
            } else {
                return new double[]{0.0, 0.0};
            }
        }
        this.elapsedMs += dtMs;
        double t = Math.min(1.0, this.elapsedMs / this.durationMs);
        double[] want = this.position(t, goalYaw, goalPitch);
        double dYaw = want[0] - yaw;
        double dPitch = want[1] - pitch;
        // The turn limit holds even when the goal runs away mid-stroke.
        double cap = this.maxVelocity() * 1.15 * dtMs / 1000.0;
        double length = Math.hypot(dYaw, dPitch);
        if (length > cap) {
            dYaw *= cap / length;
            dPitch *= cap / length;
        }
        if (t >= 1.0) {
            this.moving = false;
            boolean lifted = this.portion < 1.0;
            this.waitMs = !this.randomize ? 0.0 : lifted ? 90.0 + this.random.nextDouble() * 130.0 : 35.0 + this.random.nextDouble() * 85.0;
        }
        return new double[]{Math.round(dYaw / gcd) * gcd, Math.round(dPitch / gcd) * gcd};
    }

    private void plan(double yaw, double pitch, double goalYaw, double goalPitch, double distance) {
        double padWidth = !this.randomize || !this.lifts ? 720.0 : this.padWidth * (0.8 + this.random.nextDouble() * 0.4);
        this.portion = distance > padWidth ? padWidth / distance : 1.0;
        double ux = (goalYaw - yaw) / distance;
        double uy = (goalPitch - pitch) / distance;
        double missYaw = 0.0;
        double missPitch = 0.0;
        if (this.randomize && this.portion >= 1.0 && this.overshootScale > 0.0) {
            // Mostly a little past the target, sometimes short; small turns are relatively more precise, and
            // vertical aim is looser than horizontal.
            double along = Math.max(-0.14, Math.min(0.2, this.overshootBias + this.random.nextGaussian() * this.accuracy)) * this.overshootScale;
            double side = Math.max(-0.06, Math.min(0.06, this.random.nextGaussian() * this.accuracy * 0.4)) * this.overshootScale;
            double scale = distance * Math.min(1.0, distance / 6.0);
            missYaw = (ux * along - uy * side) * scale;
            missPitch = (uy * along + ux * side) * scale * this.pitchCost;
        }
        this.begin(yaw, pitch, goalYaw, goalPitch, missYaw, missPitch);
    }

    /** A tiny wander off the goal while holding still, kept well inside what callers count as on target. */
    private void planDrift(double yaw, double pitch, double goalYaw, double goalPitch) {
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double size = 0.15 + this.random.nextDouble() * 0.25;
        this.portion = 1.0;
        this.begin(yaw, pitch, goalYaw, goalPitch, Math.cos(angle) * size, Math.sin(angle) * size * 0.6);
    }

    private void begin(double yaw, double pitch, double goalYaw, double goalPitch, double missYaw, double missPitch) {
        this.moving = true;
        this.elapsedMs = 0.0;
        this.startYaw = yaw;
        this.startPitch = pitch;
        this.missYaw = missYaw;
        this.missPitch = missPitch;
        double strokeYaw = this.portion * (goalYaw + missYaw - yaw);
        double strokePitch = this.portion * (goalPitch + missPitch - pitch);
        double stroke = Math.hypot(strokeYaw, strokePitch);
        this.bow = this.randomize ? this.random.nextGaussian() * 0.05 * stroke : 0.0;
        this.skew = this.randomize ? 0.8 + this.random.nextDouble() * 0.22 : 1.0;
        double width = Math.max(1.0, this.tolerance * 2.0);
        double fitts = (60.0 + 80.0 * Math.log(1.0 + stroke / width) / Math.log(2.0)) / (0.4 + 1.2 * this.speed);
        // Vertical travel takes longer than the same angle sideways.
        if (stroke > 1.0E-6) {
            fitts *= 1.0 + (this.pitchCost - 1.0) * Math.abs(strokePitch) / stroke;
        }
        if (this.randomize) {
            fitts *= this.tempo * (0.85 + this.random.nextDouble() * 0.3);
        }
        // A minimum-jerk stroke peaks at 1.875 times its mean speed.
        double slowest = 1.875 * stroke / (this.maxVelocity() / 1000.0);
        this.durationMs = Math.max(25.0, Math.max(fitts, slowest / this.skew));
        this.tremorPhase = this.random.nextDouble() * Math.PI * 2.0;
        this.tremorHz = 8.0 + this.random.nextDouble() * 4.0;
    }

    private double[] position(double t, double goalYaw, double goalPitch) {
        double endYaw = this.startYaw + this.portion * (goalYaw + this.missYaw - this.startYaw);
        double endPitch = this.startPitch + this.portion * (goalPitch + this.missPitch - this.startPitch);
        double s = minimumJerk(Math.pow(t, this.skew));
        double dy = endYaw - this.startYaw;
        double dp = endPitch - this.startPitch;
        double length = Math.hypot(dy, dp);
        double yaw = this.startYaw + dy * s;
        double pitch = this.startPitch + dp * s;
        if (length > 1.0E-6) {
            double arc = Math.sin(Math.PI * s);
            double off = this.bow * arc;
            if (this.randomize) {
                off += Math.min(0.15, length * 0.004) * arc * Math.sin(this.tremorPhase + 2.0 * Math.PI * this.tremorHz * this.elapsedMs / 1000.0);
            }
            yaw += -dp / length * off;
            pitch += dy / length * off;
        }
        return new double[]{yaw, Math.max(-90.0, Math.min(90.0, pitch))};
    }

    /** 10t^3 - 15t^4 + 6t^5: the smoothest way from 0 to 1, with zero speed and acceleration at both ends. */
    static double minimumJerk(double t) {
        return t * t * t * (10.0 + t * (-15.0 + 6.0 * t));
    }
}
