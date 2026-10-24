package com.skyblockminer;

import com.skyblockminer.config.MinerConfig;
import com.skyblockminer.gui.HudEditorScreen;
import com.skyblockminer.gui.HudRenderer;
import com.skyblockminer.gui.Theme;
import com.skyblockminer.gui.Toasts;
import com.skyblockminer.helpers.Helpers;
import com.skyblockminer.helpers.SkillTracker;
import com.skyblockminer.macro.Macro;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** In-game overlay: macro status, loot tracker and notifications, at the positions set in the HUD editor. */
final class MinerHud implements HudElement {
    private final Macro macro;
    private final MinerConfig config;
    private final SkillTracker skills;
    private List<String> skillLines = List.of();
    private long skillLinesAt;

    private final Helpers helpers;

    MinerHud(Macro macro, MinerConfig config, SkillTracker skills, Helpers helpers) {
        this.macro = macro;
        this.config = config;
        this.skills = skills;
        this.helpers = helpers;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof HudEditorScreen) {
            return;
        }
        int accent = Theme.accent(this.config.accent);
        if (this.config.hud && this.macro.running()) {
            List<String> lines = this.macro.hudLines();
            if (!lines.isEmpty()) {
                HudRenderer.panel(graphics, mc.font, this.config.hudX, this.config.hudY, lines.get(0), lines.subList(1, lines.size()), accent);
            }
        }
        if (this.config.itemTracker && this.macro.running()) {
            List<String> loot = this.macro.trackerLines();
            if (!loot.isEmpty()) {
                HudRenderer.panel(graphics, mc.font, this.config.trackerX, this.config.trackerY, "Loot tracker", loot, accent);
            }
        }
        if (this.config.skillTracker) {
            long now = System.currentTimeMillis();
            if (now - this.skillLinesAt > 500L) {
                this.skillLinesAt = now;
                this.skillLines = this.skills.lines(now);
            }
            if (!this.skillLines.isEmpty()) {
                HudRenderer.panel(graphics, mc.font, this.config.skillsX, this.config.skillsY, "Skills", this.skillLines, accent);
            }
        }
        List<String> info = this.helpers.lines();
        if (!info.isEmpty()) {
            HudRenderer.panel(graphics, mc.font, this.config.infoX, this.config.infoY, "Info", info, accent);
        }
        Toasts.render(graphics, mc.font, this.config.toastX, this.config.toastY);
    }
}
