package com.skyblockminer.gui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the HUD panels; shared by the live HUD and the layout editor so both look identical. */
public final class HudRenderer {
    private static final int PAD = 5;
    private static final int LINE = 11;

    private HudRenderer() {
    }

    public static int width(Font font, String title, List<String> lines) {
        int width = font.width(title);
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        return width + PAD * 2 + 2;
    }

    public static int height(List<String> lines) {
        return PAD * 2 + LINE * (lines.size() + 1) - 2;
    }

    /** A panel with an accent title; the first line is bright, the rest muted. */
    public static void panel(GuiGraphicsExtractor g, Font font, int x, int y, String title, List<String> lines, int accent) {
        int w = width(font, title, lines);
        int h = height(lines);
        Draw.round(g, x, y, x + w, y + h, Theme.HUD_BACK);
        g.fill(x, y + 2, x + 2, y + h - 2, accent);
        Draw.textShadow(g, font, title, x + PAD + 2, y + PAD, accent);
        for (int i = 0; i < lines.size(); i++) {
            Draw.textShadow(g, font, lines.get(i), x + PAD + 2, y + PAD + LINE * (i + 1), i == 0 ? Theme.TEXT : Theme.MUTED);
        }
    }
}
