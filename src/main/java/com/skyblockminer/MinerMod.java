package com.skyblockminer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.skyblockminer.config.MacroSettings;
import com.skyblockminer.config.MarketSettings;
import com.skyblockminer.config.MinerConfig;
import com.skyblockminer.gui.ClickGuiScreen;
import com.skyblockminer.gui.HudEditorScreen;
import com.skyblockminer.gui.MarketScreen;
import com.skyblockminer.gui.Toasts;
import com.skyblockminer.helpers.AuctionSniper;
import com.skyblockminer.helpers.ChatFilter;
import com.skyblockminer.helpers.Helpers;
import com.skyblockminer.helpers.SkillTracker;
import com.skyblockminer.helpers.Solvers;
import com.skyblockminer.macro.Macro;
import com.skyblockminer.market.Flips;
import com.skyblockminer.market.Market;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
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
    private static Solvers solvers;
    private static boolean openMenu;
    private static boolean openHud;
    private static boolean openMarket;
    private static long marketOpenedAt;
    private static Market market;
    private static Flips flips;
    private static AuctionSniper sniper;
    private static SkillTracker skills;
    private static Helpers helpers;
    private static int marketTicks;
    private static String currentVersion = "";
    private static boolean changelogPending;

    @Override
    public void onInitializeClient() {
        config = MinerConfig.load();
        currentVersion = FabricLoader.getInstance().getModContainer(ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        changelogPending = MinerConfig.isNewVersion(currentVersion, config.lastSeenVersion);
        macro = new Macro(config);
        macro.applyConfig();
        settings = new MacroSettings(macro);
        solvers = new Solvers(config, macro::running);
        Toasts.setEnabled(config.toasts);
        market = new Market(FabricLoader.getInstance().getConfigDir().resolve(ID));
        flips = new Flips();
        sniper = new AuctionSniper(config, macro, market);
        skills = new SkillTracker(config.skillBest);
        helpers = new Helpers(config);

        Category category = Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.toggle", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.menu", Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        KeyMapping hudKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.hud", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        KeyMapping marketKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.market", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        KeyMapping panicKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyblockminer.panic", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        settings.keys(new com.skyblockminer.gui.Menu.KeyBinds() {
            @Override
            public String menuKey() {
                return menuKey.getTranslatedKeyMessage().getString();
            }

            @Override
            public String toggleKey() {
                return toggleKey.isUnbound() ? null : toggleKey.getTranslatedKeyMessage().getString();
            }

            @Override
            public void rebindMenu(int glfwKey) {
                menuKey.setKey(Type.KEYSYM.getOrCreate(glfwKey));
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }
        });

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> macro.map.onChunkLoad(level, chunk));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            macro.map.saveNow();
            config.save();
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (changelogPending && mc.player != null) {
                changelogPending = false;
                message("Updated to " + currentVersion + ". See the repo's Releases page for what changed.", ChatFormatting.AQUA);
                config.lastSeenVersion = currentVersion;
                config.save();
            }
            macro.map.tick(mc);
            while (toggleKey.consumeClick()) {
                macro.toggle();
            }
            // Unlike the toggle key, the panic key only ever stops.
            while (panicKey.consumeClick()) {
                macro.stop("Panic key");
            }
            while (menuKey.consumeClick()) {
                openMenu = true;
            }
            while (hudKey.consumeClick()) {
                openHud = true;
            }
            while (marketKey.consumeClick()) {
                openMarket = true;
            }
            // Screens open on the tick after a command so the closing chat screen does not replace them.
            if (openHud) {
                openHud = false;
                HudEditorScreen.open(macro, null);
            } else if (openMenu) {
                openMenu = false;
                ClickGuiScreen.open(macro, settings.layout());
            } else if (openMarket) {
                openMarket = false;
                marketOpenedAt = System.currentTimeMillis();
                market.refreshSoon();
                MarketScreen.open(market, flips, com.skyblockminer.gui.Theme.accent(config.accent), command -> {
                    if (mc.getConnection() != null) {
                        mc.getConnection().sendCommand(command);
                    }
                });
            }
            tickMarket(mc);
            macro.onTick(mc);
            macro.render(mc);
            solvers.tick(mc);
            helpers.tick(mc);
            macro.recording.tick(mc);
        });
        LevelRenderEvents.END_MAIN.register(context -> macro.onFrame());
        ClientReceiveMessageEvents.GAME.register(MinerMod::onGameMessage);
        // Filtered lines are hidden from the chat but still handled, so alerts and trackers keep working.
        ClientReceiveMessageEvents.GAME_CANCELED.register(MinerMod::onGameMessage);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !ChatFilter.hide(message.getString(), config));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> MinerCommands.register(dispatcher, macro, config, settings, market));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "status"), new MinerHud(macro, config, skills, helpers));
    }

    private static void onGameMessage(Component message, boolean overlay) {
        if (!overlay) {
            macro.onChat(message.getString());
        } else {
            String text = message.getString();
            macro.onActionBar(text);
            if (config.skillTracker) {
                skills.onActionBar(text, System.currentTimeMillis(), macro.running() ? macro.mode().id : null);
            }
        }
    }

    static void openMenu() {
        openMenu = true;
    }

    static void openHudEditor() {
        openHud = true;
    }

    public static void openMarket() {
        openMarket = true;
    }

    static SkillTracker skills() {
        return skills;
    }

    /** Market data is fetched while the market screen was used in the last 15 minutes, or for auction scanning. */
    private static void tickMarket(Minecraft mc) {
        if (!config.marketEnabled) {
            return;
        }
        boolean viewing = System.currentTimeMillis() - marketOpenedAt < 15 * 60_000L || mc.gui.screen() instanceof MarketScreen;
        market.tick(viewing, config.auctionScan);
        if (++marketTicks >= 20) {
            marketTicks = 0;
            flips.update(market, MarketSettings.flips(config));
        }
        sniper.tick(mc);
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

    public static void message(String text, ChatFormatting color) {
        com.skyblockminer.helpers.MacroLog.SHARED.add(text);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Macro] ").withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(text).withStyle(color)));
        }
    }
}
