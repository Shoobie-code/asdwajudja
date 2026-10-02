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
        CUSTOM("custom", "Custom keys", null, null, 3.0);

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

        int tool = Inv.toolSlot(player, config.farmToolSlot, TOOLS);
        if (tool < 0) {
            macro.stop("No farming tool in the hotbar");
            return "No tool";
        }
        if (player.getInventory().getSelectedSlot() != tool) {
            macro.selectSlot(tool);
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
    public String hudLine(Macro macro) {
        return String.format("%,d crops (%,.0f/h)  %d rewarps", this.crops, this.crops / macro.hours(), this.rewarps);
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
