package com.skyblockminer.mixin;

import com.skyblockminer.MinerMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientLevel.class})
abstract class ClientLevelMixin {
    @Inject(
        method = {"setServerVerifiedBlockState"},
        at = {@At("TAIL")}
    )
    private void skyblockminer$blockChanged(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        MinerMod.onBlockChanged((ClientLevel) (Object) this, pos, state);
    }
}
