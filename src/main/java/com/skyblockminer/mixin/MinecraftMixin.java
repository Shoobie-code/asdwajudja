package com.skyblockminer.mixin;

import com.skyblockminer.MinerMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Minecraft.class})
abstract class MinecraftMixin {
    @Inject(
        method = {"continueAttack"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void skyblockminer$keepBreaking(boolean down, CallbackInfo ci) {
        if (MinerMod.breaking()) {
            ci.cancel();
        }
    }
}
