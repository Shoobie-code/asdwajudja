package com.skyblockminer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Garden visitors, run by the farming macro at a rewarp: walks to the saved visitor spot, talks to each visitor NPC
 * there and presses the accept item. A visitor whose offer can't be accepted (missing items) is skipped. Untested in
 * game; the accept item's name is a setting.
 */
final class Visitors {
    private static final double NPC_RADIUS = 12.0;
    private static final long LIMIT_MS = 90000L;

    private final Set<UUID> served = new HashSet<>();
    private boolean active;
    private boolean walking;
    private long startedAt;
    private long nextActionAt;
    private long menuSince;
    private boolean clicked;
    private LivingEntity visitor;
    private int accepted;

    boolean active() {
        return this.active;
    }

    int accepted() {
        return this.accepted;
    }

    void reset() {
        this.active = false;
        this.served.clear();
        this.accepted = 0;
    }

    /** Starts a visitor round when enough visitors are waiting; returns true if it started. */
    boolean maybeStart(Minecraft mc, MinerConfig config) {
        if (!config.visitorsEnabled || config.visitorSpot == null) {
            return false;
        }
        int waiting = TabList.visitors(TabList.lines(mc));
        if (waiting < Math.max(1, config.visitorMin)) {
            return false;
        }
        this.active = true;
        this.walking = false;
        this.visitor = null;
        this.served.clear();
        this.startedAt = System.currentTimeMillis();
        this.nextActionAt = 0L;
        this.menuSince = 0L;
        return true;
    }

    /** One tick of the visitor round; clears {@link #active()} when finished. */
    String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        MinerConfig config = macro.config;
        long now = System.currentTimeMillis();
        if (now - this.startedAt > LIMIT_MS) {
            return this.finish(mc, player, "Visitors took too long");
        }
        String title = Inv.screenTitle(mc);
        if (title != null) {
            return this.inMenu(macro, mc, player, now);
        }
        this.menuSince = 0L;

        Vec3 spot = new Vec3(config.visitorSpot[0], config.visitorSpot[1], config.visitorSpot[2]);
        if (player.position().distanceToSqr(spot) > 9.0) {
            if (!this.walking || !macro.walker.busy()) {
                macro.walker.go(mc, List.of(spot), 1.5);
                this.walking = true;
            }
            PathWalker.State state = macro.walker.tick(mc, player, macro.rotator, config);
            if (state == PathWalker.State.FAILED) {
                return this.finish(mc, player, "Could not walk to the visitors: " + macro.walker.failure());
            }
            return "Walking to the visitors";
        }
        if (this.walking) {
            macro.walker.stop(mc);
            this.walking = false;
        }

        if (this.visitor == null || !this.visitor.isAlive()) {
            this.visitor = this.nextVisitor(player, level, spot);
            if (this.visitor == null) {
                return this.finish(mc, player, "Visitors done (" + this.accepted + " accepted)");
            }
        }
        macro.rotator.track(player, this.visitor.getBoundingBox().getCenter().add(0.0, 0.4, 0.0), config.rotationSpeed / 100.0);
        HitResult hit = mc.hitResult;
        if (now >= this.nextActionAt && hit instanceof EntityHitResult entityHit && hit.getType() == HitResult.Type.ENTITY
            && entityHit.getEntity() == this.visitor) {
            mc.gameMode.interact(player, this.visitor, entityHit, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
            this.nextActionAt = now + 2000L;
        } else if (now >= this.nextActionAt + 4000L) {
            // Could not get the visitor under the crosshair; skip them.
            this.served.add(this.visitor.getUUID());
            this.visitor = null;
        }
        return "Talking to a visitor";
    }

    private String inMenu(Macro macro, Minecraft mc, LocalPlayer player, long now) {
        if (this.menuSince == 0L) {
            this.menuSince = now;
            this.clicked = false;
            this.nextActionAt = now + 500L;
        }
        if (now < this.nextActionAt) {
            return "Reading the offer";
        }
        int accept = Inv.menuSlotNamed(mc, macro.config.visitorAccept);
        if (accept >= 0 && !this.clicked) {
            this.clicked = true;
            Inv.click(mc, accept);
            this.accepted++;
            this.nextActionAt = now + 1500L;
            this.markServed();
            return "Accepting the offer";
        }
        // No accept item, or the menu stayed open after accepting (missing items): close and move on.
        if (accept >= 0 && this.clicked) {
            this.accepted--;
        }
        this.markServed();
        player.closeContainer();
        this.nextActionAt = now + 800L;
        return "Next visitor";
    }

    private void markServed() {
        if (this.visitor != null) {
            this.served.add(this.visitor.getUUID());
            this.visitor = null;
        }
    }

    /** Nearest NPC (a player entity that isn't a real player) near the spot that has a name tag and wasn't served. */
    private LivingEntity nextVisitor(LocalPlayer player, ClientLevel level, Vec3 spot) {
        LivingEntity best = null;
        double bestDistance = NPC_RADIUS * NPC_RADIUS;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Player npc && entity != player && entity.getUUID().version() != 4 && !this.served.contains(entity.getUUID())
                && named(level, npc)) {
                double distance = entity.position().distanceToSqr(spot);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = npc;
                }
            }
        }
        return best;
    }

    private static boolean named(ClientLevel level, Player npc) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand && stand.hasCustomName()) {
                double dx = stand.getX() - npc.getX();
                double dz = stand.getZ() - npc.getZ();
                double above = stand.getY() - npc.getY();
                if (dx * dx + dz * dz < 1.0 && above >= 0.0 && above < 3.0) {
                    return true;
                }
            }
        }
        return false;
    }

    private String finish(Minecraft mc, LocalPlayer player, String why) {
        if (mc.gui.screen() != null) {
            player.closeContainer();
        }
        this.active = false;
        this.visitor = null;
        MinerMod.LOGGER.info("Visitors: {}", why);
        return why;
    }
}
