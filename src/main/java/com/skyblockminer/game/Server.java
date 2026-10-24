package com.skyblockminer.game;

import com.skyblockminer.macro.MacroType;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Which network the client is on, so SkyBlock macros only run on Hypixel and Wynncraft macros only on Wynncraft.
 * The Wynncraft host and world-name formats follow what the Wynntils mod reads.
 */
public final class Server {
    public enum Kind {
        HYPIXEL("Hypixel"),
        WYNNCRAFT("Wynncraft"),
        OTHER("this server");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private static final Pattern HYPIXEL = Pattern.compile("^(?:.+\\.)?hypixel\\.(?:net|io)$");
    private static final Pattern WYNNCRAFT = Pattern.compile("^(?:.+\\.)?wynncraft\\.(?:com|net|org)$");
    // Wynncraft lists a fake tab player whose name is the world, e.g. "  Global [WC12]".
    private static final Pattern WYNN_WORLD = Pattern.compile("^Global \\[(.+)]$");

    private static String cachedHost;
    private static Kind cachedKind = Kind.OTHER;

    private Server() {
    }

    /** The network of a server address as typed in the server list (port and case are ignored). */
    static Kind fromHost(String address) {
        if (address == null) {
            return Kind.OTHER;
        }
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon >= 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (HYPIXEL.matcher(host).matches()) {
            return Kind.HYPIXEL;
        }
        if (WYNNCRAFT.matcher(host).matches()) {
            return Kind.WYNNCRAFT;
        }
        return Kind.OTHER;
    }

    public static Kind current(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        String host = server == null ? null : server.ip;
        if (host == null) {
            return Kind.OTHER;
        }
        if (!host.equals(cachedHost)) {
            cachedHost = host;
            cachedKind = fromHost(host);
        }
        return cachedKind;
    }

    /**
     * Why {@code type} cannot start on {@code kind}, or null when it can. Unknown servers (singleplayer, proxies,
     * test servers) are allowed so macros can still be tried out there.
     */
    public static String mismatch(MacroType type, Kind kind) {
        Kind needed = type.server();
        if (needed == null || kind == Kind.OTHER || kind == needed) {
            return null;
        }
        return type.label + " only runs on " + needed.label + ", not on " + kind.label;
    }

    /** The Wynncraft world (like WC12) from tab-list lines, or null in the hub, character selection or elsewhere. */
    static String wynnWorld(List<String> tabLines) {
        for (String line : tabLines) {
            Matcher matcher = WYNN_WORLD.matcher(line.trim());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    static String wynnWorld(Minecraft mc) {
        return wynnWorld(TabList.lines(mc));
    }
}
