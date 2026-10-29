package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HudPresetTest {
    @Test
    void captureThenApplyRestoresEveryPanel() {
        MinerConfig a = new MinerConfig();
        a.hudX = 10;
        a.trackerY = 111;
        a.infoX = 222;
        a.skillsY = 333;
        int[] captured = a.captureHud();

        MinerConfig b = new MinerConfig();
        b.applyHud(captured);
        assertEquals(10, b.hudX);
        assertEquals(111, b.trackerY);
        assertEquals(222, b.infoX);
        assertEquals(333, b.skillsY);
    }

    @Test
    void applyIgnoresWrongSizedArrays() {
        MinerConfig c = new MinerConfig();
        int before = c.hudX;
        c.applyHud(new int[]{1, 2, 3});
        c.applyHud(null);
        assertEquals(before, c.hudX);
    }

    @Test
    void presetRoundTripsThroughTheMap() {
        MinerConfig c = new MinerConfig();
        c.toastX = 77;
        c.hudPresets.put("corner", c.captureHud());

        c.toastX = 5;
        c.applyHud(c.hudPresets.get("corner"));
        assertEquals(77, c.toastX);
    }
}
