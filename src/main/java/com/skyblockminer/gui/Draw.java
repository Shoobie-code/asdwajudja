package com.skyblockminer.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing kit on top of the vanilla fill/text calls: rounded boxes, alpha fades and text fitting. */
public final class Draw {
    public static final int LINE = 9;

    private Draw() {
    }

    /** Box with 2px rounded corners. */
    public static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 4 || y2 - y1 < 4) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        g.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        g.fill(x1, y1 + 2, x2, y2 - 2, color);
        g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        g.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
    }

    /** One pixel outline. */
    public static void outline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /** Soft drop shadow drawn as stacked translucent boxes. */
    public static void shadow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float alpha) {
        for (int i = 1; i <= 4; i++) {
            g.fill(x1 - i, y1 - i + 2, x2 + i, y2 + i + 2, fade(0x22000000, alpha));
        }
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    public static void textShadow(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, x, y, color, true);
    }

    public static void centered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color, false);
    }

    public static void right(GuiGraphicsExtractor g, Font font, String text, int rightX, int y, int color) {
        g.text(font, text, rightX - font.width(text), y, color, false);
    }

    /** Shortens text with an ellipsis so it fits {@code width} pixels. */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        int ellipsis = font.width("...");
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsis > width) {
            end--;
        }
        return text.substring(0, end) + "...";
    }

    /** Multiplies a color's alpha by {@code alpha} (0-1). Text below alpha 4 is skipped by the renderer, so clamp up. */
    public static int fade(int color, float alpha) {
        int a = (int) ((color >>> 24) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return Math.max(a, 4) << 24 | color & 0xFFFFFF;
    }

    public static int withAlpha(int color, int alpha) {
        return alpha << 24 | color & 0xFFFFFF;
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a = (int) ((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = (int) ((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
        int gr = (int) ((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | gr << 8 | b;
    }

    /** Frame-rate independent approach of {@code current} toward {@code target}. */
    public static float approach(float current, float target, float deltaSeconds, float speed) {
        float t = 1.0F - (float) Math.exp(-speed * deltaSeconds);
        float next = current + (target - current) * t;
        return Math.abs(next - target) < 0.001F ? target : next;
    }

    public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }
}
