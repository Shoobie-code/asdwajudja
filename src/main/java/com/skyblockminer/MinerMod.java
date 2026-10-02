package com.skyblockminer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.Load;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.Game;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.EndMain;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinerMod implements ClientModInitializer {
    public static final String ID = "skyblockminer";
    public static final Logger LOGGER = LoggerFactory.getLogger("Skyblock Miner");
    private static Macro macro;
    private static MinerConfig config;
    private static boolean openMenu;

    public void onInitializeClient() {
        config = MinerConfig.load();
        macro = new Macro(config);
        macro.applyConfig();
        Category category = Category.register(Identifier.fromNamespaceAndPath("skyblockminer", "main"));
        KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.toggle", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category)
        );
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.menu", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category)
        );
        ClientChunkEvents.CHUNK_LOAD.register((Load)(level, chunk) -> macro.map.onChunkLoad(level, chunk));
        ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)mc -> macro.map.saveNow());
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
            macro.map.tick(mc);

            while (toggleKey.consumeClick()) {
                macro.toggle();
            }

            while (menuKey.consumeClick()) {
                openMenu = true;
            }

            if (openMenu) {
                openMenu = false;
                MinerScreen.open(macro);
            }

            macro.onTick(mc);
            macro.render(mc);
        });
        LevelRenderEvents.END_MAIN.register((EndMain)context -> macro.onFrame());
        ClientReceiveMessageEvents.GAME.register((Game)(message, overlay) -> {
            if (!overlay) {
                macro.onChat(message.getString());
            }
        });
        ClientCommandRegistrationCallback.EVENT
            .register((ClientCommandRegistrationCallback)(dispatcher, context) -> MinerCommands.register(dispatcher, macro, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyblockminer", "status"), new MinerHud(macro, config));
    }

    static void openMenu() {
        openMenu = true;
    }

    static Macro macro() {
        return macro;
    }

    public static void onParticle(ParticleOptions particle, double x, double y, double z) {
        if (macro != null) {
            macro.onParticle(particle, x, y, z);
        }
    }

    public static void onBlockChanged(ClientLevel level, BlockPos pos, BlockState state) {
        if (macro != null) {
            macro.map.onBlockChanged(level, pos, state);
        }
    }

    public static void onMotion(int entityId, Vec3 motion) {
        if (macro != null) {
            macro.onMotion(entityId, motion);
        }
    }

    public static boolean breaking() {
        return macro != null && macro.breaking();
    }

    static void message(String text, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Miner] ").withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(text).withStyle(color)));
        }
    }
}
