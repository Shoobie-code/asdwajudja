package com.skyblockminer;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Kills mobs whose SkyBlock name tag contains one of the configured names, within a radius of a saved spot
 * (or of where the macro was started). Covers grinding spots such as ghosts, zealots, graveyard zombies,
 * crypt ghouls or goblins. Walks back to the spot when nothing is left to fight.
 */
final class CombatMacro implements Routine {
    private final Combat combat = new Combat();
    private Vec3 center;
    private LivingEntity lastTarget;
    private int kills;
    private boolean returning;

    @Override
    public void reset() {
        this.combat.clear();
        this.center = null;
        this.lastTarget = null;
        this.kills = 0;
        this.returning = false;
    }

    @Override
    public void resume() {
        this.lastTarget = null;
        this.returning = false;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        MinerConfig config = macro.config;
        if (config.combatMobs.isEmpty()) {
            macro.stop("Add mob names on the Combat page first");
            return "Off";
        }
        if (this.center == null) {
            Vec3 home = this.home(config);
            this.center = home != null ? home : player.position();
        }

        int weapon = Inv.toolSlot(player, config.combatWeaponSlot, FishingMacro.WEAPONS);
        if (weapon < 0) {
            macro.stop("No weapon in the hotbar (set a weapon slot on the Combat page)");
            return "Off";
        }
        if (player.getInventory().getSelectedSlot() != weapon) {
            macro.selectSlot(weapon);
        }

        Vec3 c = this.center;
        double radiusSq = (double) config.combatRadius * config.combatRadius;
        CommissionData.Mob mob = new CommissionData.Mob(config.combatMobs,
            (x, y, z) -> (x - c.x) * (x - c.x) + (z - c.z) * (z - c.z) <= radiusSq && Math.abs(y - c.y) <= 12.0);

        String status = this.combat.tick(mc, player, level, mob, macro.rotator, macro.walker, config, macro.random, config.combatAttackMode.equals("use"));
        LivingEntity target = this.combat.target();
        if (this.lastTarget != null && target != this.lastTarget && !this.lastTarget.isAlive()) {
            this.kills++;
        }
        this.lastTarget = target;

        if (target == null) {
            return this.wander(macro, mc, player, config, status);
        }
        this.returning = false;
        return status;
    }

    /** Nothing to fight: walk back toward the center so new spawns come into view. */
    private String wander(Macro macro, Minecraft mc, LocalPlayer player, MinerConfig config, String status) {
        if (player.position().distanceToSqr(this.center) <= 9.0) {
            if (this.returning) {
                macro.walker.stop(mc);
                this.returning = false;
            }
            return status;
        }
        if (!this.returning || !macro.walker.busy()) {
            macro.walker.go(mc, List.of(this.center), 1.5);
            this.returning = true;
        }
        PathWalker.State state = macro.walker.tick(mc, player, macro.rotator, config);
        if (state == PathWalker.State.FAILED) {
            macro.walker.stop(mc);
            this.returning = false;
            this.center = player.position();
            return "Could not walk back; using this spot";
        }
        return "Walking back to the spot";
    }

    @Override
    public String hudLine(Macro macro) {
        return String.format("%d kills (%.0f/h)", this.kills, this.kills / macro.hours());
    }

    @Override
    public String rejoinWarp(MinerConfig config) {
        return config.combatWarpCommand.isBlank() ? null : config.combatWarpCommand;
    }

    @Override
    public Vec3 home(MinerConfig config) {
        double[] spot = config.combatSpot;
        return spot == null ? null : new Vec3(spot[0], spot[1], spot[2]);
    }
}
