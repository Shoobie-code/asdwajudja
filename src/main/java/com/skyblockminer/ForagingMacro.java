package com.skyblockminer;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tree farm loop for a fixed standing spot: plants saplings on the free dirt in reach, grows them with bone
 * meal and chops the logs (a Treecapitator takes the whole tree), then starts over.
 */
final class ForagingMacro implements Routine {
    static final List<String> AXES = List.of("treecapitator", "axe");
    private static final List<String> SAPLINGS = List.of("sapling", "propagule");
    private static final List<String> BONE_MEAL = List.of("bone meal", "bonemeal");
    private static final int SCAN_EVERY = 4;
    private static final int MAX_AIM_TICKS = 30;
    private static final int MAX_BREAK_TICKS = 120;

    private enum Job {
        CHOP("Chopping"),
        PLANT("Planting"),
        GROW("Using bone meal");

        final String label;

        Job(String label) {
            this.label = label;
        }
    }

    private final TargetFinder finder = new TargetFinder();
    private BlockPos target;
    private Job job;
    private Vec3 aim;
    private int aimTicks;
    private int breakTicks;
    private int scanIn;
    private long waitUntil;
    private boolean breaking;
    private int logs;
    private int planted;
    private int trees;
    private boolean sawLogs;

    @Override
    public void reset() {
        this.target = null;
        this.job = null;
        this.scanIn = 0;
        this.waitUntil = 0L;
        this.logs = 0;
        this.planted = 0;
        this.trees = 0;
        this.sawLogs = false;
    }

    @Override
    public void releaseKeys(Minecraft mc) {
        if (this.breaking && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
        this.breaking = false;
    }

    @Override
    public boolean breaking() {
        return this.breaking;
    }

    @Override
    public void resume() {
        this.target = null;
        this.waitUntil = 0L;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        MinerConfig config = macro.config;
        long now = System.currentTimeMillis();
        if (now < this.waitUntil) {
            return this.job == null ? "Waiting" : this.job.label;
        }

        if (this.target != null && !this.stillValid(level, config)) {
            if (this.job == Job.CHOP) {
                this.logs++;
            }
            this.releaseKeys(mc);
            this.target = null;
        }
        if (this.target == null && --this.scanIn <= 0) {
            this.scanIn = SCAN_EVERY;
            this.pick(player, level, config, macro.mining.reach(player, config));
        }
        if (this.target == null) {
            macro.rotator.stop();
            macro.rotator.sync(player);
            return "Waiting for a tree to grow";
        }

        int slot = switch (this.job) {
            case CHOP -> Inv.hotbarNamed(player, AXES);
            case PLANT -> Inv.hotbarNamed(player, SAPLINGS);
            case GROW -> Inv.hotbarNamed(player, BONE_MEAL);
        };
        if (slot < 0) {
            macro.stop(switch (this.job) {
                case CHOP -> "No axe in the hotbar";
                case PLANT -> "Out of saplings";
                case GROW -> "Out of bone meal";
            });
            return "Stopped";
        }
        if (player.getInventory().getSelectedSlot() != slot) {
            macro.selectSlot(slot);
            this.waitUntil = now + 60L;
            return this.job.label;
        }

        macro.rotator.track(player, this.aim, config.rotationSpeed / 100.0);
        HitResult hit = mc.hitResult;
        boolean onTarget = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && block.getBlockPos().equals(this.target)
            && (this.job != Job.PLANT || block.getDirection() == Direction.UP);
        if (!onTarget) {
            this.releaseKeys(mc);
            if (++this.aimTicks > MAX_AIM_TICKS) {
                this.target = null;
            }
            return this.job.label;
        }

        BlockHitResult block = (BlockHitResult) hit;
        if (this.job == Job.CHOP) {
            if (mc.gameMode.continueDestroyBlock(block.getBlockPos(), block.getDirection())) {
                level.addBreakingBlockEffect(block.getBlockPos(), block.getDirection());
            }
            player.swing(InteractionHand.MAIN_HAND);
            this.breaking = true;
            if (++this.breakTicks > MAX_BREAK_TICKS) {
                this.releaseKeys(mc);
                this.target = null;
            }
        } else {
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, block);
            player.swing(InteractionHand.MAIN_HAND);
            if (this.job == Job.PLANT) {
                this.planted++;
            }
            this.target = null;
            this.waitUntil = now + Math.max(50, config.forageActionDelay);
        }
        return this.job.label;
    }

    /** Chooses the next job: logs first, then empty dirt, then saplings to grow. */
    private void pick(LocalPlayer player, ClientLevel level, MinerConfig config, double reach) {
        Vec3 eye = player.getEyePosition();
        BlockPos center = player.blockPosition();
        int r = (int) Math.ceil(reach);
        double reachSq = reach * reach;
        BlockPos bestLog = null;
        BlockPos bestDirt = null;
        BlockPos bestSapling = null;
        double logDist = Double.MAX_VALUE;
        double dirtDist = Double.MAX_VALUE;
        double saplingDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, r + 2, r))) {
            double dist = Vec3.atCenterOf(pos).distanceToSqr(eye);
            if (dist > reachSq) {
                continue;
            }
            String id = TargetFinder.blockId(level, pos);
            if (isLog(id)) {
                if (dist < logDist) {
                    logDist = dist;
                    bestLog = pos.immutable();
                }
            } else if (isSapling(id)) {
                if (dist < saplingDist) {
                    saplingDist = dist;
                    bestSapling = pos.immutable();
                }
            } else if (plantable(id, config) && level.getBlockState(pos.above()).isAir() && dist < dirtDist) {
                dirtDist = dist;
                bestDirt = pos.immutable();
            }
        }

        if (bestLog != null) {
            this.sawLogs = true;
            this.begin(Job.CHOP, bestLog, this.finder.aimPoint(player, level, bestLog, reach));
        } else {
            if (this.sawLogs) {
                this.sawLogs = false;
                this.trees++;
            }
            if (bestDirt != null) {
                this.begin(Job.PLANT, bestDirt, new Vec3(bestDirt.getX() + 0.5, bestDirt.getY() + 0.98, bestDirt.getZ() + 0.5));
            } else if (bestSapling != null && config.forageBonemeal && Inv.hotbarNamed(player, BONE_MEAL) >= 0) {
                this.begin(Job.GROW, bestSapling, Vec3.atCenterOf(bestSapling).add(0.0, -0.1, 0.0));
            }
        }
    }

    private void begin(Job job, BlockPos pos, Vec3 aim) {
        if (aim == null) {
            return;
        }
        this.job = job;
        this.target = pos;
        this.aim = aim;
        this.aimTicks = 0;
        this.breakTicks = 0;
    }

    private boolean stillValid(ClientLevel level, MinerConfig config) {
        String id = TargetFinder.blockId(level, this.target);
        return switch (this.job) {
            case CHOP -> isLog(id);
            case PLANT -> plantable(id, config) && level.getBlockState(this.target.above()).isAir();
            case GROW -> isSapling(id);
        };
    }

    private static boolean isLog(String id) {
        return id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem");
    }

    private static boolean isSapling(String id) {
        return id.endsWith("_sapling") || id.endsWith("_propagule");
    }

    private static boolean plantable(String id, MinerConfig config) {
        return switch (id) {
            case "minecraft:dirt", "minecraft:podzol", "minecraft:rooted_dirt", "minecraft:coarse_dirt", "minecraft:mud" -> true;
            case "minecraft:grass_block" -> config.forageGrass;
            default -> false;
        };
    }

    @Override
    public String hudLine(Macro macro) {
        double hours = macro.hours();
        return String.format("%d trees (%.0f/h), %d logs, %d planted", this.trees, this.trees / hours, this.logs, this.planted);
    }

    @Override
    public String rejoinWarp(MinerConfig config) {
        return config.forageWarpCommand.isBlank() ? null : config.forageWarpCommand;
    }

    @Override
    public Vec3 home(MinerConfig config) {
        int[] spot = config.forageSpot;
        return spot == null ? null : new Vec3(spot[0] + 0.5, spot[1], spot[2] + 0.5);
    }

    @Override
    public void render(Macro macro, LocalPlayer player) {
        if (macro.config.showTarget && this.target != null) {
            int color = this.job == Job.CHOP ? 0xFFFFAA00 : 0xFF55FF55;
            Gizmos.cuboid(this.target, GizmoStyle.strokeAndFill(color, 2.0F, color & 0x30FFFFFF));
        }
    }
}
