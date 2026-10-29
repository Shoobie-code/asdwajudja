package com.skyblockminer.game;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class Sidebar {
    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern HEAT = Pattern.compile("Heat:\\D*?(\\d+)");
    private static final Pattern COLD = Pattern.compile("Cold:\\s*-?\\s*(\\d+)");
    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+)");
    private static final Pattern CLEARED = Pattern.compile("Cleared:\\s*(\\d+)%");
    // The SkyBlock location line is marked with the area glyph (⏣); Wynncraft uses its own glyph.
    private static final Pattern LOCATION = Pattern.compile("[⏣ф]\\s*(.+)");
    private static final Pattern SLAYER = Pattern.compile(
        "(Revenant Horror|Tarantula Broodfather|Sven Packmaster|Voidgloom Seraph|Inferno Demonlord|Riftstalker Bloodfiend)\\s+([IVX]+)");

    private Sidebar() {
    }

    public static List<String> lines(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        if (mc.level == null) {
            return lines;
        } else {
            Scoreboard scoreboard = mc.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective == null) {
                return lines;
            } else {
                for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
                    if (!entry.isHidden()) {
                        PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                        String text = PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
                        lines.add(FORMATTING.matcher(text).replaceAll("").trim());
                    }
                }

                return lines;
            }
        }
    }

    public static int heat(List<String> lines) {
        return find(lines, HEAT);
    }

    public static int cold(List<String> lines) {
        return find(lines, COLD);
    }

    /** The area name from the sidebar's location line (the one with the ⏣ glyph), or null when absent. */
    public static String location(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = LOCATION.matcher(line.trim());
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return null;
    }

    /** Dungeon cleared percentage from the sidebar, or -1 when not in a dungeon. */
    public static int dungeonCleared(List<String> lines) {
        return find(lines, CLEARED);
    }

    /** The active slayer boss and tier (e.g. "Revenant Horror V") from the sidebar, or null when not on a slayer quest. */
    public static String slayer(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = SLAYER.matcher(line);
            if (matcher.find()) {
                return matcher.group(1) + " " + matcher.group(2);
            }
        }
        return null;
    }

    /** The purse (or piggy) coin total from the sidebar, or -1 when no such line is present. */
    public static long purse(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = PURSE.matcher(line);
            if (matcher.find()) {
                try {
                    return Long.parseLong(matcher.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static int find(List<String> lines, Pattern pattern) {
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }

        return -1;
    }
}
