package com.skyblockminer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * Mines the closest matching blocks in reach (mithril, gemstone, ore, tunnels, custom). When nothing is in
 * reach it looks up the nearest known block of the wanted types in the {@link OreIndex} and walks there.
 */
final class BlockMining implements Routine {
    private static final int IDLE_TICKS_BEFORE_WALK = 20;
    private static final long UNREACHABLE_MS = 60000L;
    private static final double GOAL_RADIUS = 2.5;

    private final MacroType type;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private Map<String, Integer> costs;
    private int costsRevision = -1;
    private BlockPos vein;
    private int idleTicks;
    private int walks;

    BlockMining(MacroType type) {
        this.type = type;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (this.costs == null || this.costsRevision != macro.config.revision()) {
            this.costs = Targets.costs(this.type.blocks, macro.config);
            this.costsRevision = macro.config.revision();
        }
        if (this.vein != null) {
            return this.walkToVein(macro, mc, player, level);
        }

        String status = macro.mining.tick(mc, player, level, this.costs, macro.config, macro.rotator);
        if (!macro.config.oreWalk || macro.mining.target() != null) {
            this.idleTicks = 0;
            return status;
        }
        if (++this.idleTicks < IDLE_TICKS_BEFORE_WALK) {
            return status;
        }
        this.idleTicks = 0;
        return this.findVein(macro, mc, player);
    }

    private String findVein(Macro macro, Minecraft mc, LocalPlayer player) {
        long now = System.currentTimeMillis();
        this.unreachable.values().removeIf(at -> now - at > UNREACHABLE_MS);
        Vec3 eye = player.getEyePosition();
        double reach = macro.mining.reach(player, macro.config);
        double reachSq = reach * reach;
        // Blocks in reach were already rejected by the target finder (hidden or excluded), so look past them.
        BlockPos found = macro.map.ores().nearest(eye, this.costs, macro.config.oreWalkRange,
            pos -> this.unreachable.containsKey(pos) || Vec3.atCenterOf(pos).distanceToSqr(eye) <= reachSq);
        if (found == null) {
            return macro.map.ores().size() == 0 ? "No blocks in reach (map still scanning)" : "No known ore within " + macro.config.oreWalkRange + " blocks";
        }
        this.vein = found;
        this.walks++;
        macro.mining.release(mc);
        macro.walker.go(mc, List.of(Vec3.atCenterOf(found)), GOAL_RADIUS);
        return "Heading to the nearest vein";
    }

    private String walkToVein(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        double reach = macro.mining.reach(player, macro.config) - 0.5;
        boolean gone = !this.costs.containsKey(TargetFinder.blockId(level, this.vein));
        boolean inReach = player.onGround() && Vec3.atCenterOf(this.vein).distanceToSqr(player.getEyePosition()) <= reach * reach;
        if (gone || inReach) {
            macro.walker.stop(mc);
            this.vein = null;
            return "Mining";
        }
        PathWalker.State state = macro.walker.tick(mc, player, macro.rotator, macro.config);
        switch (state) {
            case DONE -> {
                macro.walker.stop(mc);
                this.vein = null;
            }
            case FAILED -> {
                this.unreachable.put(this.vein, System.currentTimeMillis());
                this.vein = null;
            }
            default -> {
            }
        }
        String name = this.vein == null ? "vein" : MiningEngine.pretty(TargetFinder.blockId(level, this.vein));
        return state == PathWalker.State.SEARCHING ? "Finding a path to " + name : "Walking to " + name + " (" + macro.walker.remaining() + " steps)";
    }

    @Override
    public void reset() {
        this.costs = null;
        this.vein = null;
        this.idleTicks = 0;
        this.walks = 0;
        this.unreachable.clear();
    }

    @Override
    public void resume() {
        this.vein = null;
        this.idleTicks = 0;
    }

    @Override
    public String hudLine(Macro macro) {
        return String.format("%,d known ores, %d vein walks", macro.map.ores().size(), this.walks);
    }

    @Override
    public void render(Macro macro, LocalPlayer player) {
        if (this.vein != null && macro.config.showTarget) {
            Gizmos.cuboid(this.vein, GizmoStyle.strokeAndFill(0xFFFFAA00, 2.0F, 0x30FFAA00));
        }
    }
}
