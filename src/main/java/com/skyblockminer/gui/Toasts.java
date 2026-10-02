package com.skyblockminer.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Short notification cards that slide in on the HUD (macro started, failsafes, pests, breaks...). */
public final class Toasts {
    public enum Kind {
        INFO(0xFF3B82F6),
        SUCCESS(Theme.GOOD),
        WARNING(Theme.WARN),
        ERROR(Theme.BAD);

        final int color;

        Kind(int color) {
            this.color = color;
        }
    }

    private record Toast(String title, String text, Kind kind, long at) {
    }

    private static final int MAX = 4;
    private static final long LIFETIME = 5000L;
    private static final long SLIDE = 220L;
    private static final int WIDTH = 190;
    private static final int HEIGHT = 26;
    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static boolean enabled = true;

    private Toasts() {
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            TOASTS.clear();
        }
    }

    public static void push(String title, String text, Kind kind) {
        if (!enabled) {
            return;
        }
        TOASTS.addFirst(new Toast(title, text == null ? "" : text, kind, System.currentTimeMillis()));
        while (TOASTS.size() > MAX) {
            TOASTS.removeLast();
        }
    }

    /** Draws the stack with its top-left corner at (x, y). */
    public static void render(GuiGraphicsExtractor g, Font font, int x, int y) {
        if (TOASTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int row = 0;
        for (Iterator<Toast> it = TOASTS.iterator(); it.hasNext(); ) {
            Toast toast = it.next();
            long age = now - toast.at;
            if (age > LIFETIME) {
                it.remove();
                continue;
            }
            float in = Math.min(1.0F, age / (float) SLIDE);
            float out = Math.min(1.0F, (LIFETIME - age) / (float) SLIDE);
            float shown = easeOut(Math.min(in, out));
            int left = x - (int) ((1.0F - shown) * (WIDTH + 8));
            int top = y + row * (HEIGHT + 4);
            draw(g, font, toast, left, top, shown, age);
            row++;
        }
    }

    private static void draw(GuiGraphicsExtractor g, Font font, Toast toast, int x, int y, float alpha, long age) {
        Draw.round(g, x, y, x + WIDTH, y + HEIGHT, Draw.fade(0xE60B0D11, alpha));
        g.fill(x, y + 2, x + 2, y + HEIGHT - 2, Draw.fade(toast.kind.color, alpha));
        Draw.text(g, font, Draw.fit(font, toast.title, WIDTH - 12), x + 7, y + 4, Draw.fade(toast.kind.color, alpha));
        Draw.text(g, font, Draw.fit(font, toast.text, WIDTH - 12), x + 7, y + 15, Draw.fade(Theme.TEXT, alpha));
        int bar = (int) ((WIDTH - 4) * (1.0F - age / (float) LIFETIME));
        g.fill(x + 2, y + HEIGHT - 1, x + 2 + bar, y + HEIGHT, Draw.fade(Draw.withAlpha(toast.kind.color, 0x90), alpha));
    }

    private static float easeOut(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    public static int width() {
        return WIDTH;
    }
}
