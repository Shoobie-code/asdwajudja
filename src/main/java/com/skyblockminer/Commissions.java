package com.skyblockminer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class Commissions {
    private static final long SPOT_COOLDOWN = 120000L;
    private Commissions.Phase phase = Commissions.Phase.START;
    private long phaseAt;
    private String current;
    private CommissionData.Commission data;
    private Vec3 spot;
    private final Map<Vec3, Long> badSpots = new HashMap<>();
    private Commissions.Phase afterSelling;
    private int completed;
    private long idleSince;
    private long crowdedSince;
    private long nextActionAt;
    private String lastClaimed;
    private long waitTabUntil;
    private boolean claimWalking;
    private LivingEntity emissary;
    private int talkAttempts;
    private Map<String, Double> progress = Map.of();
    private long progressAt;
    private long noSpotsSince;

    void reset() {
        this.phase(Commissions.Phase.START);
        this.current = null;
        this.data = null;
        this.spot = null;
        this.badSpots.clear();
        this.completed = 0;
        this.lastClaimed = null;
        this.claimWalking = false;
        this.emissary = null;
        this.noSpotsSince = 0L;
    }

    void restart() {
        this.phase(Commissions.Phase.START);
        this.current = null;
        this.data = null;
        this.spot = null;
        this.claimWalking = false;
        this.emissary = null;
        this.noSpotsSince = 0L;
    }

    int completed() {
        return this.completed;
    }

    String current() {
        return this.current;
    }

    double currentProgress() {
        return this.current == null ? -1.0 : this.progress.getOrDefault(this.current, -1.0);
    }

    boolean expectsMenu() {
        return this.phase == Commissions.Phase.CLAIMING || this.phase == Commissions.Phase.CLOSING || this.phase == Commissions.Phase.SELLING;
    }

    void refreshSoon() {
        this.progressAt = 0L;
    }

    void onInventoryFull(Macro macro) {
        if (macro.config.sellTrash && this.phase != Commissions.Phase.SELLING) {
            macro.mining.release(Minecraft.getInstance());
            this.afterSelling = this.phase == Commissions.Phase.WORKING ? Commissions.Phase.WORKING : Commissions.Phase.CHOOSING;
            this.phase(Commissions.Phase.SELLING);
        }
    }

    private void phase(Commissions.Phase next) {
        this.phase = next;
        this.phaseAt = System.currentTimeMillis();
        this.nextActionAt = 0L;
        this.idleSince = 0L;
        this.crowdedSince = 0L;
    }

    String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        long now = System.currentTimeMillis();
        if (now - this.progressAt > 1000L) {
            this.progress = TabList.commissions(mc);
            this.progressAt = now;
        }
        return switch (this.phase) {
            case START, WARPING -> this.warp(macro, mc, now);
            case CHOOSING -> this.choose(macro, mc, player, now);
            case TRAVELING -> this.travel(macro, mc, player, now);
            case WORKING -> this.work(macro, mc, player, level, now);
            case CLAIMING -> this.claim(macro, mc, player, level, now);
            case CLOSING -> {
                if (mc.gui.screen() == null) {
                    this.phase(Commissions.Phase.CHOOSING);
                } else if (now - this.phaseAt > 600L) {
                    player.closeContainer();
                }

                yield "Claimed";
            }
            case SELLING -> this.sell(macro, mc, player, now);
            case LOBBY_SWAP -> {
                if (now - this.phaseAt > 6000L) {
                    this.phase(Commissions.Phase.START);
                }

                yield "Swapping lobby";
            }
        };
    }

    private String warp(Macro macro, Minecraft mc, long now) {
        if ("Dwarven Mines".equals(TabList.area(mc))) {
            this.phase(Commissions.Phase.CHOOSING);
            return "Choosing a commission";
        } else {
            if (this.phase == Commissions.Phase.START || now - this.phaseAt > 12000L) {
                macro.command("warp forge");
                this.phase(Commissions.Phase.WARPING);
            }

            return "Warping to the Dwarven Mines";
        }
    }

    private String choose(Macro macro, Minecraft mc, LocalPlayer player, long now) {
        if (this.progress.isEmpty()) {
            if (now - this.phaseAt > 6000L) {
                macro.stop("No commissions in the tab list. Turn on the Commissions widget in /tablist, or talk to the King first");
            }

            return "Reading commissions";
        } else if (this.lastClaimed != null && now < this.waitTabUntil && this.progress.getOrDefault(this.lastClaimed, 0.0) >= 1.0) {
            return "Waiting for the tab list";
        } else {
            for (Entry<String, Double> entry : this.progress.entrySet()) {
                if (entry.getValue() >= 1.0) {
                    this.current = entry.getKey();
                    this.data = CommissionData.find(this.current);
                    this.startClaim();
                    return "Claiming " + this.current;
                }
            }

            record Option(String name, CommissionData.Commission data, List<Vec3> spots) {
            }

            this.badSpots.values().removeIf(until -> until < now);
            List<Option> options = new ArrayList<>();
            boolean anySupported = false;

            for (String name : this.progress.keySet()) {
                CommissionData.Commission commission = CommissionData.find(name);
                if (commission != null && supported(macro.config, commission)) {
                    anySupported = true;
                    List<Vec3> spots = new ArrayList<>();

                    for (Vec3 candidate : commission.spots()) {
                        if (!this.badSpots.containsKey(candidate) && (macro.config.avoidRadius <= 0 || !macro.playerNear(mc, candidate, macro.config.avoidRadius))) {
                            spots.add(candidate);
                        }
                    }

                    if (!spots.isEmpty()) {
                        options.add(new Option(name, commission, spots));
                    }
                }
            }

            if (!anySupported) {
                macro.stop("None of your commissions are supported (slayer commissions off, or no weapon slot for Goblin Slayer)");
                return "Stopped";
            } else if (options.isEmpty()) {
                if (this.noSpotsSince == 0L) {
                    this.noSpotsSince = now;
                }

                if (now - this.noSpotsSince > 30000L) {
                    this.noSpotsSince = 0L;
                    macro.command("hub");
                    this.phase(Commissions.Phase.LOBBY_SWAP);
                    return "Swapping lobby";
                } else {
                    return "All commission spots are busy, waiting";
                }
            } else {
                this.noSpotsSince = 0L;
                options.sort(Comparator.comparingInt(option -> option.data().cost()));
                Option chosen = options.get(0);
                this.current = chosen.name();
                this.data = chosen.data();
                Vec3 here = player.position();
                this.spot = chosen.spots().stream().min(Comparator.comparingDouble(here::distanceToSqr)).orElseThrow();
                macro.mining.reset(mc);
                macro.combat.reset(mc, macro.walker);
                if (this.data.type() == CommissionData.Type.MINING) {
                    macro.walker.go(mc, List.of(this.spot), 2.5);
                    this.phase(Commissions.Phase.TRAVELING);
                } else {
                    macro.walker.go(mc, List.of(this.spot), 4.0);
                    this.phase(Commissions.Phase.TRAVELING);
                }

                macro.message("Starting " + this.current);
                return "Walking to " + this.current;
            }
        }
    }

    private static boolean supported(MinerConfig config, CommissionData.Commission commission) {
        if (commission.type() == CommissionData.Type.MINING) {
            return true;
        } else {
            return !config.slayerCommissions ? false : !"goblin".equals(commission.mob()) || config.weaponSlot > 0;
        }
    }

    private String travel(Macro macro, Minecraft mc, LocalPlayer player, long now) {
        PathWalker.State state = macro.walker.tick(mc, player, macro.rotator, macro.config);
        if (state == PathWalker.State.DONE) {
            if (!this.equipForWork(macro, player)) {
                return "Stopped";
            } else {
                this.phase(Commissions.Phase.WORKING);
                return this.current;
            }
        } else if (state == PathWalker.State.FAILED) {
            this.badSpots.put(this.spot, now + 120000L);
            macro.message("Couldn't reach the " + this.current + " spot (" + macro.walker.failure() + "), trying another");
            this.phase(Commissions.Phase.CHOOSING);
            return "Path failed";
        } else {
            return (macro.walker.state() == PathWalker.State.SEARCHING ? "Finding a path to " : "Walking to ") + this.current;
        }
    }

    private boolean equipForWork(Macro macro, LocalPlayer player) {
        boolean goblins = "goblin".equals(this.data.mob());
        int slot = goblins ? macro.config.weaponSlot - 1 : Inv.hotbar(player, Inv::isMiningTool);
        if (slot < 0) {
            macro.stop("No pickaxe or drill in your hotbar");
            return false;
        } else {
            macro.selectSlot(slot);
            return true;
        }
    }

    private String work(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level, long now) {
        double value = this.progress.getOrDefault(this.current, -1.0);
        if (value >= 1.0) {
            macro.mining.release(mc);
            macro.combat.reset(mc, macro.walker);
            this.completed++;
            macro.notify("Commission complete", this.current + " (" + this.completed + " this session)", 3066993);
            this.startClaim();
            return "Claiming " + this.current;
        } else if (value < 0.0 && !this.progress.isEmpty()) {
            macro.mining.release(mc);
            this.phase(Commissions.Phase.CHOOSING);
            return "Choosing a commission";
        } else {
            String pct = value >= 0.0 ? String.format(" %.0f%%", value * 100.0) : "";
            if (macro.config.avoidRadius > 0 && macro.playerNear(mc, player.position(), macro.config.avoidRadius)) {
                if (this.crowdedSince == 0L) {
                    this.crowdedSince = now;
                }

                if (now - this.crowdedSince > 20000L) {
                    this.badSpots.put(this.spot, now + 120000L);
                    macro.mining.release(mc);
                    macro.message("Another player is mining here, moving");
                    this.phase(Commissions.Phase.CHOOSING);
                    return "Moving away from another player";
                }
            } else {
                this.crowdedSince = 0L;
            }

            if (this.data.type() == CommissionData.Type.SLAYER) {
                return this.current
                    + pct
                    + ": "
                    + macro.combat.tick(mc, player, level, CommissionData.mob(this.data.mob()), macro.rotator, macro.walker, macro.config, macro.random);
            } else {
                String status = macro.mining.tick(mc, player, level, Targets.mithril(this.data.titanium(this.current)), macro.config, macro.rotator);
                if (macro.mining.target() == null) {
                    if (this.idleSince == 0L) {
                        this.idleSince = now;
                    }

                    if (now - this.idleSince > 8000L) {
                        this.badSpots.put(this.spot, now + 120000L);
                        macro.mining.release(mc);
                        macro.message("Spot is mined out, moving");
                        this.phase(Commissions.Phase.CHOOSING);
                        return "Moving";
                    }
                } else {
                    this.idleSince = 0L;
                }

                return this.current + pct + ": " + status;
            }
        }
    }

    private void startClaim() {
        this.claimWalking = false;
        this.emissary = null;
        this.talkAttempts = 0;
        this.phase(Commissions.Phase.CLAIMING);
    }

    private String claim(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level, long now) {
        String title = Inv.screenTitle(mc);
        if ("Commissions".equals(title)) {
            macro.walker.stop(mc);
            if (now < this.nextActionAt) {
                return "Claiming";
            } else {
                for (int slot = 9; slot < 18; slot++) {
                    if (Inv.lore(Inv.slot(mc, slot)).stream().anyMatch(line -> line.contains("COMPLETED"))) {
                        Inv.click(mc, slot);
                        this.nextActionAt = now + 350L + macro.random.nextInt(300);
                        return "Claiming";
                    }
                }

                this.lastClaimed = this.current;
                this.waitTabUntil = now + 6000L;
                player.closeContainer();
                int tool = Inv.hotbar(player, Inv::isMiningTool);
                if (tool >= 0) {
                    macro.selectSlot(tool);
                }

                this.phase(Commissions.Phase.CLOSING);
                return "Claimed";
            }
        } else if (title != null) {
            if (now - this.phaseAt > 3000L) {
                player.closeContainer();
            }

            return "Waiting for the Commissions menu";
        } else if (now - this.phaseAt > 60000L) {
            macro.message("Claiming took too long, starting over");
            this.phase(Commissions.Phase.START);
            return "Retrying";
        } else {
            int pigeon = Inv.hotbar(player, stack -> Inv.name(stack).contains("Royal Pigeon"));
            if (pigeon >= 0) {
                if (player.getInventory().getSelectedSlot() != pigeon) {
                    macro.selectSlot(pigeon);
                    this.nextActionAt = now + 250L + macro.random.nextInt(200);
                    return "Taking out the Royal Pigeon";
                } else {
                    if (now >= this.nextActionAt) {
                        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                        this.nextActionAt = now + 2500L;
                    }

                    return "Opening Commissions";
                }
            } else {
                if (!this.claimWalking) {
                    macro.walker.go(mc, CommissionData.EMISSARIES, 2.5);
                    this.claimWalking = true;
                }

                PathWalker.State state = macro.walker.tick(mc, player, macro.rotator, macro.config);
                if (state == PathWalker.State.FAILED) {
                    macro.stop("Couldn't reach an emissary to claim (" + macro.walker.failure() + ")");
                    return "Stopped";
                } else if (state != PathWalker.State.DONE) {
                    return "Walking to an emissary";
                } else {
                    if (this.emissary == null || !this.emissary.isAlive()) {
                        this.emissary = emissaryNpc(player, level);
                    }

                    if (this.emissary == null) {
                        macro.stop("No emissary NPC found here");
                        return "Stopped";
                    } else {
                        macro.rotator.track(player, this.emissary.getBoundingBox().getCenter().add(0.0, 0.4, 0.0), macro.config.rotationSpeed / 100.0);
                        HitResult hit = mc.hitResult;
                        if (now >= this.nextActionAt
                            && hit instanceof EntityHitResult entityHit
                            && hit.getType() == Type.ENTITY
                            && entityHit.getEntity() == this.emissary) {
                            if (this.talkAttempts++ % 2 == 0) {
                                mc.gameMode.attack(player, this.emissary);
                            } else {
                                mc.gameMode.interact(player, this.emissary, entityHit, InteractionHand.MAIN_HAND);
                            }

                            player.swing(InteractionHand.MAIN_HAND);
                            this.nextActionAt = now + 2000L;
                        }

                        return "Talking to the emissary";
                    }
                }
            }
        }
    }

    private static LivingEntity emissaryNpc(LocalPlayer player, ClientLevel level) {
        Vec3 here = player.position();
        Vec3 spot = CommissionData.EMISSARIES.stream().min(Comparator.comparingDouble(here::distanceToSqr)).orElseThrow();
        LivingEntity best = null;
        double bestDistance = 16.0;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand) && entity != player && entity.getUUID().version() != 4) {
                double distance = entity.position().distanceToSqr(spot);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = living;
                }
            }
        }

        return best;
    }

    private String sell(Macro macro, Minecraft mc, LocalPlayer player, long now) {
        String title = Inv.screenTitle(mc);
        if (title == null) {
            if (now >= this.nextActionAt) {
                macro.command("trades");
                this.nextActionAt = now + 3000L;
            }

            if (now - this.phaseAt > 15000L) {
                macro.stop("Inventory full and the Trades menu would not open");
                return "Stopped";
            } else {
                return "Opening Trades to sell";
            }
        } else if (!title.contains("Trades")) {
            player.closeContainer();
            return "Opening Trades to sell";
        } else if (now < this.nextActionAt) {
            return "Selling";
        } else {
            int size = player.containerMenu.slots.size();

            for (int slot = Math.max(0, size - 36); slot < size; slot++) {
                ItemStack stack = Inv.slot(mc, slot);
                String name = Inv.name(stack);
                if (!stack.isEmpty() && !Inv.isMiningTool(stack) && !CommissionData.TRASH.stream().noneMatch(name::contains)) {
                    Inv.click(mc, slot);
                    this.nextActionAt = now + 250L + macro.random.nextInt(200);
                    return "Selling " + name;
                }
            }

            player.closeContainer();
            this.phase(this.afterSelling == null ? Commissions.Phase.CHOOSING : this.afterSelling);
            return "Sold";
        }
    }

    static enum Phase {
        START,
        WARPING,
        CHOOSING,
        TRAVELING,
        WORKING,
        CLAIMING,
        CLOSING,
        SELLING,
        LOBBY_SWAP;
    }
}
