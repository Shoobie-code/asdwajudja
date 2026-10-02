package com.skyblockminer.mixin;

import com.skyblockminer.MinerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
abstract class ClientPacketListenerMixin {
    @Inject(
        method = {"handleParticleEvent"},
        at = {@At("HEAD")}
    )
    private void skyblockminer$particle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            MinerMod.onParticle(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(
        method = {"handleSetEntityMotion"},
        at = {@At("HEAD")}
    )
    private void skyblockminer$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            MinerMod.onMotion(packet.id(), packet.movement());
        }
    }
}
