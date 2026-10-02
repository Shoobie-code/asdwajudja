package com.skyblockminer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class MiningEngine {
    private static final int MAX_MINE_TICKS = 200;
    private static final int MAX_AIM_TICKS = 40;
    private static final long EXCLUDE_MS = 20000L;
    private static final long RECENT_MS = 10000L;
    private final TargetFinder finder = new TargetFinder();
    private final Map<BlockPos, Long> excluded = new HashMap<>();
    private final Map<BlockPos, Long> recentlyMined = new HashMap<>();
    private final Random random = new Random();
    private TargetFinder.Target target;
    private int mineTicks;
    private int aimTicks;
    private int driftIn;
    private boolean destroying;
    private boolean abilityQueued;
    private boolean abilityRelease;
    private boolean sneakHeld;
    private int broken;

    void configure(MinerConfig config) {
        this.finder.configure(config.randomize, config.aimSpread / 100.0);
    }

    boolean breaking() {
        return this.destroying;
    }

    TargetFinder.Target target() {
        return this.target;
    }

    int broken() {
        return this.broken;
    }

    void resetCount() {
        this.broken = 0;
    }

    boolean recentlyMined(BlockPos pos) {
        Long at = this.recentlyMined.get(pos);
        return at != null && System.currentTimeMillis() - at < 10000L;
    }

    void onAbilityReady() {
        this.abilityQueued = true;
    }

    void release(Minecraft mc) {
        this.stopDestroying(mc);
        this.holdSneak(mc, false);
    }

    void reset(Minecraft mc) {
        this.release(mc);
        this.target = null;
        this.excluded.clear();
        this.abilityQueued = false;
        this.abilityRelease = false;
    }

    double reach(LocalPlayer player, MinerConfig config) {
        return Math.min(config.reach, player.blockInteractionRange());
    }

    String tick(Minecraft mc, LocalPlayer player, ClientLevel level, Map<String, Integer> costs, MinerConfig config, Rotator rotator) {
        return this.tick(mc, player, level, costs, config, rotator, null);
    }

    String tick(Minecraft mc, LocalPlayer player, ClientLevel level, Map<String, Integer> costs, MinerConfig config, Rotator rotator, Predicate<BlockPos> allow) {
        this.holdSneak(mc, config.sneak);
        if (this.abilityQueued && config.useAbility) {
            String ability = this.useAbility(mc, player);
            if (ability != null) {
                return ability;
            }
        }

        long now = System.currentTimeMillis();
        this.excluded.values().removeIf(until -> until < now);
        this.recentlyMined.values().removeIf(at -> now - at > 10000L);
        if (this.target != null && !costs.containsKey(TargetFinder.blockId(level, this.target.pos()))) {
            this.broken++;
            this.recentlyMined.put(this.target.pos(), now);
            this.target = null;
        }

        double reach = this.reach(player, config);
        if (this.target == null) {
            this.target = this.finder.find(player, level, costs, reach, this.excluded.keySet(), allow);
            this.mineTicks = 0;
            this.aimTicks = 0;
            this.driftIn = 12 + this.random.nextInt(25);
            this.stopDestroying(mc);
        }

        if (this.target == null) {
            rotator.stop();
            rotator.sync(player);
            return costs.isEmpty() ? "No blocks set" : "No blocks in reach";
        } else {
            rotator.track(player, this.target.aim(), config.rotationSpeed / 100.0);
            HitResult hit = mc.hitResult;
            boolean onTarget = hit instanceof BlockHitResult block && hit.getType() == Type.BLOCK && block.getBlockPos().equals(this.target.pos());
            if (onTarget) {
                BlockHitResult blockx = (BlockHitResult)hit;
                if (mc.gameMode.continueDestroyBlock(blockx.getBlockPos(), blockx.getDirection())) {
                    level.addBreakingBlockEffect(blockx.getBlockPos(), blockx.getDirection());
                }

                player.swing(InteractionHand.MAIN_HAND);
                this.destroying = true;
                if (config.randomize && --this.driftIn <= 0) {
                    Vec3 aim = this.finder.aimPoint(player, level, this.target.pos(), reach);
                    if (aim != null) {
                        this.target = new TargetFinder.Target(this.target.pos(), aim, this.target.block(), this.target.cost());
                    }

                    this.driftIn = 15 + this.random.nextInt(30);
                }

                if (++this.mineTicks > 200) {
                    this.skip("took too long to break");
                }

                return "Mining " + pretty(this.target == null ? "" : this.target.block());
            } else {
                this.stopDestroying(mc);
                if (++this.aimTicks > 40) {
                    Vec3 aim = this.finder.aimPoint(player, level, this.target.pos(), reach);
                    if (aim == null || !(aim.distanceToSqr(this.target.aim()) > 0.01)) {
                        this.skip("could not be reached");
                        return "Looking for blocks";
                    }

                    this.target = new TargetFinder.Target(this.target.pos(), aim, this.target.block(), this.target.cost());
                    this.aimTicks = 0;
                }

                return "Aiming at " + pretty(this.target.block());
            }
        }
    }

    private void skip(String why) {
        if (this.target != null) {
            this.excluded.put(this.target.pos(), System.currentTimeMillis() + 20000L);
            MinerMod.LOGGER.info("Skipping {} at {}: {}", new Object[]{this.target.block(), this.target.pos(), why});
            this.target = null;
        }
    }

    private String useAbility(Minecraft mc, LocalPlayer player) {
        if (!Inv.isMiningTool(player.getMainHandItem())) {
            this.abilityQueued = false;
            return null;
        } else if (!this.abilityRelease) {
            this.stopDestroying(mc);
            this.abilityRelease = true;
            return "Using pickaxe ability";
        } else {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            this.abilityRelease = false;
            this.abilityQueued = false;
            return "Using pickaxe ability";
        }
    }

    private void holdSneak(Minecraft mc, boolean down) {
        if (down) {
            mc.options.keyShift.setDown(true);
            this.sneakHeld = true;
        } else if (this.sneakHeld) {
            mc.options.keyShift.setDown(false);
            this.sneakHeld = false;
        }
    }

    private void stopDestroying(Minecraft mc) {
        if (this.destroying) {
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }

            this.destroying = false;
        }
    }

    static String pretty(String id) {
        String name = id.substring(id.indexOf(58) + 1).replace('_', ' ');

        return switch (name) {
            case "polished diorite" -> "Titanium";
            case "light blue wool", "prismarine", "prismarine bricks", "dark prismarine", "gray wool", "cyan terracotta" -> "Mithril";
            default -> name;
        };
    }
}
