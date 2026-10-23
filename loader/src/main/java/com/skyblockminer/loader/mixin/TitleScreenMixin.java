package com.skyblockminer.loader.mixin;

import com.skyblockminer.loader.TitleWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the loader's channel switcher on top of the title screen. Optional: a miss only hides the widget. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void skymacroLoader$drawWidget(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TitleWidget.render(g, mouseX, mouseY);
    }
}
