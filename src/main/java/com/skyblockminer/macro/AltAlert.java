package com.skyblockminer.macro;

import com.skyblockminer.config.MinerConfig;
import java.util.regex.Pattern;

/** Private-messages another account in game when a failsafe catches a macro check. */
public final class AltAlert {
    public static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    public static final String DEFAULT_MESSAGE = "yo im getting macro checked";
    /** Minecraft chat packets are capped at 256 characters, "msg <name> " included. */
    private static final int MAX_COMMAND = 256;
    private long nextAllowedAt = Long.MIN_VALUE;

    /**
     * Returns the command (without the slash) to send for a check at {@code now}, or null when the alert is off,
     * no valid alt name is set, or the last one went out less than the cooldown ago.
     */
    String onCheck(MinerConfig config, long now) {
        if (!config.altAlert || now < this.nextAllowedAt) {
            return null;
        }
        String command = command(config);
        if (command != null) {
            this.nextAllowedAt = now + config.altCooldown * 60000L;
        }
        return command;
    }

    static String command(MinerConfig config) {
        String name = config.altName == null ? "" : config.altName.trim();
        if (!NAME.matcher(name).matches()) {
            return null;
        }
        String prefix = "msg " + name + " ";
        String text = clean(config.altMessage);
        if (text.isEmpty()) {
            text = DEFAULT_MESSAGE;
        }
        String command = prefix + text;
        return command.length() > MAX_COMMAND ? command.substring(0, MAX_COMMAND) : command;
    }

    /** Drops formatting codes and control characters, which the server rejects in chat. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }
}
