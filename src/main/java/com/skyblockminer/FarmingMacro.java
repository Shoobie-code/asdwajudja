package com.skyblockminer;

import com.skyblockminer.gui.Toasts;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lane farming for Garden plots: faces a fixed direction, holds one set of movement keys while breaking
 * the crops under the crosshair, and switches to the other set whenever the player stops moving (end of a
 * lane). Optionally warps back to the farm start when it reaches a configured rewarp point.
 */
final class FarmingMacro implements Routine {
    enum Pattern {
        VERTICAL("vertical", "Vertical crops (wheat, carrot, potato, wart)", "A+W", "D+W", 3.0),
        MELON("melon", "Melon / Pumpkin", "A", "D", 28.0),
        CANE("cane", "Sugar cane", "A+S", "D+S", 0.0),
        CACTUS("cactus", "Cactus", "A", "D", 0.0),
        COCOA("cocoa", "Cocoa beans", "W", "S", -10.0),
        MUSHROOM("mushroom", "Mushroom", "A+W", "D+W", 4.0),
        CUSTOM("custom", "Custom keys", null, null, 3.0),
        ECHO("echo", "Recorded movement (/sm echo record)", null, null, 3.0);

        static final List<Pattern> ALL = List.of(values());
        final String id;
        final String label;
        final String left;
        final String right;
        final double pitch;

        Pattern(String id, String label, String left, String right, double pitch) {
            this.id = id;
            this.label = label;
            this.left = left;
            this.right = right;
            this.pitch = pitch;
        }

        static Pattern parse(String id) {
            for (Pattern pattern : ALL) {
                if (pattern.id.equalsIgnoreCase(id)) {
                    return pattern;
                }
            }
            return null;
        }
    }

    static final List<String> TOOLS = List.of("hoe", "dicer", "cactus knife", "fungi cutter", "chopper", "sugar cane");
    private static final Set<String> CROPS = Set.of(
        "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:nether_wart", "minecraft:sugar_cane",
        "minecraft:cactus", "minecraft:melon", "minecraft:pumpkin", "minecraft:cocoa", "minecraft:red_mushroom",
        "minecraft:brown_mushroom", "minecraft:sunflower", "minecraft:rose_bush"
    );
    static final List<String> PESTS = List.of(
        "Beetle", "Cricket", "Earthworm", "Fly", "Locust", "Mite", "Mosquito", "Moth", "Rat", "Slug", "Mouse", "Mantis"
    );
    private static final double VACUUM_RANGE = 12.0;
    private static final double PEST_SEARCH = 40.0;
    private static final long PEST_HUNT_MS = 45000L;
    private static final long PEST_CLEAR_MS = 3000L;
    private static final int REWARP_SETTLE_MS = 2500;
    private static final int MAX_FUTILE_SWITCHES = 4;

    private final Keys keys = new Keys();
    private boolean left = true;
    private boolean aligning = true;
    private float baseYaw = Float.NaN;
    private Vec3 lastPos;
    private int stillTicks;
    private int laneTicks;
    private int futileSwitches;
    private long resumeAt;
    private boolean breaking;
    private BlockPos lastBroken;
    private int crops;
    private int rewarps;
    private final Combat pestCombat = new Combat();
    private long huntUntil;
    private long noPestSince;
    private Vec3 huntCenter;
    private int pests;
    private int frame;
    private final Visitors visitors = new Visitors();

    @Override
    public void reset() {
        this.left = true;
        this.aligning = true;
        this.baseYaw = Float.NaN;
        this.lastPos = null;
        this.stillTicks = 0;
        this.laneTicks = 0;
        this.futileSwitches = 0;
        this.resumeAt = 0L;
        this.breaking = false;
        this.lastBroken = null;
        this.crops = 0;
        this.rewarps = 0;
        this.frame = 0;
        this.visitors.reset();
        this.huntUntil = 0L;
        this.pests = 0;
        this.pestCombat.clear();
    }

    @Override
    public void releaseKeys(Minecraft mc) {
        this.keys.release();
        this.stopBreaking(mc);
    }

    @Override
    public boolean breaking() {
        return this.breaking;
    }

    int crops() {
        return this.crops;
    }

    @Override
    public void resume() {
        this.frame = 0;
        this.aligning = true;
        this.left = true;
        this.lastPos = null;
        this.stillTicks = 0;
        this.laneTicks = 0;
        this.futileSwitches = 0;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        MinerConfig config = macro.config;
        long now = System.currentTimeMillis();
        if (now < this.resumeAt) {
            this.releaseKeys(mc);
            return "Rewarping";
        }
        if (this.huntUntil > 0L) {
            return this.huntPests(macro, mc, player, level, now);
        }
        if (this.visitors.active()) {
            this.keys.release();
            this.stopBreaking(mc);
            String status = this.visitors.tick(macro, mc, player, level);
            if (!this.visitors.active()) {
                macro.rotator.stop();
                if (macro.config.farmWarpCommand.isBlank()) {
                    this.resume();
                } else {
                    this.rewarp(macro, mc, "Back from the visitors");
                }
            }
            return status;
        }

        int tool = Inv.toolSlot(player, config.farmToolSlot, TOOLS);
        if (tool < 0) {
            macro.stop("No farming tool in the hotbar");
            return "No tool";
        }
        if (player.getInventory().getSelectedSlot() != tool) {
            macro.selectSlot(tool);
        }

        if (Pattern.parse(config.farmPattern) == Pattern.ECHO) {
            return this.echo(macro, mc, player, level);
        }
        if (Float.isNaN(this.baseYaw)) {
            this.baseYaw = startYaw(player.getYRot(), config);
        }
        macro.rotator.look(player, this.baseYaw, (float) config.farmPitch, config.rotationSpeed / 100.0);
        if (this.aligning) {
            this.releaseKeys(mc);
            if (!macro.rotator.settled(player, 1.5)) {
                return "Aligning to the farm";
            }
            this.aligning = false;
            this.lastPos = player.position();
        }

        if (this.atRewarp(config, player)) {
            this.rewarp(macro, mc, "Reached the end of the farm");
            return "Rewarping";
        }

        this.breakCrop(mc, player, level);
        this.move(macro, mc, player, config);
        return "Farming (" + (this.left ? "left" : "right") + " lane)";
    }

    /** Replays the recorded keys and view angles tick by tick, breaking crops on the way, then rewarps and repeats. */
    private String echo(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        List<float[]> frames = macro.recording.frames();
        if (frames.isEmpty()) {
            macro.stop("No recorded movement yet (run /sm echo record)");
            return "Off";
        }
        if (this.frame >= frames.size()) {
            this.frame = 0;
            if (macro.config.farmWarpCommand.isBlank()) {
                this.aligning = true;
            } else {
                this.rewarp(macro, mc, "End of the recording");
                this.frame = 0;
                return "Rewarping";
            }
        }
        float[] f = frames.get(this.frame);
        if (this.aligning) {
            this.releaseKeys(mc);
            macro.rotator.look(player, f[1], f[2], macro.config.rotationSpeed / 100.0);
            if (!macro.rotator.settled(player, 1.5)) {
                return "Aligning to the recording";
            }
            this.aligning = false;
        }
        macro.rotator.look(player, f[1], f[2], 1.0);
        this.keys.hold(Recording.keys(mc, (int) f[0]));
        this.breakCrop(mc, player, level);
        this.frame++;
        return String.format("Replaying (%d%%)", this.frame * 100 / frames.size());
    }

    /** Pest action "kill": vacuums pests near where the farm was left, then rewarps to the farm start. */
    private String huntPests(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level, long now) {
        this.keys.release();
        this.stopBreaking(mc);
        int vacuum = Inv.hotbarNamed(player, List.of("vacuum"));
        if (vacuum < 0) {
            this.endHunt(macro, mc, "No vacuum in the hotbar");
            return "Rewarping";
        }
        if (player.getInventory().getSelectedSlot() != vacuum) {
            macro.selectSlot(vacuum);
        }
        if (this.huntCenter == null) {
            this.huntCenter = player.position();
        }
        Vec3 c = this.huntCenter;
        CommissionData.Mob mob = new CommissionData.Mob(PESTS,
            (x, y, z) -> (x - c.x) * (x - c.x) + (z - c.z) * (z - c.z) <= PEST_SEARCH * PEST_SEARCH);
        LivingEntity before = this.pestCombat.target();
        String status = this.pestCombat.tick(mc, player, level, mob, macro.rotator, macro.walker, macro.config, macro.random, VACUUM_RANGE);
        if (before != null && before != this.pestCombat.target() && !before.isAlive()) {
            this.pests++;
        }
        if (this.pestCombat.target() != null) {
            this.noPestSince = 0L;
        } else if (this.noPestSince == 0L) {
            this.noPestSince = now;
        }
        if (now > this.huntUntil || this.noPestSince > 0L && now - this.noPestSince > PEST_CLEAR_MS) {
            this.endHunt(macro, mc, now > this.huntUntil ? "Gave up on pests" : "Pests cleared");
            return "Rewarping";
        }
        return "Pests: " + status;
    }

    private void endHunt(Macro macro, Minecraft mc, String why) {
        this.huntUntil = 0L;
        this.huntCenter = null;
        this.pestCombat.reset(mc, macro.walker);
        macro.rotator.stop();
        if (!macro.config.farmWarpCommand.isBlank()) {
            this.rewarp(macro, mc, why);
        } else {
            this.resume();
        }
    }

    private void move(Macro macro, Minecraft mc, LocalPlayer player, MinerConfig config) {
        Pattern pattern = Pattern.parse(config.farmPattern);
        String spec = pattern == null || pattern == Pattern.CUSTOM
            ? (this.left ? config.farmCustomLeft : config.farmCustomRight)
            : (this.left ? pattern.left : pattern.right);
        List<KeyMapping> lane = Keys.parse(mc, spec);
        this.keys.hold(lane);

        Vec3 pos = player.position();
        double moved = this.lastPos == null ? 1.0 : Math.hypot(pos.x - this.lastPos.x, pos.z - this.lastPos.z);
        boolean falling = this.lastPos != null && pos.y < this.lastPos.y - 0.05;
        this.lastPos = pos;
        if (moved < 0.02 && !falling) {
            this.stillTicks++;
        } else {
            this.stillTicks = 0;
            this.laneTicks++;
        }

        if (this.stillTicks < config.farmSwitchTicks) {
            return;
        }

        // Switching right after the last switch means this direction is blocked too.
        this.futileSwitches = this.laneTicks < 3 ? this.futileSwitches + 1 : 0;
        this.left = !this.left;
        this.stillTicks = 0;
        this.laneTicks = 0;
        if (this.futileSwitches >= MAX_FUTILE_SWITCHES) {
            this.futileSwitches = 0;
            if (config.farmRewarpWhenStuck && !config.farmWarpCommand.isBlank() && this.rewarps < 50) {
                this.rewarp(macro, mc, "Stuck in both directions");
            } else {
                macro.stop("Farming got stuck (can't move either way)");
            }
        }
    }

    private void breakCrop(Minecraft mc, LocalPlayer player, ClientLevel level) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
            && CROPS.contains(TargetFinder.blockId(level, block.getBlockPos()))) {
            BlockPos pos = block.getBlockPos();
            if (mc.gameMode.continueDestroyBlock(pos, block.getDirection())) {
                level.addBreakingBlockEffect(pos, block.getDirection());
            }
            player.swing(InteractionHand.MAIN_HAND);
            this.breaking = true;
            if (!pos.equals(this.lastBroken)) {
                this.lastBroken = pos.immutable();
                this.crops++;
            }
        } else {
            this.stopBreaking(mc);
        }
    }

    private void stopBreaking(Minecraft mc) {
        if (this.breaking) {
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
            this.breaking = false;
        }
    }

    private boolean atRewarp(MinerConfig config, LocalPlayer player) {
        int[] point = config.farmRewarp;
        if (point == null || config.farmWarpCommand.isBlank()) {
            return false;
        }
        Vec3 pos = player.position();
        return Math.hypot(pos.x - (point[0] + 0.5), pos.z - (point[2] + 0.5)) < 1.0 && Math.abs(pos.y - point[1]) < 1.5;
    }

    private void rewarp(Macro macro, Minecraft mc, String why) {
        this.releaseKeys(mc);
        if (!why.startsWith("Back from") && this.visitors.maybeStart(mc, macro.config)) {
            MinerMod.LOGGER.info("Farming: serving visitors before rewarping ({})", why);
            return;
        }
        this.rewarps++;
        macro.expectTeleport(REWARP_SETTLE_MS + 5000L);
        macro.command(macro.config.farmWarpCommand);
        MinerMod.LOGGER.info("Farming: rewarp ({})", why);
        this.resumeAt = System.currentTimeMillis() + REWARP_SETTLE_MS;
        this.aligning = true;
        this.left = true;
        this.lastPos = null;
        this.stillTicks = 0;
        this.laneTicks = 0;
    }

    static float startYaw(float yaw, MinerConfig config) {
        if (!config.farmKeepYaw) {
            return (float) config.farmYaw;
        }
        return config.farmSnapYaw ? Math.round(yaw / 45.0F) * 45.0F : yaw;
    }

    @Override
    public void onChat(Macro macro, String text) {
        if (text.contains("Pest") && (text.contains("spawned") || text.contains("appeared"))) {
            switch (macro.config.farmPestAction) {
                case "stop" -> {
                    macro.stop("A pest spawned");
                    Macro.alert();
                }
                case "kill" -> {
                    if (this.huntUntil == 0L) {
                        this.huntUntil = System.currentTimeMillis() + PEST_HUNT_MS;
                        this.noPestSince = 0L;
                        MinerMod.message("A pest spawned, hunting it with the vacuum.", ChatFormatting.YELLOW);
                    }
                }
                case "notify" -> {
                    MinerMod.message("A pest spawned in the Garden.", ChatFormatting.YELLOW);
                    Toasts.push("Pest spawned", text, Toasts.Kind.WARNING);
                    macro.notify("Pest spawned", text, 15105570);
                }
                default -> {
                }
            }
        }
    }

    @Override
    public boolean ownsMenu() {
        return this.visitors.active();
    }

    @Override
    public String hudLine(Macro macro) {
        String line = String.format("%,d crops (%,.0f/h)  %d rewarps", this.crops, this.crops / macro.hours(), this.rewarps);
        line = this.pests > 0 ? line + "  " + this.pests + " pests" : line;
        return this.visitors.accepted() > 0 ? line + "  " + this.visitors.accepted() + " visitors" : line;
    }

    @Override
    public String rejoinWarp(MinerConfig config) {
        return config.farmWarpCommand.isBlank() ? null : config.farmWarpCommand;
    }

    @Override
    public void render(Macro macro, LocalPlayer player) {
        int[] point = macro.config.farmRewarp;
        if (point != null && macro.config.showTarget) {
            BlockPos pos = new BlockPos(point[0], point[1], point[2]);
            Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(0xFFFF5555, 2.0F, 0x30FF5555));
            Gizmos.billboardTextOverBlock("Rewarp", pos, 0, 0xFFFF5555, 0.32F);
        }
    }
}
