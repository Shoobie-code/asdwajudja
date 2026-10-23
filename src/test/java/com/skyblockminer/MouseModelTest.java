package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MouseModelTest {
    private static final double FRAME = 1000.0 / 60.0;
    /** Degrees per mouse count at 50% sensitivity. */
    private static final double GCD = 0.15;

    /** Turns from (0, 0) toward a fixed goal; returns {yaw, pitch} per frame, starting with the start. */
    private static List<double[]> run(MouseModel mouse, double goalYaw, double goalPitch, int frames) {
        List<double[]> trace = new ArrayList<>();
        double yaw = 0.0;
        double pitch = 0.0;
        trace.add(new double[]{yaw, pitch});
        for (int i = 0; i < frames; i++) {
            double[] turn = mouse.step(yaw, pitch, goalYaw, goalPitch, FRAME, GCD);
            yaw += turn[0];
            pitch += turn[1];
            trace.add(new double[]{yaw, pitch});
        }
        return trace;
    }

    private static MouseModel mouse(long seed, double speed) {
        MouseModel mouse = new MouseModel(new Random(seed));
        mouse.setSpeed(speed);
        return mouse;
    }

    private static double speedAt(List<double[]> trace, int i) {
        return Math.hypot(trace.get(i + 1)[0] - trace.get(i)[0], trace.get(i + 1)[1] - trace.get(i)[1]);
    }

    @Test
    void settlesOnTheTarget() {
        for (long seed = 0; seed < 50; seed++) {
            List<double[]> trace = run(mouse(seed, 0.5), 70.0, -20.0, 180);
            double[] end = trace.get(trace.size() - 1);
            assertTrue(Math.hypot(end[0] - 70.0, end[1] + 20.0) <= 0.6, "seed " + seed + " ended at " + end[0] + ", " + end[1]);
        }
    }

    @Test
    void comesToRestAtHighSensitivity() {
        // At 100% sensitivity one count is about 0.61 degrees, so the goal can sit further than the 0.4 tolerance
        // from every reachable angle. The view must still come to rest instead of flicking one count back and forth.
        double gcd = Math.pow(0.8, 3.0) * 1.2;
        for (long seed = 0; seed < 20; seed++) {
            MouseModel mouse = mouse(seed, 0.5);
            double yaw = 0.0;
            double pitch = 0.0;
            int lateMoves = 0;
            double goalYaw = 65.5 * gcd;
            double goalPitch = 15.5 * gcd;
            for (int i = 0; i < 600; i++) {
                double[] turn = mouse.step(yaw, pitch, goalYaw, goalPitch, FRAME, gcd);
                yaw += turn[0];
                pitch += turn[1];
                if (i >= 300 && (turn[0] != 0.0 || turn[1] != 0.0)) {
                    lateMoves++;
                }
            }
            assertEquals(0, lateMoves, "seed " + seed + " still moving at " + yaw + ", " + pitch);
            assertTrue(Math.abs(yaw - goalYaw) <= gcd && Math.abs(pitch - goalPitch) <= gcd);
        }
    }

    @Test
    void everyStepIsWholeMouseCounts() {
        MouseModel mouse = mouse(3, 0.7);
        double yaw = 0.0;
        double pitch = 0.0;
        for (int i = 0; i < 120; i++) {
            double[] turn = mouse.step(yaw, pitch, 33.3, 12.1, FRAME, GCD);
            assertEquals(Math.round(turn[0] / GCD), turn[0] / GCD, 1.0E-9);
            assertEquals(Math.round(turn[1] / GCD), turn[1] / GCD, 1.0E-9);
            yaw += turn[0];
            pitch += turn[1];
        }
    }

    @Test
    void speedRisesThenFallsInsteadOfSnapping() {
        MouseModel mouse = mouse(7, 0.5);
        mouse.setRandomize(false);
        List<double[]> trace = run(mouse, 90.0, 0.0, 60);
        int peak = 0;
        for (int i = 0; i < trace.size() - 1; i++) {
            if (speedAt(trace, i) > speedAt(trace, peak)) {
                peak = i;
            }
        }
        assertTrue(peak > 1, "no acceleration");
        assertTrue(speedAt(trace, 0) < speedAt(trace, peak) / 3.0);
        // After the peak the steps get smaller again before the stop.
        int after = peak;
        while (after < trace.size() - 2 && speedAt(trace, after) > 0.0) {
            after++;
        }
        assertTrue(speedAt(trace, after - 1) < speedAt(trace, peak) / 3.0, "stopped dead from full speed");
    }

    @Test
    void neverTurnsFasterThanTheLimit() {
        for (double speed : new double[]{0.1, 0.5, 1.0}) {
            MouseModel mouse = mouse(11, speed);
            List<double[]> trace = run(mouse, 170.0, 40.0, 240);
            double limit = mouse.maxVelocity() * 1.15 * FRAME / 1000.0 + 2 * GCD;
            for (int i = 0; i < trace.size() - 1; i++) {
                assertTrue(speedAt(trace, i) <= limit, "frame " + i + " turned " + speedAt(trace, i) + " at speed " + speed);
            }
        }
    }

    @Test
    void overflicksAndUnderflicksBothHappen() {
        int over = 0;
        int under = 0;
        for (long seed = 0; seed < 200; seed++) {
            List<double[]> trace = run(mouse(seed, 0.5), 60.0, 0.0, 200);
            double furthest = 0.0;
            int firstStop = -1;
            for (int i = 1; i < trace.size() - 1; i++) {
                furthest = Math.max(furthest, trace.get(i)[0]);
                if (firstStop < 0 && i > 3 && speedAt(trace, i) == 0.0) {
                    firstStop = i;
                }
            }
            if (furthest > 60.5) {
                over++;
            }
            if (firstStop > 0 && trace.get(firstStop)[0] < 59.5) {
                under++;
            }
        }
        assertTrue(over > 60, "overflicks: " + over);
        assertTrue(under > 15, "underflicks: " + under);
    }

    @Test
    void strokesCurveInsteadOfDrawingStraightLines() {
        int curved = 0;
        for (long seed = 0; seed < 40; seed++) {
            List<double[]> trace = run(mouse(seed, 0.5), 80.0, 0.0, 40);
            double most = 0.0;
            for (double[] p : trace) {
                most = Math.max(most, Math.abs(p[1]));
            }
            if (most >= 0.5) {
                curved++;
            }
        }
        assertTrue(curved > 20, "curved strokes: " + curved);
    }

    @Test
    void wideTurnsLiftTheMouse() {
        MouseModel mouse = mouse(5, 0.8);
        List<double[]> trace = run(mouse, 300.0, 0.0, 300);
        // Find a pause mid-turn: several frames without movement while still far from the goal.
        int still = 0;
        boolean lifted = false;
        for (int i = 0; i < trace.size() - 1; i++) {
            if (speedAt(trace, i) == 0.0 && trace.get(i)[0] > 30.0 && trace.get(i)[0] < 270.0) {
                lifted |= ++still >= 4;
            } else {
                still = 0;
            }
        }
        assertTrue(lifted);
        assertTrue(Math.abs(trace.get(trace.size() - 1)[0] - 300.0) < 0.6);
    }

    @Test
    void followsAMovingGoalSmoothly() {
        MouseModel mouse = mouse(9, 0.6);
        double yaw = 0.0;
        double goal = 20.0;
        double worst = 0.0;
        for (int i = 0; i < 240; i++) {
            goal += 0.4;
            double[] turn = mouse.step(yaw, 0.0, goal, 0.0, FRAME, GCD);
            yaw += turn[0];
            if (i > 60) {
                worst = Math.max(worst, Math.abs(goal - yaw));
            }
        }
        assertTrue(worst < 8.0, "fell behind by " + worst);
    }
}
