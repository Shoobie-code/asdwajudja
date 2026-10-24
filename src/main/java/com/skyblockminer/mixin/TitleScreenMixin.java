package com.skyblockminer.mixin;

import com.skyblockminer.update.TitleWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the update panel on the title screen. Optional: a miss only hides the panel. */
@Mixin({TitleScreen.class})
abstract class TitleScreenMixin {
    @Inject(
        method = {"extractRenderState"},
        at = {@At("TAIL")},
        require = 0
    )
    private void skyblockminer$updatePanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TitleWidget.render(g, mouseX, mouseY);
    }
}
