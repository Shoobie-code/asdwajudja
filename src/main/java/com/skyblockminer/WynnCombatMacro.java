package com.skyblockminer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Wynncraft mob grinder. Mob names float above mobs as text displays; the macro picks the nearest mob whose label
 * matches the configured names (any mob when the list is empty) within a radius of a saved spot, walks into range,
 * then alternates basic attacks with a spell rotation cast as three-click combos. It can also cast a healing spell
 * when health drops, and holds spells back while mana is low. Health and mana come from the action bar.
 */
final class WynnCombatMacro implements Routine {
    private static final double MELEE = 3.0;
    private static final double RANGED = 12.0;
    private static final int RETARGET_TICKS = 10;
    private static final int HEAL_COOLDOWN_TICKS = 60;
    private static final Pattern FORMATTING = Pattern.compile("§.");

    private record Label(Vec3 pos, String name) {
    }

    private final Deque<WynnSpells.Click> pending = new ArrayDeque<>();
    private Vec3 center;
    private LivingEntity target;
    private String targetName = "";
    private LivingEntity lastTarget;
    private int retargetIn;
    private int repathIn;
    private int clickIn;
    private int attackIn;
    private int spellIn;
    private int healIn;
    private int rotationIndex;
    private int casting;
    private int kills;
    private int casts;
    private boolean returning;

    @Override
    public void reset() {
        this.pending.clear();
        this.center = null;
        this.target = null;
        this.targetName = "";
        this.lastTarget = null;
        this.retargetIn = 0;
        this.clickIn = 0;
        this.attackIn = 0;
        this.spellIn = 0;
        this.healIn = 0;
        this.rotationIndex = 0;
        this.casting = 0;
        this.kills = 0;
        this.casts = 0;
        this.returning = false;
    }

    @Override
    public void resume() {
        this.pending.clear();
        this.target = null;
        this.lastTarget = null;
        this.returning = false;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        MinerConfig config = macro.config;
        WynnSpells.WynnClass type = WynnSpells.WynnClass.parse(config.wynnClass);
        if (type == null) {
            macro.stop("Pick your class on the Wynncraft page first");
            return "Off";
        }
        if (this.center == null) {
            Vec3 home = this.home(config);
            this.center = home != null ? home : player.position();
        }
        int slot = config.wynnWeaponSlot - 1;
        if (slot >= 0 && player.getInventory().getSelectedSlot() != slot) {
            macro.selectSlot(slot);
        }
        long now = System.currentTimeMillis();

        // A spell combo in progress always finishes first, or the server reads the clicks as a different spell.
        if (!this.pending.isEmpty()) {
            if (this.target != null && this.target.isAlive()) {
                macro.rotator.follow(player, aimPoint(this.target), Math.min(1.0, config.rotationSpeed / 100.0 * 1.3));
            }
            if (--this.clickIn <= 0) {
                this.click(mc, player, this.pending.poll());
                this.clickIn = Math.max(1, config.wynnClickTicks);
                if (this.pending.isEmpty()) {
                    // Movement spells (Charge, Dash, Teleport, Escape, Haul) move the player; don't trip the failsafes.
                    macro.expectTeleport(2500L);
                }
            }
            return "Casting spell " + this.casting;
        }

        if (this.healIn > 0) {
            this.healIn--;
        }
        int health = macro.wynn.healthPercent(now);
        int mana = macro.wynn.manaPercent(now);
        if (config.wynnHealSpell > 0 && health >= 0 && health < config.wynnHealBelow && this.healIn <= 0 && this.manaFor(config, mana)) {
            this.healIn = HEAL_COOLDOWN_TICKS;
            return this.cast(type, config.wynnHealSpell, "Healing");
        }

        if (this.target == null || !this.target.isAlive() || this.target.isRemoved() || --this.retargetIn <= 0) {
            LivingEntity next = this.find(player, level, config);
            if (next != this.target) {
                this.repathIn = 0;
            }
            this.target = next;
            this.retargetIn = RETARGET_TICKS;
        }
        if (this.lastTarget != null && this.target != this.lastTarget && !this.lastTarget.isAlive()) {
            this.kills++;
        }
        this.lastTarget = this.target;

        if (this.target == null) {
            macro.rotator.stop();
            macro.rotator.sync(player);
            return this.wander(macro, mc, player, config);
        }
        this.returning = false;

        double range = config.wynnRange > 0 ? config.wynnRange : type.ranged ? RANGED : MELEE;
        double distance = Math.sqrt(player.distanceToSqr(this.target));
        if (distance > range || type.ranged && !visible(player, level, this.target)) {
            if (!macro.walker.busy() || --this.repathIn <= 0) {
                macro.walker.go(mc, List.of(this.target.position()), Math.max(1.5, Math.min(range - 1.0, 6.0)));
                this.repathIn = 30;
            }
            macro.walker.tick(mc, player, macro.rotator, config);
            return String.format("Chasing %s (%.0fm)", this.targetName, distance);
        }

        macro.walker.stop(mc);
        macro.rotator.follow(player, aimPoint(this.target), Math.min(1.0, config.rotationSpeed / 100.0 * 1.3));

        List<Integer> rotation = WynnSpells.rotation(config.wynnSpells);
        if (this.spellIn > 0) {
            this.spellIn--;
        }
        if (!rotation.isEmpty() && this.spellIn <= 0 && this.manaFor(config, mana) && aimedAt(mc, this.target, type)) {
            int spell = rotation.get(this.rotationIndex++ % rotation.size());
            this.spellIn = Math.max(1, (int) Math.round(config.wynnSpellSeconds * 20.0));
            return this.cast(type, spell, "Fighting " + this.targetName);
        }

        if (--this.attackIn <= 0) {
            this.attack(mc, player, type, this.target);
            this.attackIn = 3 + macro.random.nextInt(3);
        }
        return "Fighting " + this.targetName;
    }

    private boolean manaFor(MinerConfig config, int mana) {
        // Unknown mana (no action bar yet) does not block casting; the server just refuses the spell.
        return mana < 0 || mana >= config.wynnMinMana;
    }

    private String cast(WynnSpells.WynnClass type, int spell, String status) {
        List<WynnSpells.Click> combo = WynnSpells.combo(spell, type);
        if (combo.isEmpty()) {
            return status;
        }
        this.pending.addAll(combo);
        this.casting = spell;
        this.casts++;
        this.clickIn = 0;
        return "Casting spell " + spell;
    }

    /** One spell input: a swing for left, use-item for right. Neither hits an entity, so it can't break the combo. */
    private void click(Minecraft mc, LocalPlayer player, WynnSpells.Click click) {
        if (click == WynnSpells.Click.LEFT) {
            player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
    }

    private void attack(Minecraft mc, LocalPlayer player, WynnSpells.WynnClass type, LivingEntity target) {
        if (type.attack() == WynnSpells.Click.RIGHT) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            return;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit && hit.getType() == HitResult.Type.ENTITY && entityHit.getEntity() == target) {
            mc.gameMode.attack(player, target);
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** Melee classes only cast once the crosshair is on the mob; ranged spells fly where you look, so close is enough. */
    private static boolean aimedAt(Minecraft mc, LivingEntity target, WynnSpells.WynnClass type) {
        if (type.ranged) {
            return true;
        }
        return mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() == target;
    }

    private static Vec3 aimPoint(LivingEntity target) {
        return target.getBoundingBox().getCenter().add(0.0, target.getBbHeight() * 0.2, 0.0);
    }

    private LivingEntity find(LocalPlayer player, ClientLevel level, MinerConfig config) {
        List<Label> labels = new ArrayList<>();
        List<LivingEntity> bodies = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Display.TextDisplay display) {
                this.addLabel(labels, display.position(), display.getText());
            } else if (entity instanceof ArmorStand stand) {
                if (stand.hasCustomName()) {
                    this.addLabel(labels, stand.position(), stand.getCustomName());
                }
            } else if (entity instanceof LivingEntity living && entity != player && living.isAlive()) {
                // Real players have version 4 UUIDs; Wynncraft's NPC and mob player models do not.
                if (!(entity instanceof Player) || entity.getUUID().version() != 4) {
                    bodies.add(living);
                }
            }
        }

        Vec3 c = this.center;
        double radiusSq = (double) config.wynnRadius * config.wynnRadius;
        LivingEntity best = null;
        String bestName = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity body : bodies) {
            double dx = body.getX() - c.x;
            double dz = body.getZ() - c.z;
            if (dx * dx + dz * dz > radiusSq || Math.abs(body.getY() - c.y) > 12.0) {
                continue;
            }
            String name = body.hasCustomName() ? mobName(body.getCustomName().getString()) : null;
            if (name == null) {
                name = labelAbove(labels, body);
            }
            if (name == null || !matches(name, config.wynnMobs)) {
                continue;
            }
            double distance = player.distanceToSqr(body);
            if (!visible(player, level, body)) {
                distance += 400.0;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = body;
                bestName = name;
            }
        }
        if (best != null) {
            this.targetName = bestName;
        }
        return best;
    }

    private void addLabel(List<Label> labels, Vec3 pos, Component text) {
        String name = text == null ? null : mobName(text.getString());
        if (name != null) {
            labels.add(new Label(pos, name));
        }
    }

    private static String labelAbove(List<Label> labels, LivingEntity body) {
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        double top = body.getBbHeight() + 1.5;
        for (Label label : labels) {
            double dx = label.pos.x - body.getX();
            double dz = label.pos.z - body.getZ();
            double above = label.pos.y - body.getY();
            if (dx * dx + dz * dz > 2.25 || above < 0.0 || above > top) {
                continue;
            }
            double distance = dx * dx + dz * dz + above * above * 0.1;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = label.name;
            }
        }
        return best;
    }

    /**
     * The mob name in a label, or null for labels that are not mobs. NPC labels start with a lone icon line
     * (icon, name, description), and damage or XP popups start with '+', '-' or '['.
     */
    static String mobName(String label) {
        if (label == null) {
            return null;
        }
        String clean = FORMATTING.matcher(label).replaceAll("");
        String[] lines = clean.split("\n");
        String first = lines[0].trim();
        if (first.codePointCount(0, first.length()) <= 1 || first.startsWith("+") || first.startsWith("-") || first.startsWith("[")) {
            return null;
        }
        // Keep only readable text: drop private-use glyphs (icons, health bars) Wynncraft mixes in.
        StringBuilder name = new StringBuilder(first.length());
        first.codePoints().filter(WynnCombatMacro::readable).forEach(name::appendCodePoint);
        String result = name.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /** False for Wynncraft's font glyphs and spacers (private-use or unassigned code points). */
    private static boolean readable(int codePoint) {
        int type = Character.getType(codePoint);
        return type != Character.PRIVATE_USE && type != Character.SURROGATE && type != Character.UNASSIGNED;
    }

    static boolean matches(String name, List<String> wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String want : wanted) {
            if (!want.isBlank() && lower.contains(want.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean visible(LocalPlayer player, ClientLevel level, LivingEntity body) {
        Vec3 eye = player.getEyePosition();
        Vec3 center = body.getBoundingBox().getCenter();
        return level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    /** Nothing to fight: walk back toward the center so new spawns come into view. */
    private String wander(Macro macro, Minecraft mc, LocalPlayer player, MinerConfig config) {
        String looking = config.wynnMobs.isEmpty() ? "Looking for mobs" : "Looking for " + config.wynnMobs.get(0);
        if (player.position().distanceToSqr(this.center) <= 9.0) {
            if (this.returning) {
                macro.walker.stop(mc);
                this.returning = false;
            }
            return looking;
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
        String line = String.format("%d kills (%.0f/h), %d spells", this.kills, this.kills / macro.hours(), this.casts);
        String stats = macro.wynn.hudText(System.currentTimeMillis());
        return stats == null || stats.isEmpty() ? line : line + ", " + stats;
    }

    @Override
    public Vec3 home(MinerConfig config) {
        double[] spot = config.wynnSpot;
        return spot == null ? null : new Vec3(spot[0], spot[1], spot[2]);
    }
}
