package com.skyblockminer;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class MinerHud implements HudElement {
    private static final int TITLE = -11141121;
    private static final int TEXT = -1;
    private static final int MUTED = -5592406;
    private static final int BACKDROP = -1879048192;
    private final Macro macro;
    private final MinerConfig config;

    MinerHud(Macro macro, MinerConfig config) {
        this.macro = macro;
        this.config = config;
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (this.config.hud && this.macro.running()) {
            Minecraft mc = Minecraft.getInstance();
            List<String> lines = new ArrayList<>();
            lines.add("Skyblock Miner - " + this.macro.mode().label);
            lines.add(this.macro.status());
            if (this.macro.mode() == MacroType.COMMISSIONS && this.macro.commissions.current() != null) {
                double progress = this.macro.commissions.currentProgress();
                lines.add(this.macro.commissions.current() + (progress >= 0.0 ? String.format(" (%.0f%%)", progress * 100.0) : ""));
            } else if (this.macro.mode() == MacroType.ROUTE && this.macro.routeMiner.index() >= 0) {
                lines.add(
                    String.format(
                        "Route %s: point %d/%d, lap %d",
                        this.macro.routes.name(),
                        this.macro.routeMiner.index() + 1,
                        this.macro.routes.points().size(),
                        this.macro.routeMiner.laps() + 1
                    )
                );
            } else if (this.macro.mode() == MacroType.POWDER && this.macro.powder.heading() != null) {
                lines.add("Heading " + this.macro.powder.heading().getName() + ", " + this.macro.powder.steps() + " blocks walked");
            }

            lines.add(this.macro.stats());
            String breakIn = this.macro.breakIn();
            if (breakIn != null) {
                lines.add(breakIn);
            }

            int width = 0;

            for (String line : lines) {
                width = Math.max(width, mc.font.width(line));
            }

            graphics.fill(2, 2, 8 + width, 6 + lines.size() * 10, -1879048192);

            for (int i = 0; i < lines.size(); i++) {
                graphics.text(mc.font, lines.get(i), 5, 5 + i * 10, i == 0 ? -11141121 : (i == 1 ? -1 : -5592406), false);
            }
        }
    }
}
