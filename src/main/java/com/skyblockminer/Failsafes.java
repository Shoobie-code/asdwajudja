package com.skyblockminer;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class Failsafes {
    private static final double TELEPORT_DISTANCE = 2.0;
    private static final double ROTATION_DEGREES = 1.5;
    private static final double VELOCITY = 0.5;
    private static final int CAGE_BLOCKS = 2;
    private static final List<String> CHAT_WORDS = List.of(
        "macro", "macroing", "macroer", "bot", "botting", "cheat", "cheater", "cheating", "hack", "hacker", "hacking", "report", "wdr", "ban"
    );
    private static final Pattern WORDS = Pattern.compile("\\b(" + String.join("|", CHAT_WORDS) + ")\\b");
    private Vec3 lastPosition;
    private int slot;
    private final Set<UUID> warnedPlayers = new HashSet<>();
    private BlockPos cageCenter;
    private Set<BlockPos> openAround = new HashSet<>();

    void reset(LocalPlayer player) {
        this.lastPosition = player.position();
        this.slot = player.getInventory().getSelectedSlot();
        this.warnedPlayers.clear();
        this.cageCenter = null;
    }

    void expectSlot(int slot) {
        this.slot = slot;
    }

    String checkTick(LocalPlayer player, boolean teleportOk) {
        Vec3 position = player.position();
        double moved = this.lastPosition == null ? 0.0 : position.distanceTo(this.lastPosition);
        this.lastPosition = position;
        if (!teleportOk && moved > 2.0) {
            return String.format("You were teleported %.1f blocks (possible staff check)", moved);
        } else {
            int current = player.getInventory().getSelectedSlot();
            return current != this.slot ? "Your held slot was changed" : null;
        }
    }

    String checkRotation(double externalDegrees) {
        return externalDegrees > 1.5 ? String.format("Your view was moved %.1f degrees (staff check, or you moved the mouse)", externalDegrees) : null;
    }

    String checkCage(ClientLevel level, LocalPlayer player, Predicate<BlockPos> minedByUs) {
        BlockPos center = player.blockPosition();
        Set<BlockPos> open = new HashSet<>();
        int appeared = 0;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 3, 2))) {
            if (level.getBlockState(pos).isAir()) {
                open.add(pos.immutable());
            } else if (center.equals(this.cageCenter) && this.openAround.contains(pos) && level.getBlockState(pos).is(Blocks.BEDROCK) && !minedByUs.test(pos)) {
                appeared++;
            }
        }

        this.cageCenter = center;
        this.openAround = open;
        return appeared >= 2 ? "Bedrock appeared around you (staff cage check)" : null;
    }

    static String checkVelocity(Vec3 motion, boolean recentlyHurt, boolean onSlime) {
        if (!recentlyHurt && !onSlime) {
            double horizontal = Math.hypot(motion.x, motion.z);
            return horizontal < 0.5 && Math.abs(motion.y) < 0.6
                ? null
                : String.format("You were pushed (velocity %.2f) without taking damage (possible staff check)", Math.max(horizontal, Math.abs(motion.y)));
        } else {
            return null;
        }
    }

    String nearbyPlayer(Minecraft mc, LocalPlayer player, int radius) {
        if (radius > 0 && mc.level != null && mc.getConnection() != null) {
            double radiusSq = (double)radius * radius;

            for (AbstractClientPlayer other : realPlayers(mc, player)) {
                if (!(other.distanceToSqr(player) > radiusSq) && this.warnedPlayers.add(other.getUUID())) {
                    return other.getName().getString();
                }
            }

            return null;
        } else {
            return null;
        }
    }

    String playerInside(Minecraft mc, LocalPlayer player) {
        for (AbstractClientPlayer other : realPlayers(mc, player)) {
            if (other.distanceToSqr(player) < 0.25) {
                return other.getName().getString() + " is standing inside you";
            }
        }

        return null;
    }

    private static List<AbstractClientPlayer> realPlayers(Minecraft mc, LocalPlayer player) {
        return mc.level != null && mc.getConnection() != null
            ? mc.level
                .players()
                .stream()
                .filter(other -> other != player && other.getUUID().version() == 4 && mc.getConnection().getPlayerInfo(other.getUUID()) != null)
                .toList()
            : List.of();
    }

    static String chatMention(String text, String me) {
        int colon = text.indexOf(": ");
        if (colon <= 0) {
            return null;
        } else {
            String sender = text.substring(0, colon);
            String body = text.substring(colon + 2).toLowerCase(Locale.ROOT);
            if (sender.startsWith("To ") || sender.startsWith("[NPC]")) {
                return null;
            } else if (sender.startsWith("From ")) {
                return "Private message: " + text;
            } else if (me.isEmpty() || !sender.endsWith(" " + me) && !sender.equals(me)) {
                if (!me.isEmpty() && Pattern.compile("\\b" + Pattern.quote(me.toLowerCase(Locale.ROOT)) + "\\b").matcher(body).find()) {
                    return "You were mentioned: " + text;
                } else {
                    Matcher match = WORDS.matcher(body);
                    return match.find() ? "Chat says \"" + match.group(1) + "\": " + text : null;
                }
            } else {
                return null;
            }
        }
    }
}
