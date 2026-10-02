package com.skyblockminer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.skyblockminer.gui.ClickGuiScreen;
import com.skyblockminer.gui.HudEditorScreen;
import com.skyblockminer.gui.Toasts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
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
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinerMod implements ClientModInitializer {
    public static final String ID = "skyblockminer";
    public static final Logger LOGGER = LoggerFactory.getLogger("Skyblock Macro");
    private static Macro macro;
    private static MinerConfig config;
    private static MacroSettings settings;
    private static boolean openMenu;
    private static boolean openHud;

    @Override
    public void onInitializeClient() {
        config = MinerConfig.load();
        macro = new Macro(config);
        macro.applyConfig();
        settings = new MacroSettings(macro);
        Toasts.setEnabled(config.toasts);

        Category category = Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.toggle", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.menu", Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        KeyMapping hudKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.hud", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> macro.map.onChunkLoad(level, chunk));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            macro.map.saveNow();
            config.save();
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            macro.map.tick(mc);
            while (toggleKey.consumeClick()) {
                macro.toggle();
            }
            while (menuKey.consumeClick()) {
                openMenu = true;
            }
            while (hudKey.consumeClick()) {
                openHud = true;
            }
            // Screens open on the tick after a command so the closing chat screen does not replace them.
            if (openHud) {
                openHud = false;
                HudEditorScreen.open(macro, null);
            } else if (openMenu) {
                openMenu = false;
                ClickGuiScreen.open(macro, settings.categories());
            }
            macro.onTick(mc);
            macro.render(mc);
        });
        LevelRenderEvents.END_MAIN.register(context -> macro.onFrame());
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                macro.onChat(message.getString());
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> MinerCommands.register(dispatcher, macro, config, settings));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "status"), new MinerHud(macro, config));
    }

    static void openMenu() {
        openMenu = true;
    }

    static void openHudEditor() {
        openHud = true;
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
            mc.player.sendSystemMessage(Component.literal("[Macro] ").withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(text).withStyle(color)));
        }
    }
}
