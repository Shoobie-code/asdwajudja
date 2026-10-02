package com.skyblockminer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

final class TabList {
    private static final Pattern PROGRESS = Pattern.compile("^(.+?):\\s*(DONE|[\\d.]+%)$");
    private static final Pattern POWDER = Pattern.compile("^(Mithril|Gemstone|Glacite):\\s*([\\d,]+)$");

    private TabList() {
    }

    static List<String> lines(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        if (mc.getConnection() == null) {
            return lines;
        } else {
            for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
                Component name = info.getTabListDisplayName();
                if (name != null) {
                    String text = ChatFormatting.stripFormatting(name.getString());
                    if (text != null && !text.isBlank()) {
                        lines.add(text.trim());
                    }
                }
            }

            return lines;
        }
    }

    static String area(Minecraft mc) {
        for (String line : lines(mc)) {
            if (line.startsWith("Area: ")) {
                return line.substring(6).trim();
            }
        }

        return null;
    }

    static Map<String, Double> commissions(Minecraft mc) {
        return parseCommissions(lines(mc));
    }

    static Map<String, Double> parseCommissions(List<String> lines) {
        Map<String, Double> commissions = new LinkedHashMap<>();

        for (String line : lines) {
            Matcher matcher = PROGRESS.matcher(line);
            if (matcher.matches()) {
                String name = matcher.group(1).trim();
                if (CommissionData.isKnown(name)) {
                    String value = matcher.group(2);
                    commissions.put(name, value.equals("DONE") ? 1.0 : Double.parseDouble(value.replace("%", "")) / 100.0);
                }
            }
        }

        return commissions;
    }

    static long powder(Minecraft mc, String type) {
        return parsePowder(lines(mc), type);
    }

    static long parsePowder(List<String> lines, String type) {
        for (String line : lines) {
            Matcher matcher = POWDER.matcher(line);
            if (matcher.matches() && matcher.group(1).equals(type)) {
                return Long.parseLong(matcher.group(2).replace(",", ""));
            }
        }

        return -1L;
    }
}
