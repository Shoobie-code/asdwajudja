package com.skyblockminer.update;

import com.skyblockminer.gui.Theme;
import com.skyblockminer.update.Releases.Channel;
import com.skyblockminer.update.SelfUpdate.State;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/**
 * Small panel in the title screen's top-left corner: the running build, Stable / Beta buttons and a quit button
 * once an update is waiting. Clicks are polled from GLFW like the macro's own screens.
 */
public final class TitleWidget {
    private static final int X = 6;
    private static final int Y = 6;
    private static final int PILL_H = 14;
    private static final int GOLD = 0xFFE8B341;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DARK = 0xFF1A1712;
    private static final int HOVER = 0xCC4A453C;
    private static final int OFF = 0xCC34302A;

    private static boolean wasDown;
    private static boolean primed;

    private TitleWidget() {
    }

    public static void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = primed && down && !wasDown;
        wasDown = down;
        primed = true;

        State state = SelfUpdate.state;
        String title = SelfUpdate.running.isEmpty() ? "Skymacro" : "Skymacro " + SelfUpdate.running;
        g.text(font, title, X, Y + 3, GOLD, true);
        int x = X + font.width(title) + 6;
        if (state != State.DISABLED) {
            for (Channel channel : Channel.values()) {
                int w = font.width(channel.label) + 10;
                boolean hover = inside(mouseX, mouseY, x, Y, x + w, Y + PILL_H);
                boolean active = channel == SelfUpdate.channel;
                g.fill(x, Y, x + w, Y + PILL_H, active ? GOLD : hover ? HOVER : OFF);
                g.text(font, channel.label, x + 5, Y + 3, active ? DARK : TEXT, false);
                if (clicked && hover) {
                    // Clicking the selected channel again just checks for an update.
                    SelfUpdate.check(channel);
                }
                x += w + 3;
            }
        }
        g.text(font, SelfUpdate.message, X, Y + PILL_H + 4, Theme.MUTED, true);

        if (state == State.STAGED) {
            String label = "Quit game to install";
            int w = font.width(label) + 10;
            int y = Y + PILL_H + 16;
            boolean hover = inside(mouseX, mouseY, X, y, X + w, y + PILL_H);
            g.fill(X, y, X + w, y + PILL_H, hover ? HOVER : OFF);
            g.text(font, label, X + 5, y + 3, GOLD, false);
            if (clicked && hover) {
                mc.stop();
            }
        }
    }

    private static boolean inside(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }
}
