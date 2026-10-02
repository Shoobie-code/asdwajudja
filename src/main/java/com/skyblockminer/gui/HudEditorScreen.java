package com.skyblockminer.gui;

import com.skyblockminer.Macro;
import com.skyblockminer.MinerConfig;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Drag-and-drop layout editor for the HUD panels. Positions snap to screen edges and the center line. */
public final class HudEditorScreen extends Screen {
    private static final int SNAP = 5;
    private static final List<String> STATUS_SAMPLE = List.of("Farming (left lane)", "1,204 crops (5,812/h)  2 rewarps", "Break in 41:07");
    private static final List<String> TRACKER_SAMPLE = List.of("Enchanted Sugar: 312 (1,504/h)", "Sacks: 18,950 (91,442/h)");
    private static final List<String> TOAST_SAMPLE = List.of("Pop-up notifications", "appear here");

    private record Panel(String title, IntSupplier x, IntConsumer setX, IntSupplier y, IntConsumer setY) {
    }

    private final Macro macro;
    private final MinerConfig config;
    private final Runnable back;
    private final Input input = new Input();
    private final List<Panel> panels;
    private Panel dragging;
    private int grabX;
    private int grabY;

    private HudEditorScreen(Macro macro, Runnable back) {
        super(Component.literal("HUD layout"));
        this.macro = macro;
        this.config = macro.config();
        this.back = back;
        MinerConfig c = this.config;
        this.panels = List.of(
            new Panel("Status", () -> c.hudX, v -> c.hudX = v, () -> c.hudY, v -> c.hudY = v),
            new Panel("Loot tracker", () -> c.trackerX, v -> c.trackerX = v, () -> c.trackerY, v -> c.trackerY = v),
            new Panel("Notifications", () -> c.toastX, v -> c.toastX = v, () -> c.toastY, v -> c.toastY = v));
    }

    public static void open(Macro macro, Runnable back) {
        Minecraft.getInstance().gui.setScreen(new HudEditorScreen(macro, back));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.config.save();
        if (this.back != null) {
            this.back.run();
        } else {
            super.onClose();
        }
    }

    private List<String> lines(Panel panel) {
        if (panel == this.panels.get(0)) {
            List<String> live = this.macro.hudLines();
            return live.size() > 1 ? live.subList(1, live.size()) : STATUS_SAMPLE;
        }
        if (panel == this.panels.get(1)) {
            List<String> live = this.macro.trackerLines();
            return live.isEmpty() ? TRACKER_SAMPLE : live;
        }
        return TOAST_SAMPLE;
    }

    private int panelWidth(Panel panel) {
        return panel == this.panels.get(2) ? Toasts.width() : HudRenderer.width(this.font, panel.title(), this.lines(panel));
    }

    private int panelHeight(Panel panel) {
        return HudRenderer.height(this.lines(panel));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        this.input.poll();
        int accent = Theme.accent(this.config.accent);

        g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x22FFFFFF);
        g.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x22FFFFFF);

        if (this.input.clicked(0)) {
            if (this.button(mouseX, mouseY, -1)) {
                this.resetLayout();
            } else if (this.button(mouseX, mouseY, 1)) {
                this.onClose();
                return;
            } else {
                for (int i = this.panels.size() - 1; i >= 0; i--) {
                    Panel panel = this.panels.get(i);
                    int x = panel.x().getAsInt();
                    int y = panel.y().getAsInt();
                    if (Draw.inside(mouseX, mouseY, x, y, x + this.panelWidth(panel), y + this.panelHeight(panel))) {
                        this.dragging = panel;
                        this.grabX = mouseX - x;
                        this.grabY = mouseY - y;
                        break;
                    }
                }
            }
        }
        if (this.input.released(0)) {
            this.dragging = null;
        }
        if (this.dragging != null) {
            this.move(this.dragging, mouseX - this.grabX, mouseY - this.grabY);
        }

        for (Panel panel : this.panels) {
            int x = panel.x().getAsInt();
            int y = panel.y().getAsInt();
            int w = this.panelWidth(panel);
            int h = this.panelHeight(panel);
            HudRenderer.panel(g, this.font, x, y, panel.title(), this.lines(panel), accent);
            Draw.outline(g, x - 1, y - 1, x + w + 1, y + h + 1, panel == this.dragging ? accent : 0x55FFFFFF);
        }

        Draw.centered(g, this.font, "Drag the panels. Esc or Done to save.", this.width / 2, this.height - 52, Theme.MUTED);
        this.drawButton(g, mouseX, mouseY, -1, "Reset", Theme.OFF);
        this.drawButton(g, mouseX, mouseY, 1, "Done", accent);
    }

    private void move(Panel panel, int x, int y) {
        int w = this.panelWidth(panel);
        int h = this.panelHeight(panel);
        x = snap(x, w, this.width);
        y = snap(y, h, this.height);
        panel.setX().accept(Math.max(0, Math.min(this.width - w, x)));
        panel.setY().accept(Math.max(0, Math.min(this.height - h, y)));
    }

    private static int snap(int pos, int size, int screen) {
        if (Math.abs(pos - 2) < SNAP) {
            return 2;
        }
        if (Math.abs(pos + size - (screen - 2)) < SNAP) {
            return screen - 2 - size;
        }
        if (Math.abs(pos + size / 2 - screen / 2) < SNAP) {
            return screen / 2 - size / 2;
        }
        return pos;
    }

    private void resetLayout() {
        MinerConfig defaults = new MinerConfig();
        this.config.hudX = defaults.hudX;
        this.config.hudY = defaults.hudY;
        this.config.trackerX = defaults.trackerX;
        this.config.trackerY = defaults.trackerY;
        this.config.toastX = defaults.toastX;
        this.config.toastY = defaults.toastY;
    }

    /** Buttons sit side by side under the hint; side is -1 (left) or 1 (right). */
    private boolean button(int mx, int my, int side) {
        int x = this.width / 2 + (side < 0 ? -64 : 4);
        int y = this.height - 38;
        return Draw.inside(mx, my, x, y, x + 60, y + 18);
    }

    private void drawButton(GuiGraphicsExtractor g, int mx, int my, int side, String label, int color) {
        int x = this.width / 2 + (side < 0 ? -64 : 4);
        int y = this.height - 38;
        boolean hover = this.button(mx, my, side);
        Draw.round(g, x, y, x + 60, y + 18, hover ? Draw.mix(color, 0xFFFFFFFF, 0.15F) : color);
        Draw.centered(g, this.font, label, x + 30, y + 5, 0xFFFFFFFF);
    }
}
