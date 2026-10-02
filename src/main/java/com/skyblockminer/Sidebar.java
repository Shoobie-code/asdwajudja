package com.skyblockminer;

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

final class Sidebar {
    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern HEAT = Pattern.compile("Heat:\\D*?(\\d+)");
    private static final Pattern COLD = Pattern.compile("Cold:\\s*-?\\s*(\\d+)");

    private Sidebar() {
    }

    static List<String> lines(Minecraft mc) {
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

    static int heat(List<String> lines) {
        return find(lines, HEAT);
    }

    static int cold(List<String> lines) {
        return find(lines, COLD);
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
