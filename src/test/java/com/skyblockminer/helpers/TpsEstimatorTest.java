package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TpsEstimatorTest {
    @Test
    void fullSpeedReadsTwenty() {
        TpsEstimator e = new TpsEstimator();
        long game = 0;
        long real = 0;
        for (int i = 0; i < 10; i++) {
            game += 20;        // 20 ticks
            real += 1000;      // per real second
            e.update(game, real);
        }
        assertTrue(e.ready());
        assertEquals(20.0, e.tps(), 0.01);
        assertFalse(e.laggy(19.0));
    }

    @Test
    void halfSpeedIsDetectedAsLag() {
        TpsEstimator e = new TpsEstimator();
        long game = 0;
        long real = 0;
        for (int i = 0; i < 30; i++) {
            game += 10;        // only 10 ticks
            real += 1000;      // per real second -> ~10 TPS
            e.update(game, real);
        }
        assertEquals(10.0, e.tps(), 0.3);
        assertTrue(e.laggy(15.0));
    }

    @Test
    void largeGapsAreIgnored() {
        TpsEstimator e = new TpsEstimator();
        e.update(0, 0);
        e.update(5, 60_000); // 60s gap (warp/pause) -> skipped
        assertFalse(e.ready());
    }

    @Test
    void notLaggyBeforeAnyReading() {
        assertFalse(new TpsEstimator().laggy(19.0));
    }
}
