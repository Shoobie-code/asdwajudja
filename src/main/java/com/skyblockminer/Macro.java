package com.skyblockminer;

import java.util.List;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class Macro {
    final MinerConfig config;
    final Rotator rotator = new Rotator();
    final MiningEngine mining = new MiningEngine();
    final WorldMap map = new WorldMap();
    final PathWalker walker = new PathWalker(this.map);
    final Combat combat = new Combat();
    final Commissions commissions = new Commissions();
    final RouteMiner routeMiner = new RouteMiner();
    final PowderMacro powder = new PowderMacro();
    final ChestSolver chests = new ChestSolver();
    final Routes routes = new Routes();
    final Failsafes failsafes = new Failsafes();
    final Random random = new Random();
    private final Webhook webhook = new Webhook();
    private boolean running;
    private MacroType mode = MacroType.MITHRIL;
    private String status = "Off";
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
    private boolean screenWasOpen;
    private String loggedStatus = "";
    private boolean pauseChanged;
    private boolean savedPauseOnLostFocus;
    private boolean rejoining;
    private long rejoinAt;
    private int rejoins;
    private Vec3 gotoTarget;

    Macro(MinerConfig config) {
        this.config = config;
        this.routes.load(config.route);
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

    boolean breaking() {
        return this.running && this.mining.breaking();
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
        if (mc.player != null && mc.level != null) {
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
            this.commissions.reset();
            this.routeMiner.reset();
            this.powder.reset();
            this.chests.reset();
            this.failsafes.reset(mc.player);
            this.rotator.stop();
            this.rotator.sync(mc.player);
            this.runInBackground(mc);
            this.status = "Starting";
            switch (type) {
                case COMMISSIONS:
                    MinerMod.message("Running commissions. Run /miner again to stop.", ChatFormatting.GREEN);
                    break;
                case ROUTE:
                    MinerMod.message(
                        "Route miner on route \""
                            + this.routes.name()
                            + "\" ("
                            + this.routes.points().size()
                            + " points, "
                            + this.config.routeBlocks
                            + "). Run /miner again to stop.",
                        ChatFormatting.GREEN
                    );
                    break;
                case POWDER:
                    MinerMod.message("Powder macro on. Run /miner again to stop.", ChatFormatting.GREEN);
                    break;
                case GOTO:
                    MinerMod.message("Walking to " + fmt(this.gotoTarget) + ".", ChatFormatting.GREEN);
                    this.walker.go(mc, List.of(this.gotoTarget), 1.0);
                    break;
                default:
                    MinerMod.message("Mining " + type.label + ". Run /miner again to stop.", ChatFormatting.GREEN);
            }
        }
    }

    public void goTo(Vec3 target) {
        this.gotoTarget = target;
        this.start(MacroType.GOTO);
    }

    public void stop(String reason) {
        if (this.running) {
            Minecraft mc = Minecraft.getInstance();
            this.running = false;
            this.releaseAll(mc);
            this.rotator.stop();
            this.restoreForeground(mc);
            this.status = "Off";
            MinerMod.message(reason + " (" + this.summary() + ")", ChatFormatting.YELLOW);
            if (this.mode != MacroType.GOTO) {
                this.notify("Miner stopped", reason + "\n" + this.summary(), 15844367);
            }
        }
    }

    void failsafe(String reason) {
        if (this.running) {
            this.stop("FAILSAFE: " + reason);
            alert();
            this.webhook.send(this.config, "FAILSAFE", reason, 15158332, true);
        }
    }

    void applyConfig() {
        this.rotator.setRandomize(this.config.randomize);
        this.mining.configure(this.config);
    }

    private void releaseAll(Minecraft mc) {
        this.mining.release(mc);
        this.walker.stop(mc);
        this.routeMiner.releaseKeys(mc);
        this.powder.releaseKeys(mc);
    }

    private void pause(Minecraft mc) {
        this.mining.release(mc);
        this.walker.releaseKeys(mc);
        this.routeMiner.releaseKeys(mc);
        this.powder.releaseKeys(mc);
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

    void command(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            String word = command.split(" ")[0];
            if (List.of("warp", "hub", "lobby", "l", "skyblock", "play", "is").contains(word)) {
                this.expectWorldUntil = System.currentTimeMillis() + 20000L;
            }

            mc.getConnection().sendCommand(command);
        }
    }

    void expectTeleport(long ms) {
        this.teleportOkUntil = System.currentTimeMillis() + ms;
    }

    void selectSlot(int slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && slot >= 0 && slot <= 8) {
            player.getInventory().setSelectedSlot(slot);
            this.failsafes.expectSlot(slot);
        }
    }

    boolean playerNear(Minecraft mc, Vec3 point, double radius) {
        if (mc.level != null && mc.getConnection() != null) {
            for (AbstractClientPlayer other : mc.level.players()) {
                if (other != mc.player
                    && other.getUUID().version() == 4
                    && mc.getConnection().getPlayerInfo(other.getUUID()) != null
                    && other.position().distanceToSqr(point) <= radius * radius) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    void message(String text) {
        MinerMod.message(text, ChatFormatting.AQUA);
    }

    void notify(String title, String text, int color) {
        this.webhook.send(this.config, title, text, color, false);
    }

    static void alert() {
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
        return this.mode == MacroType.COMMISSIONS && this.config.autoRejoin && this.rejoins < 5;
    }

    private void beginRejoin(String why) {
        Minecraft mc = Minecraft.getInstance();
        this.rejoins++;
        this.rejoining = true;
        this.rejoinAt = System.currentTimeMillis() + 5000L;
        this.expectWorldUntil = System.currentTimeMillis() + 60000L;
        this.pause(mc);
        this.walker.stop(mc);
        this.commissions.restart();
        this.message(why + ": going back to SkyBlock (attempt " + this.rejoins + " of 5)");
        this.notify("Rejoining", why, 15105570);
    }

    public void onChat(String text) {
        if (this.running) {
            if (text.contains(": ")) {
                String mention = this.config.chatAlerts ? Failsafes.chatMention(text, this.myName()) : null;
                long now = System.currentTimeMillis();
                if (mention != null && now - this.chatAlertAt > 5000L) {
                    this.chatAlertAt = now;
                    MinerMod.message(mention, ChatFormatting.RED);
                    alert();
                    this.webhook.send(this.config, "Chat alert", mention, 15105570, true);
                }
            } else {
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
                    this.stop("Your pickaxe broke");
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
                    } else if (this.minesBlocks()) {
                        this.stop("Your inventory is full");
                        alert();
                    }
                }
            }
        }
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
        if (this.running) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
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
        }
    }

    public void render(Minecraft mc) {
        if (this.config.showRoute && mc.player != null && !this.routes.points().isEmpty()) {
            boolean routeOn = this.running ? this.mode == MacroType.ROUTE : this.selected() == MacroType.ROUTE;
            if (routeOn) {
                this.routes.render(mc.player, this.running ? this.routeMiner.index() : -1);
            }
        }
    }

    public void onTick(Minecraft mc) {
        this.logStatus();
        boolean screenOpen = mc.gui.screen() != null;
        boolean screenClosed = this.screenWasOpen && !screenOpen;
        this.screenWasOpen = screenOpen;
        if (this.running) {
            long now = System.currentTimeMillis();
            boolean warping = now < this.expectWorldUntil;
            LocalPlayer player = mc.player;
            ClientLevel level = mc.level;
            if (player != null && level != null) {
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
                            this.rejoining = false;
                            this.expectWorldUntil = now + 15000L;
                            this.message("Back on SkyBlock.");
                        } else {
                            this.command("skyblock");
                            this.rejoinAt = now + 15000L;
                            this.expectWorldUntil = now + 30000L;
                        }
                    }
                } else {
                    if (player.hurtTime > 0) {
                        this.lastHurtAt = now;
                    }

                    if (this.config.failsafes) {
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
                            return;
                        }

                        String nearby = this.failsafes.nearbyPlayer(mc, player, this.config.playerRadius);
                        if (nearby != null) {
                            if (this.config.stopForPlayers) {
                                this.failsafe(nearby + " came within " + this.config.playerRadius + " blocks");
                                return;
                            }

                            MinerMod.message(nearby + " is within " + this.config.playerRadius + " blocks of you.", ChatFormatting.RED);
                            alert();
                        }
                    }

                    if (!this.checkHazards(mc, now)) {
                        this.updatePowder(mc, now);
                        if (this.nextStatusAt > 0L && now >= this.nextStatusAt) {
                            this.nextStatusAt = now + Math.max(1, this.config.statusEvery) * 60000L;
                            this.notify("Status: " + this.mode.label, this.status + "\n" + this.summary(), 3447003);
                        }

                        if (this.breakUntil > now) {
                            this.pause(mc);
                            this.rotator.stop();
                            this.rotator.sync(player);
                            this.status = "On a break (" + clock(this.breakUntil - now) + " left)";
                        } else {
                            if (this.breakUntil != 0L) {
                                this.breakUntil = 0L;
                                this.message("Break over, back to work.");
                            }

                            if (this.nextBreakAt > 0L && now >= this.nextBreakAt && this.mode != MacroType.GOTO) {
                                this.startBreak(now);
                            } else {
                                boolean ownMenu = this.mode == MacroType.COMMISSIONS && this.commissions.expectsMenu();
                                if (screenOpen && !ownMenu) {
                                    this.pause(mc);
                                    this.rotator.stop();
                                    this.rotator.sync(player);
                                    this.status = "Paused (menu open)";
                                } else {
                                    if (this.chests.active()) {
                                        this.walker.releaseKeys(mc);
                                        this.powder.releaseKeys(mc);
                                        String chest = this.chests.tick(this, mc, player, level);
                                        if (chest != null) {
                                            this.status = chest;
                                            return;
                                        }
                                    }

                                    switch (this.mode) {
                                        case COMMISSIONS:
                                            this.status = this.commissions.tick(this, mc, player, level);
                                            break;
                                        case ROUTE:
                                            this.status = this.routeMiner.tick(this, mc, player, level);
                                            break;
                                        case POWDER:
                                            this.status = this.powder.tick(this, mc, player, level);
                                            break;
                                        case GOTO:
                                            PathWalker.State state = this.walker.tick(mc, player, this.rotator, this.config);
                                            this.status = state == PathWalker.State.SEARCHING ? "Finding a path" : "Walking (" + this.walker.remaining() + " steps left)";
                                            if (state == PathWalker.State.DONE) {
                                                this.stop("Arrived at " + fmt(this.gotoTarget));
                                            } else if (state == PathWalker.State.FAILED) {
                                                this.stop("Could not walk to " + fmt(this.gotoTarget) + ": " + this.walker.failure());
                                            }
                                            break;
                                        default:
                                            this.status = this.mining.tick(mc, player, level, Targets.costs(this.mode.blocks, this.config), this.config, this.rotator);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (!warping) {
                    this.stop("Left the world");
                }
            }
        }
    }

    private void logStatus() {
        String kind = this.status.replaceAll("[0-9]+", "#");
        if (!kind.equals(this.loggedStatus)) {
            this.loggedStatus = kind;
            MinerMod.LOGGER.info("Status: {}", this.status);
        }
    }

    private boolean checkHazards(Minecraft mc, long now) {
        if (now - this.hazardCheckedAt >= 2000L && (this.config.heatLimit > 0 || this.config.coldLimit > 0)) {
            this.hazardCheckedAt = now;
            List<String> lines = Sidebar.lines(mc);
            int heat = Sidebar.heat(lines);
            if (this.config.heatLimit > 0 && heat >= this.config.heatLimit) {
                this.command("warp forge");
                this.stop("Heat reached " + heat + ": warped to the Forge");
                alert();
                return true;
            } else {
                int cold = Sidebar.cold(lines);
                if (this.config.coldLimit > 0 && cold >= this.config.coldLimit) {
                    this.command("warp camp");
                    this.stop("Cold reached -" + cold + ": warped to Base Camp");
                    alert();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
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
        this.scheduleBreak();
        this.nextBreakAt = this.nextBreakAt + (this.breakUntil - now);
    }

    private long vary(long ms) {
        return (long)(ms * (0.8 + this.random.nextDouble() * 0.4));
    }

    private void updatePowder(Minecraft mc, long now) {
        if (now - this.powderCheckedAt >= 5000L) {
            this.powderCheckedAt = now;
            long powder = TabList.powder(mc, this.mode != MacroType.POWDER && this.mode != MacroType.ROUTE ? "Mithril" : "Gemstone");
            if (powder >= 0L) {
                if (this.powderStart < 0L) {
                    this.powderStart = powder;
                }

                this.powderNow = powder;
            }
        }
    }

    double hours() {
        return Math.max(1.0E-6, (System.currentTimeMillis() - this.startedAt) / 3600000.0);
    }

    String stats() {
        double hours = this.hours();
        StringBuilder text = new StringBuilder(String.format("%d blocks (%.1f/min)", this.mining.broken(), this.mining.broken() / (hours * 60.0)));
        if (this.mode == MacroType.COMMISSIONS) {
            text.append(String.format("  %d comms (%.1f/h)", this.commissions.completed(), this.commissions.completed() / hours));
        }

        if (this.chests.opened() > 0) {
            text.append(String.format("  %d chests", this.chests.opened()));
        }

        if (this.powderStart >= 0L && this.powderNow > this.powderStart && hours > 0.02) {
            text.append(String.format("  %,d powder/h", (long)((this.powderNow - this.powderStart) / hours)));
        }

        return text.toString();
    }

    String summary() {
        return this.stats() + ", " + clock(System.currentTimeMillis() - this.startedAt);
    }

    String breakIn() {
        if (this.nextBreakAt > 0L && this.running) {
            long left = this.nextBreakAt - System.currentTimeMillis();
            return left > 0L ? "Break in " + clock(left) : null;
        } else {
            return null;
        }
    }

    static String clock(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return seconds >= 3600L
            ? String.format("%d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L)
            : String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    private static String fmt(Vec3 v) {
        return v == null ? "?" : String.format("%d %d %d", (int)Math.floor(v.x), (int)Math.floor(v.y), (int)Math.floor(v.z));
    }
}
