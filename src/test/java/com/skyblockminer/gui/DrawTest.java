package com.skyblockminer.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrawTest {
    @Test
    void fadeScalesAlphaOnly() {
        assertEquals(0x80123456, Draw.fade(0xFF123456, 128 / 255.0F + 0.001F));
        assertEquals(0x04123456, Draw.fade(0xFF123456, 0.0F), "alpha is clamped so text stays renderable");
    }

    @Test
    void mixInterpolatesChannels() {
        assertEquals(0xFF000000, Draw.mix(0xFF000000, 0xFFFFFFFF, 0.0F));
        assertEquals(0xFFFFFFFF, Draw.mix(0xFF000000, 0xFFFFFFFF, 1.0F));
        assertEquals(0xFF7F7F7F, Draw.mix(0xFF000000, 0xFFFFFFFF, 0.5F));
    }

    @Test
    void approachConverges() {
        float value = 0.0F;
        for (int i = 0; i < 200; i++) {
            value = Draw.approach(value, 1.0F, 0.016F, 16.0F);
        }
        assertEquals(1.0F, value);
        assertTrue(Draw.approach(0.0F, 1.0F, 0.016F, 16.0F) > 0.1F);
    }
}
