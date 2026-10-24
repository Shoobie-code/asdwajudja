package com.skyblockminer.helpers;

import com.skyblockminer.config.MinerConfig;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Hides common chat spam so alerts that matter stay visible. Hidden lines still reach the macro's own chat
 * handling (failsafe alerts, loot and pest messages), they are only kept off the screen.
 *
 * <p>Idea from SkyHanni's chat filters (LGPL-2.1, github.com/hannibal002/SkyHanni); the patterns here are written
 * independently from Hypixel's message texts.
 */
public final class ChatFilter {
    private static final Pattern LOBBY = Pattern.compile("(joined|spooked into|sled into|slid into) the lobby!");
    private static final Pattern WATCHDOG = Pattern.compile(
        "^\\[WATCHDOG ANNOUNCEMENT]|^Watchdog has banned [\\d,]+ players|^Staff have banned an additional [\\d,]+|^Blacklisted modifications are a bannable offense!");
    private static final Pattern PROFILE = Pattern.compile("^You are playing on profile: |^Profile ID: |^Sending to server ");
    private static final Pattern ABILITY = Pattern.compile(
        "^You do not have enough mana|^This ability is on cooldown|^There are blocks in the way!|^Your .+ hit \\d+ enem(y|ies) for ");
    private static final Pattern COMBO = Pattern.compile("^\\+\\d+ Kill Combo|^Your Kill Combo has expired!");

    private ChatFilter() {
    }

    /** Whether a received chat line should be hidden, given the filter settings. */
    public static boolean hide(String text, MinerConfig config) {
        if (!config.chatFilter || text.isBlank()) {
            return false;
        }
        String line = text.strip();
        // Never hide what a person might be saying to you.
        if (line.startsWith("From ") || line.toLowerCase(Locale.ROOT).contains("macro")) {
            return false;
        }
        if (config.filterLobby && LOBBY.matcher(line).find()) {
            return true;
        }
        if (config.filterWatchdog && WATCHDOG.matcher(line).find()) {
            return true;
        }
        if (config.filterProfile && PROFILE.matcher(line).find()) {
            return true;
        }
        if (config.filterAbility && ABILITY.matcher(line).find()) {
            return true;
        }
        if (config.filterCombo && COMBO.matcher(line).find()) {
            return true;
        }
        return matchesCustom(line, config.filterCustom);
    }

    static boolean matchesCustom(String line, List<String> custom) {
        if (custom.isEmpty()) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (String part : custom) {
            if (!part.isBlank() && lower.contains(part.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
