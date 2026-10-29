package com.skyblockminer.macro;

import com.skyblockminer.MinerMod;
import com.skyblockminer.config.MinerConfig;
import com.skyblockminer.game.Server;
import com.skyblockminer.game.Sidebar;
import com.skyblockminer.game.TabList;
import com.skyblockminer.gui.Toasts;
import com.skyblockminer.macro.combat.Combat;
import com.skyblockminer.macro.combat.CombatMacro;
import com.skyblockminer.macro.farming.FarmBuilder;
import com.skyblockminer.macro.farming.FarmingMacro;
import com.skyblockminer.macro.farming.Recording;
import com.skyblockminer.macro.fishing.FishingMacro;
import com.skyblockminer.macro.foraging.ForagingMacro;
import com.skyblockminer.macro.mining.BlockMining;
import com.skyblockminer.macro.mining.ChestSolver;
import com.skyblockminer.macro.mining.Commissions;
import com.skyblockminer.macro.mining.ExcavatorMacro;
import com.skyblockminer.macro.mining.GlaciteCommissions;
import com.skyblockminer.macro.mining.MiningEngine;
import com.skyblockminer.macro.mining.PowderMacro;
import com.skyblockminer.macro.mining.RouteMiner;
import com.skyblockminer.macro.mining.Routes;
import com.skyblockminer.macro.wynncraft.WynnCombatMacro;
import com.skyblockminer.macro.wynncraft.WynnStatus;
import com.skyblockminer.pathing.PathWalker;
import com.skyblockminer.pathing.WorldMap;
import com.skyblockminer.rotation.Rotator;
import com.skyblockminer.script.ScriptRoutine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Runs the selected {@link Routine} and everything shared between macros: failsafes, scheduled breaks,
 * pausing for menus, auto-rejoin (with an optional walk back to a saved spot), stats and notifications.
 */
public final class Macro {
    private static final int MAX_REJOINS = 5;

    public final MinerConfig config;
    public final Rotator rotator = new Rotator();
    public final MiningEngine mining = new MiningEngine();
    public final WorldMap map = new WorldMap();
    public final PathWalker walker = new PathWalker(this.map, new PathWalker.Host() {
        @Override
        public void expectTeleport(long ms) {
            Macro.this.expectTeleport(ms);
        }

        @Override
        public void selectSlot(int slot) {
            Macro.this.selectSlot(slot);
        }
    });
    public final Combat combat = new Combat();
    final Commissions commissions = new Commissions();
    final RouteMiner routeMiner = new RouteMiner();
    final PowderMacro powder = new PowderMacro();
    final FarmingMacro farming = new FarmingMacro();
    final ForagingMacro foraging = new ForagingMacro();
    final FishingMacro fishing = new FishingMacro();
    final FarmBuilder builder = new FarmBuilder();
    final CombatMacro combatMacro = new CombatMacro();
    final WynnCombatMacro wynnCombat = new WynnCombatMacro();
    public final WynnStatus wynn = new WynnStatus();
    final GlaciteCommissions glacite = new GlaciteCommissions();
    final ExcavatorMacro excavator = new ExcavatorMacro();
    public final ScriptRoutine scripts = new ScriptRoutine(this, FabricLoader.getInstance().getConfigDir().resolve(MinerMod.ID).resolve("scripts"));
    final AutoSell autoSell = new AutoSell();
    public final Recording recording = new Recording();
    final Prices prices = new Prices();
    final ChestSolver chests = new ChestSolver();
    public final Routes routes = new Routes();
    final Failsafes failsafes = new Failsafes();
    final ItemTracker tracker = new ItemTracker();
    final com.skyblockminer.helpers.CoinTracker coins = new com.skyblockminer.helpers.CoinTracker();
    public final com.skyblockminer.helpers.TpsEstimator tps = new com.skyblockminer.helpers.TpsEstimator();
    public final Random random = new Random();
    private final Map<MacroType, Routine> routines = new EnumMap<>(MacroType.class);
    private final List<Routine> allRoutines;
    private final Webhook webhook = new Webhook();
    private final AltAlert altAlert = new AltAlert();
    private boolean running;
    private MacroType mode = MacroType.MITHRIL;
    private String status = "Off";
    private List<String> hudLines = List.of();
    private List<String> trackerLines = List.of();
    private int trackerIn;
    private ClientLevel startLevel;
    private long expectWorldUntil;
    private long teleportOkUntil;
    private long startedAt;
    private long nextBreakAt;
    private long breakUntil;
    private long nextStatusAt;
    private long powderStart = -1L;
    private long powderNow = -1L;
    private long powderCheckedAt;
    private long hazardCheckedAt;
    private long lastHurtAt;
    private Vec3 pendingPush;
    private int pushTicks;
    private long chatAlertAt;
    private long customAlertAt;
    private boolean screenWasOpen;
    private String loggedStatus = "";
    private boolean pauseChanged;
    private boolean savedPauseOnLostFocus;
    private boolean rejoining;
    private long rejoinAt;
    /** Sends of /skyblock during the current rejoin; see {@link RejoinPolicy}. */
    private int rejoinTries;
    private int rejoins;
    private Vec3 gotoTarget;
    private Vec3 home;
    private long homeAt;
    private boolean walkingHome;
    private boolean outsideHours;

    public Macro(MinerConfig config) {
        this.config = config;
        this.routes.load(config.route);
        this.recording.load();
        for (MacroType type : MacroType.values()) {
            if (type.minesInPlace()) {
                this.routines.put(type, new BlockMining(type));
            }
        }
        this.routines.put(MacroType.ROUTE, this.routeMiner);
        this.routines.put(MacroType.POWDER, this.powder);
        this.routines.put(MacroType.COMMISSIONS, this.commissions);
        this.routines.put(MacroType.FARMING, this.farming);
        this.routines.put(MacroType.FORAGING, this.foraging);
        this.routines.put(MacroType.FISHING, this.fishing);
        this.routines.put(MacroType.BUILDER, this.builder);
        this.routines.put(MacroType.COMBAT, this.combatMacro);
        this.routines.put(MacroType.WYNN_COMBAT, this.wynnCombat);
        this.routines.put(MacroType.GLACITE, this.glacite);
        this.routines.put(MacroType.EXCAVATOR, this.excavator);
        this.routines.put(MacroType.SCRIPT, this.scripts);
        this.allRoutines = List.copyOf(this.routines.values());
    }

    public boolean running() {
        return this.running;
    }

    public MacroType mode() {
        return this.mode;
    }

    public MacroType selected() {
        return MacroType.parseOr(this.config.mode, MacroType.MITHRIL);
    }

    public String status() {
        return this.status;
    }

    /** HUD text built once per tick (title first), so rendering every frame allocates nothing. */
    public List<String> hudLines() {
        return this.hudLines;
    }

    public MinerConfig config() {
        return this.config;
    }

    private Routine routine() {
        return this.routines.get(this.mode);
    }

    public boolean breaking() {
        if (!this.running) {
            return false;
        }
        Routine routine = this.routine();
        return this.mining.breaking() || routine != null && routine.breaking();
    }

    public void toggle() {
        if (this.running) {
            this.stop("Stopped");
        } else {
            this.start(this.selected());
        }
    }

    public void select(MacroType type) {
        if (type != MacroType.GOTO) {
            this.config.mode = type.id;
            this.config.save();
            if (this.running && this.mode != MacroType.GOTO) {
                this.start(type);
            }
        }
    }

    public void start(MacroType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!this.config.masterSwitch) {
            MinerMod.message("Macros are switched off (menu → Settings → Master switch)", ChatFormatting.RED);
            Toasts.push("Master switch", "Macros are switched off", Toasts.Kind.WARNING);
            return;
        }
        String wrongServer = Server.mismatch(type, Server.current(mc));
        if (wrongServer != null) {
            MinerMod.message(wrongServer, ChatFormatting.RED);
            Toasts.push("Wrong server", wrongServer, Toasts.Kind.WARNING);
            return;
        }
        if (this.running) {
            this.stop("Switching to " + type.label);
        }
        if (type != MacroType.GOTO && !type.id.equals(this.config.mode)) {
            this.config.mode = type.id;
            this.config.save();
        }

        this.mode = type;
        this.running = true;
        this.startLevel = mc.level;
        this.startedAt = System.currentTimeMillis();
        this.powderStart = -1L;
        this.rejoining = false;
        this.rejoins = 0;
        this.pendingPush = null;
        this.scheduleBreak();
        this.breakUntil = 0L;
        this.nextStatusAt = this.config.statusEvery > 0 ? this.startedAt + this.config.statusEvery * 60000L : 0L;
        this.applyConfig();
        this.mining.reset(mc);
        this.mining.resetCount();
        this.combat.reset(mc, this.walker);
        this.allRoutines.forEach(Routine::reset);
        this.chests.reset();
        this.autoSell.reset();
        this.outsideHours = false;
        this.tracker.reset();
        this.coins.reset();
        this.trackerIn = 0;
        this.failsafes.reset(mc.player);
        this.rotator.stop();
        this.rotator.sync(mc.player);
        this.runInBackground(mc);
        this.status = "Starting";
        this.walkingHome = false;
        this.home = null;

        Routine routine = this.routine();
        Vec3 spot = routine == null ? null : routine.home(this.config);
        if (spot != null && mc.player.position().distanceToSqr(spot) > 4.0) {
            this.walkHome(spot, 0L);
        }

        if (type.untested()) {
            MinerMod.message(type.label + " has not been tested in game yet. Watch the first few minutes and report what goes wrong.", ChatFormatting.YELLOW);
        }
        switch (type) {
            case COMMISSIONS -> this.message("Running commissions.");
            case ROUTE -> this.message("Route miner on route \"" + this.routes.name() + "\" (" + this.routes.points().size() + " points, "
                + this.config.routeBlocks + ").");
            case GOTO -> {
                this.message("Walking to " + fmt(this.gotoTarget) + ".");
                this.walker.go(mc, List.of(this.gotoTarget), 1.0);
            }
            default -> this.message(type.label + " macro started.");
        }
        if (type != MacroType.GOTO) {
            Toasts.push(type.label + " started", "Press your toggle key or /sm to stop", Toasts.Kind.SUCCESS);
        }
    }

    public void goTo(Vec3 target) {
        this.gotoTarget = target;
        this.start(MacroType.GOTO);
    }

    public void stop(String reason) {
        if (!this.running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        this.running = false;
        this.releaseAll(mc);
        this.allRoutines.forEach(Routine::stopped);
        this.rotator.stop();
        this.restoreForeground(mc);
        this.status = "Off";
        this.hudLines = List.of();
        this.trackerLines = List.of();
        MinerMod.message(reason + " (" + this.summary() + ")", ChatFormatting.YELLOW);
        String hint = StopHints.hintFor(reason);
        if (hint != null) {
            MinerMod.message(hint, ChatFormatting.GRAY);
        }
        if (this.mode != MacroType.GOTO) {
            this.notify("stop", "Macro stopped", reason + "\n" + this.summary(), 15844367);
            Toasts.push("Macro stopped", reason, reason.startsWith("FAILSAFE") ? Toasts.Kind.ERROR : Toasts.Kind.INFO);
        }
    }

    void failsafe(String reason) {
        if (this.running) {
            this.stop("FAILSAFE: " + reason);
            alert();
            String altCommand = this.altAlert.onCheck(this.config, System.currentTimeMillis());
            if (altCommand != null) {
                this.command(altCommand);
            }
            this.webhook.send(this.config, "FAILSAFE", reason, 15158332, true);
        }
    }

    public void applyConfig() {
        this.rotator.configure(this.config);
        this.mining.configure(this.config);
        com.skyblockminer.gui.Theme.colorBlind = this.config.colorBlind;
    }

    private void releaseAll(Minecraft mc) {
        this.mining.release(mc);
        this.walker.stop(mc);
        for (Routine routine : this.allRoutines) {
            routine.releaseKeys(mc);
        }
    }

    private void pause(Minecraft mc) {
        this.mining.release(mc);
        this.walker.releaseKeys(mc);
        for (Routine routine : this.allRoutines) {
            routine.releaseKeys(mc);
        }
    }

    private void runInBackground(Minecraft mc) {
        if (this.config.ungrab && mc.gui.screen() == null) {
            mc.mouseHandler.releaseMouse();
        }
        if (this.config.keepRunningUnfocused && !this.pauseChanged) {
            this.savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
            this.pauseChanged = true;
        }
    }

    private void restoreForeground(Minecraft mc) {
        if (this.pauseChanged) {
            mc.options.pauseOnLostFocus = this.savedPauseOnLostFocus;
            this.pauseChanged = false;
        }
        if (this.config.ungrab && mc.gui.screen() == null && mc.getWindow().isFocused() && !mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.grabMouse();
        }
    }

    public void command(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        String clean = command.startsWith("/") ? command.substring(1) : command;
        String word = clean.split(" ")[0];
        if (List.of("warp", "hub", "lobby", "l", "skyblock", "play", "is", "garden").contains(word)) {
            this.expectWorldUntil = System.currentTimeMillis() + 20000L;
        }
        mc.getConnection().sendCommand(clean);
    }

    public void expectTeleport(long ms) {
        this.teleportOkUntil = System.currentTimeMillis() + ms;
    }

    public void selectSlot(int slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && slot >= 0 && slot <= 8) {
            player.getInventory().setSelectedSlot(slot);
            this.failsafes.expectSlot(slot);
        }
    }

    public boolean playerNear(Minecraft mc, Vec3 point, double radius) {
        for (AbstractClientPlayer other : Failsafes.realPlayers(mc, mc.player)) {
            if (other.position().distanceToSqr(point) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    public void message(String text) {
        MinerMod.message(text, ChatFormatting.AQUA);
    }

    /** Sends a Discord message for {@code event} (one of {@link Webhook#EVENTS}) when the user has it switched on. */
    public void notify(String event, String title, String text, int color) {
        if (Webhook.wants(this.config, event)) {
            this.webhook.send(this.config, title, text, color, false);
        }
    }

    /** Sends a test embed so the webhook can be checked from the GUI. */
    public void testWebhook() {
        if (this.config.webhookUrl.isBlank() || !Webhook.URL.matcher(this.config.webhookUrl).matches()) {
            Toasts.push("Webhook", "That is not a Discord webhook URL", Toasts.Kind.ERROR);
            return;
        }
        this.webhook.send(this.config, "Test message", "Notifications are working.", 3447003, true);
        Toasts.push("Webhook", "Test message sent", Toasts.Kind.SUCCESS);
    }

    /** Sends the alt message now, ignoring the cooldown, so it can be checked from the GUI. */
    public void testAltAlert() {
        String command = AltAlert.command(this.config);
        if (command == null) {
            Toasts.push("Alt alert", "Set a valid Minecraft name for your other account first", Toasts.Kind.ERROR);
            return;
        }
        if (Minecraft.getInstance().getConnection() == null) {
            Toasts.push("Alt alert", "Join a server first", Toasts.Kind.ERROR);
            return;
        }
        this.command(command);
        Toasts.push("Alt alert", "Sent /" + command, Toasts.Kind.SUCCESS);
    }

    public static void alert() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.0F, 1.0F));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.0F));
        if (!mc.getWindow().isFocused()) {
            GLFW.glfwRequestWindowAttention(mc.getWindow().handle());
        }
    }

    private boolean minesBlocks() {
        return this.mode.minesInPlace() || this.mode == MacroType.ROUTE || this.mode == MacroType.POWDER;
    }

    private boolean canRejoin() {
        // Rejoining means going back to SkyBlock, so Wynncraft macros stop on a world change instead.
        return this.mode != MacroType.GOTO && this.mode.server() == Server.Kind.HYPIXEL && this.config.autoRejoin && this.rejoins < MAX_REJOINS;
    }

    private void beginRejoin(String why) {
        Minecraft mc = Minecraft.getInstance();
        this.rejoins++;
        this.rejoining = true;
        this.rejoinTries = 0;
        this.walkingHome = false;
        this.home = null;
        this.rejoinAt = System.currentTimeMillis() + 5000L;
        this.expectWorldUntil = System.currentTimeMillis() + 60000L;
        this.pause(mc);
        this.walker.stop(mc);
        this.commissions.restart();
        this.message(why + ": going back to SkyBlock (attempt " + this.rejoins + " of " + MAX_REJOINS + ")");
        this.notify("rejoin", "Rejoining", why, 15105570);
        Toasts.push("Rejoining", why, Toasts.Kind.WARNING);
    }

    /** Back on SkyBlock after a rejoin: warp to the routine's area and walk to its saved spot. */
    private void finishRejoin(long now) {
        this.rejoining = false;
        this.expectWorldUntil = now + 15000L;
        this.message("Back on SkyBlock.");
        Routine routine = this.routine();
        if (routine == null || this.mode == MacroType.COMMISSIONS) {
            return;
        }
        String warp = routine.rejoinWarp(this.config);
        if (warp != null) {
            this.command(warp);
        }
        Vec3 spot = routine.home(this.config);
        if (spot != null) {
            this.walkHome(spot, now + 4000L);
        } else {
            routine.resume();
        }
    }

    private void walkHome(Vec3 spot, long at) {
        this.home = spot;
        this.homeAt = at;
        this.walkingHome = false;
    }

    public void onChat(String text) {
        this.walker.onChat(text);
        if (!this.running) {
            return;
        }
        if (this.mode == MacroType.SCRIPT) {
            this.scripts.onAnyChat(text);
        }
        if (!this.config.alertWords.isEmpty() && com.skyblockminer.helpers.ChatFilter.matchesCustom(text, this.config.alertWords)) {
            long now = System.currentTimeMillis();
            if (now - this.customAlertAt > 5000L) {
                this.customAlertAt = now;
                MinerMod.message("Alert: " + text, ChatFormatting.YELLOW);
                alert();
                Toasts.push("Chat alert", text, Toasts.Kind.WARNING);
            }
        }
        if (text.contains(": ")) {
            String mention = this.config.chatAlerts ? Failsafes.chatMention(text, this.myName()) : null;
            long now = System.currentTimeMillis();
            if (mention != null && now - this.chatAlertAt > 5000L) {
                this.chatAlertAt = now;
                MinerMod.message(mention, ChatFormatting.RED);
                alert();
                this.webhook.send(this.config, "Chat alert", mention, 15105570, true);
                Toasts.push("Chat alert", mention, Toasts.Kind.WARNING);
            }
            return;
        }

        this.tracker.onChat(text);
        Routine routine = this.routine();
        if (routine != null) {
            routine.onChat(this, text);
            if (!this.running) {
                return;
            }
        }
        if (text.contains("is empty! Refuel it") || text.contains("too little fuel to keep mining")) {
            this.stop("Your drill is out of fuel");
            alert();
        } else if (text.contains("You were spawned in Limbo")) {
            if (this.canRejoin()) {
                this.command("lobby");
                this.beginRejoin("Sent to Limbo");
            } else {
                this.stop("Sent to Limbo");
                alert();
            }
        } else if (text.startsWith("Oh no! Your")) {
            this.stop("Your tool broke");
            alert();
        } else if (text.contains("Commission Complete!")) {
            this.commissions.refreshSoon();
        } else if (text.contains("You uncovered a treasure chest")) {
            if (this.config.openChests && this.minesBlocks()) {
                this.chests.spawned();
            }
        } else if (text.contains("CHEST LOCKPICKED")) {
            this.chests.lockpicked();
        } else if (text.contains("is now available!")) {
            this.mining.onAbilityReady();
        } else if (text.contains("inventory is full") || text.contains("Inventory full")) {
            if (this.mode == MacroType.COMMISSIONS && this.config.sellTrash) {
                this.commissions.onInventoryFull(this);
            } else if (this.mode != MacroType.GOTO && this.mode != MacroType.COMMISSIONS && this.config.autoSell && !this.config.sellItems.isEmpty()) {
                this.pause(Minecraft.getInstance());
                this.autoSell.begin();
            } else if (this.mode != MacroType.GOTO) {
                this.stop("Your inventory is full");
                alert();
            }
        }
    }

    /** Action bar text, read for Wynncraft health, mana and spell clicks. */
    public void onActionBar(String text) {
        this.wynn.onActionBar(text, System.currentTimeMillis());
    }

    public void onParticle(ParticleOptions particle, double x, double y, double z) {
        if (this.running) {
            this.chests.onParticle(particle, x, y, z);
        }
    }

    public void onMotion(int entityId, Vec3 motion) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (this.running && player != null && entityId == player.getId() && this.config.failsafes) {
            this.pendingPush = motion;
            this.pushTicks = 3;
        }
    }

    private String myName() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? "" : player.getGameProfile().name();
    }

    public void onFrame() {
        if (!this.running) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < this.expectWorldUntil || now < this.teleportOkUntil) {
            this.rotator.sync(player);
        } else if (this.config.failsafes) {
            String reason = this.failsafes.checkRotation(this.rotator.externalChange(player));
            if (reason != null) {
                this.failsafe(reason);
                return;
            }
        }
        this.rotator.update(player);
    }

    public void render(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        if (this.config.showWaypoints) {
            for (Map.Entry<String, int[]> entry : this.config.waypoints.entrySet()) {
                int[] p = entry.getValue();
                if (p != null && p.length == 3) {
                    BlockPos pos = new BlockPos(p[0], p[1], p[2]);
                    Vec3 pp = mc.player.position();
                    double dx = pp.x - (p[0] + 0.5);
                    double dy = pp.y - (p[1] + 0.5);
                    double dz = pp.z - (p[2] + 0.5);
                    int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(0xFF55FF55, 2.0F, 0x3055FF55));
                    Gizmos.billboardTextOverBlock(entry.getKey() + " (" + distance + "m)", pos, 0, 0xFF55FF55, 0.32F);
                }
            }
        }
        if (this.config.showRoute && !this.routes.points().isEmpty()) {
            boolean routeOn = this.running ? this.mode == MacroType.ROUTE : this.selected() == MacroType.ROUTE;
            if (routeOn) {
                this.routes.render(mc.player, this.running ? this.routeMiner.index() : -1);
            }
        }
        if (!this.running) {
            return;
        }
        Routine routine = this.routine();
        if (routine != null) {
            routine.render(this, mc.player);
        }
        if (this.config.showPath) {
            this.walker.render(mc.player);
        }
        if (this.config.showTarget && this.mining.breaking() && this.mining.target() != null) {
            Gizmos.cuboid(this.mining.target().pos(), GizmoStyle.strokeAndFill(0xFF55FFFF, 2.0F, 0x2055FFFF));
        }
    }

    public void onTick(Minecraft mc) {
        this.config.saveIfDirty();
        this.logStatus();
        if (mc.level != null) {
            this.tps.update(mc.level.getGameTime(), System.currentTimeMillis());
        }
        boolean screenOpen = mc.gui.screen() != null;
        boolean screenClosed = this.screenWasOpen && !screenOpen;
        this.screenWasOpen = screenOpen;
        if (!this.running) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean warping = now < this.expectWorldUntil;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            if (!warping) {
                this.stop("Left the world");
            }
            return;
        }

        this.tickRunning(mc, player, level, now, warping, screenOpen, screenClosed);
        if (!this.running) {
            return;
        }
        this.tracker.tick(mc, player);
        this.hudLines = this.buildHud();
        if (--this.trackerIn <= 0) {
            this.trackerIn = 20;
            this.coins.update(Sidebar.purse(Sidebar.lines(mc)), now);
            this.trackerLines = this.buildTracker();
        }
    }

    private void tickRunning(Minecraft mc, LocalPlayer player, ClientLevel level, long now, boolean warping, boolean screenOpen, boolean screenClosed) {
        if (level != this.startLevel) {
            this.startLevel = level;
            this.failsafes.reset(player);
            this.rotator.sync(player);
            if (!warping) {
                if (!this.canRejoin()) {
                    this.stop("World changed (server swap or kick)");
                    return;
                }
                this.beginRejoin("Moved to another server");
                warping = true;
            }
        }

        if (screenClosed && this.config.ungrab && mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }

        if (this.rejoining) {
            this.status = "Rejoining SkyBlock";
            if (now >= this.rejoinAt) {
                if (TabList.area(mc) != null) {
                    this.finishRejoin(now);
                } else if (RejoinPolicy.giveUp(this.rejoinTries)) {
                    this.rejoining = false;
                    this.stop("Could not get back to SkyBlock after " + this.rejoinTries + " tries");
                    alert();
                    return;
                } else {
                    this.command("skyblock");
                    this.rejoinTries++;
                    this.rejoinAt = now + RejoinPolicy.backoffMs(this.rejoinTries);
                    this.expectWorldUntil = now + 30000L;
                }
            }
            return;
        }

        if (player.hurtTime > 0) {
            this.lastHurtAt = now;
        }
        if (this.config.failsafes && this.checkFailsafes(mc, player, level, now, warping)) {
            return;
        }
        if (this.checkHazards(mc, now)) {
            return;
        }
        this.updatePowder(mc, now);
        if (this.nextStatusAt > 0L && now >= this.nextStatusAt) {
            this.nextStatusAt = now + Math.max(1, this.config.statusEvery) * 60000L;
            this.notify("status", "Status: " + this.mode.label, this.status + "\n" + this.summary(), 3447003);
        }

        if (this.breakUntil > now) {
            this.pause(mc);
            this.rotator.stop();
            this.rotator.sync(player);
            this.status = "On a break (" + clock(this.breakUntil - now) + " left)";
            return;
        }
        if (this.config.pauseOnLag && this.tps.laggy(this.config.lagThreshold)) {
            this.pause(mc);
            this.rotator.stop();
            this.rotator.sync(player);
            this.status = String.format("Paused: server lag (%.0f TPS)", this.tps.tps());
            return;
        }
        if (this.breakUntil != 0L) {
            this.breakUntil = 0L;
            this.message("Break over, back to work.");
            Routine routine = this.routine();
            if (routine != null) {
                routine.resume();
            }
        }
        if (this.nextBreakAt > 0L && now >= this.nextBreakAt && this.mode != MacroType.GOTO) {
            this.startBreak(now);
            return;
        }

        if (this.mode != MacroType.GOTO && this.checkActiveHours(mc, player)) {
            return;
        }

        if (this.autoSell.active()) {
            this.pause(mc);
            this.rotator.stop();
            this.status = this.autoSell.tick(this, mc, player);
            Routine routine = this.routine();
            if (!this.autoSell.active() && this.running && routine != null) {
                routine.resume();
            }
            return;
        }

        Routine active = this.routine();
        boolean ownMenu = this.mode == MacroType.COMMISSIONS && this.commissions.expectsMenu() || active != null && active.ownsMenu();
        if (screenOpen && !ownMenu) {
            this.pause(mc);
            this.rotator.stop();
            this.rotator.sync(player);
            this.status = "Paused (menu open)";
            return;
        }

        if (this.home != null) {
            this.status = this.tickWalkHome(mc, player, now);
            return;
        }

        if (this.chests.active()) {
            this.walker.releaseKeys(mc);
            this.powder.releaseKeys(mc);
            String chest = this.chests.tick(this, mc, player, level);
            if (chest != null) {
                this.status = chest;
                return;
            }
        }

        if (this.mode == MacroType.GOTO) {
            PathWalker.State state = this.walker.tick(mc, player, this.rotator, this.config);
            this.status = state == PathWalker.State.SEARCHING ? "Finding a path" : "Walking (" + this.walker.remaining() + " steps left)";
            if (state == PathWalker.State.DONE) {
                this.stop("Arrived at " + fmt(this.gotoTarget));
            } else if (state == PathWalker.State.FAILED) {
                this.stop("Could not walk to " + fmt(this.gotoTarget) + ": " + this.walker.failure());
            }
        } else {
            String next = this.routine().tick(this, mc, player, level);
            this.status = this.running ? next : "Off";
        }
    }

    /** Returns true while outside the configured active hours, keeping the macro idle in place. */
    private boolean checkActiveHours(Minecraft mc, LocalPlayer player) {
        Schedule hours = Schedule.parse(this.config.activeHours);
        int minute = Schedule.now();
        if (hours == null || hours.active(minute)) {
            if (this.outsideHours) {
                this.outsideHours = false;
                this.message("Active hours started, back to work.");
                Routine routine = this.routine();
                if (routine != null) {
                    routine.resume();
                }
            }
            return false;
        }
        if (!this.outsideHours) {
            this.outsideHours = true;
            this.message("Outside active hours, waiting until " + hours.start() + ".");
            this.notify("waiting", "Waiting", "Outside active hours, resuming at " + hours.start(), 3447003);
            Toasts.push("Active hours", "Waiting until " + hours.start(), Toasts.Kind.INFO);
        }
        this.pause(mc);
        this.rotator.stop();
        this.rotator.sync(player);
        this.status = "Waiting for active hours (" + clock(hours.minutesUntilActive(minute) * 60000L) + ")";
        return true;
    }

    /** Returns true when a failsafe stopped the macro. */
    private boolean checkFailsafes(Minecraft mc, LocalPlayer player, ClientLevel level, long now, boolean warping) {
        String reason = this.failsafes.checkTick(player, warping || now < this.teleportOkUntil);
        if (reason == null && this.pendingPush != null && --this.pushTicks <= 0) {
            boolean slime = level.getBlockState(player.blockPosition().below()).is(Blocks.SLIME_BLOCK);
            reason = !warping && now >= this.teleportOkUntil ? Failsafes.checkVelocity(this.pendingPush, now - this.lastHurtAt < 1500L, slime) : null;
            this.pendingPush = null;
        }
        if (reason == null && !warping) {
            reason = this.failsafes.checkCage(level, player, this.mining::recentlyMined);
        }
        if (reason == null && now > this.expectWorldUntil + 10000L) {
            reason = this.failsafes.playerInside(mc, player);
        }
        if (reason != null) {
            this.failsafe(reason);
            return true;
        }

        String nearby = this.failsafes.nearbyPlayer(mc, player, this.config.playerRadius);
        if (nearby != null) {
            if (this.config.stopForPlayers) {
                this.failsafe(nearby + " came within " + this.config.playerRadius + " blocks");
                return true;
            }
            MinerMod.message(nearby + " is within " + this.config.playerRadius + " blocks of you.", ChatFormatting.RED);
            Toasts.push("Player nearby", nearby + " is within " + this.config.playerRadius + " blocks", Toasts.Kind.WARNING);
            alert();
        }
        return false;
    }

    private String tickWalkHome(Minecraft mc, LocalPlayer player, long now) {
        if (now < this.homeAt) {
            this.pause(mc);
            return "Waiting to walk back";
        }
        if (!this.walkingHome) {
            this.walkingHome = true;
            this.walker.go(mc, List.of(this.home), 0.6);
        }
        PathWalker.State state = this.walker.tick(mc, player, this.rotator, this.config);
        if (state == PathWalker.State.DONE) {
            this.walker.stop(mc);
            this.home = null;
            this.walkingHome = false;
            this.rotator.stop();
            Routine routine = this.routine();
            if (routine != null) {
                routine.resume();
            }
            return "Arrived";
        }
        if (state == PathWalker.State.FAILED) {
            this.stop("Could not walk back to the saved spot: " + this.walker.failure());
            return "Off";
        }
        return state == PathWalker.State.SEARCHING ? "Finding the way back" : "Walking back (" + this.walker.remaining() + " steps)";
    }

    private void logStatus() {
        String kind = this.status.replaceAll("[0-9]+", "#");
        if (!kind.equals(this.loggedStatus)) {
            this.loggedStatus = kind;
            MinerMod.LOGGER.debug("Status: {}", this.status);
        }
    }

    /** Returns true when heat or cold forced a stop. Only applies to mining. */
    private boolean checkHazards(Minecraft mc, long now) {
        if (this.mode.category != MacroType.Category.MINING || now - this.hazardCheckedAt < 2000L
            || this.config.heatLimit <= 0 && this.config.coldLimit <= 0) {
            return false;
        }
        this.hazardCheckedAt = now;
        List<String> lines = Sidebar.lines(mc);
        int heat = Sidebar.heat(lines);
        if (this.config.heatLimit > 0 && heat >= this.config.heatLimit) {
            this.command("warp forge");
            this.stop("Heat reached " + heat + ": warped to the Forge");
            alert();
            return true;
        }
        int cold = Sidebar.cold(lines);
        if (this.config.coldLimit > 0 && cold >= this.config.coldLimit) {
            this.command("warp camp");
            this.stop("Cold reached -" + cold + ": warped to Base Camp");
            alert();
            return true;
        }
        return false;
    }

    private void scheduleBreak() {
        this.nextBreakAt = this.config.breakEvery > 0 ? System.currentTimeMillis() + this.vary(this.config.breakEvery * 60000L) : 0L;
    }

    private void startBreak(long now) {
        Minecraft mc = Minecraft.getInstance();
        this.breakUntil = now + this.vary(Math.max(1, this.config.breakLength) * 60000L);
        this.pause(mc);
        this.rotator.stop();
        this.message("Taking a " + clock(this.breakUntil - now) + " break.");
        Toasts.push("Break", "Back in " + clock(this.breakUntil - now), Toasts.Kind.INFO);
        this.scheduleBreak();
        this.nextBreakAt = this.nextBreakAt + (this.breakUntil - now);
    }

    private long vary(long ms) {
        return (long) (ms * (0.8 + this.random.nextDouble() * 0.4));
    }

    private void updatePowder(Minecraft mc, long now) {
        if (this.mode.category != MacroType.Category.MINING || now - this.powderCheckedAt < 5000L) {
            return;
        }
        this.powderCheckedAt = now;
        long powder = TabList.powder(mc, this.mode != MacroType.POWDER && this.mode != MacroType.ROUTE ? "Mithril" : "Gemstone");
        if (powder >= 0L) {
            if (this.powderStart < 0L) {
                this.powderStart = powder;
            }
            this.powderNow = powder;
        }
    }

    public double hours() {
        return Math.max(1.0E-6, (System.currentTimeMillis() - this.startedAt) / 3600000.0);
    }

    public long elapsedMs() {
        return this.running ? System.currentTimeMillis() - this.startedAt : 0L;
    }

    /** A human-readable session report: the macro, how long it has run, its stats, loot rates, TPS and tick timings. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("Skyblock Macro session report");
        lines.add("Macro: " + this.mode.label + (this.running ? " (running)" : " (stopped)"));
        lines.add("Elapsed: " + clock(this.elapsedMs()));
        String stats = this.stats();
        if (!stats.isBlank()) {
            lines.add("Stats: " + stats);
        }
        lines.addAll(this.trackerLines());
        if (this.tps.ready()) {
            lines.add(String.format("Server TPS: %.1f", this.tps.tps()));
        }
        lines.addAll(com.skyblockminer.helpers.ModuleTimings.SHARED.lines());
        return lines;
    }

    public String stats() {
        double hours = this.hours();
        StringBuilder text = new StringBuilder();
        if (this.mode.category == MacroType.Category.MINING) {
            text.append(String.format("%d blocks (%.1f/min)", this.mining.broken(), this.mining.broken() / (hours * 60.0)));
        }
        if (this.mode == MacroType.COMMISSIONS) {
            text.append(String.format("  %d comms (%.1f/h)", this.commissions.completed(), this.commissions.completed() / hours));
        }
        if (this.chests.opened() > 0) {
            text.append(String.format("  %d chests", this.chests.opened()));
        }
        if (this.powderStart >= 0L && this.powderNow > this.powderStart && hours > 0.02) {
            text.append(String.format("  %,d powder/h", (long) ((this.powderNow - this.powderStart) / hours)));
        }
        if (text.isEmpty()) {
            Routine routine = this.routine();
            String line = routine == null ? null : routine.hudLine(this);
            text.append(line == null ? "" : line);
        }
        return text.toString().trim();
    }

    String summary() {
        return this.stats() + ", " + clock(System.currentTimeMillis() - this.startedAt);
    }

    String breakIn() {
        if (this.nextBreakAt <= 0L || !this.running) {
            return null;
        }
        long left = this.nextBreakAt - System.currentTimeMillis();
        return left > 0L ? "Break in " + clock(left) : null;
    }

    private List<String> buildHud() {
        List<String> lines = new ArrayList<>(6);
        lines.add(this.mode.label + "  " + clock(System.currentTimeMillis() - this.startedAt));
        lines.add(this.status);
        Routine routine = this.routine();
        String detail = routine == null ? null : routine.hudLine(this);
        if (detail != null) {
            lines.add(detail);
        }
        if (this.mode.category == MacroType.Category.MINING) {
            lines.add(this.stats());
        }
        if (this.autoSell.sold() > 0) {
            lines.add("Sold " + this.autoSell.sold() + " stacks");
        }
        String breakIn = this.breakIn();
        if (breakIn != null) {
            lines.add(breakIn);
        }
        return Collections.unmodifiableList(lines);
    }

    /** Loot lines for the tracker HUD, rebuilt once a second. */
    public List<String> trackerLines() {
        return this.trackerLines;
    }

    private List<String> buildTracker() {
        double hours = this.hours();
        List<String> lines = new ArrayList<>(7);
        for (Map.Entry<String, Integer> entry : this.tracker.top(5)) {
            lines.add(String.format("%s: %,d (%,.0f/h)", entry.getKey(), entry.getValue(), entry.getValue() / hours));
        }
        if (this.tracker.sacks() > 0L) {
            lines.add(String.format("Sacks: %,d (%,.0f/h)", this.tracker.sacks(), this.tracker.sacks() / hours));
        }
        if (this.coins.ready() && this.coins.gained() != 0L) {
            lines.add(String.format("Coins: %+,d (%,.0f/h)", this.coins.gained(), this.coins.perHour()));
        }
        if (this.config.bazaarPrices) {
            this.prices.refreshIfStale();
            if (this.prices.ready()) {
                double coins = this.tracker.value(this.prices::price);
                if (coins > 0.0) {
                    lines.add(String.format("Profit: %,.0f coins (%,.0f/h)", coins, coins / hours));
                }
            }
        }
        return Collections.unmodifiableList(lines);
    }

    /** Instant-sell value of a display name from the Bazaar cache, or 0 when prices are off or not loaded yet. */
    public double itemValue(String displayName) {
        if (!this.config.bazaarPrices) {
            return 0.0;
        }
        this.prices.refreshIfStale();
        return this.prices.ready() ? this.prices.price(displayName) : 0.0;
    }

    /** Whether Bazaar prices are enabled and loaded, so {@link #itemValue} returns real numbers. */
    public boolean pricesReady() {
        if (this.config.bazaarPrices) {
            this.prices.refreshIfStale();
        }
        return this.config.bazaarPrices && this.prices.ready();
    }

    public static String clock(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return seconds >= 3600L
            ? String.format("%d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L)
            : String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    private static String fmt(Vec3 v) {
        return v == null ? "?" : String.format("%d %d %d", (int) Math.floor(v.x), (int) Math.floor(v.y), (int) Math.floor(v.z));
    }
}
