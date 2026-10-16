package com.skyblockminer;

import com.skyblockminer.gui.Toasts;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/**
 * Quality-of-life helpers that run whether or not a macro is on: the info panel (SkyBlock time and upcoming
 * events), and Garden alerts when visitors arrive or pests build up.
 *
 * <p>Feature ideas from SkyHanni (LGPL-2.1, github.com/hannibal002/SkyHanni), implemented independently here.
 */
final class Helpers {
    private static final Pattern ALIVE = Pattern.compile("^Alive:\\s*(\\d+)");
    private static final Pattern PLOTS = Pattern.compile("^(?:Infested )?Plots:\\s*(.+)$");
    private static final int REFRESH_TICKS = 10;
    private final MinerConfig config;
    private List<String> lines = List.of();
    private int refreshIn;
    private int lastVisitors = -1;
    private int lastPests = -1;
    private String area;

    Helpers(MinerConfig config) {
        this.config = config;
    }

    /** Info panel lines, rebuilt twice a second so rendering allocates nothing. */
    List<String> lines() {
        return this.lines;
    }

    void tick(Minecraft mc) {
        if (--this.refreshIn > 0) {
            return;
        }
        this.refreshIn = REFRESH_TICKS;
        if (mc.player == null || mc.getConnection() == null) {
            this.lines = List.of();
            this.lastVisitors = -1;
            this.lastPests = -1;
            return;
        }
        List<String> tab = TabList.lines(mc);
        String area = null;
        for (String line : tab) {
            if (line.startsWith("Area: ")) {
                area = line.substring(6).trim();
                break;
            }
        }
        if (area == null || !area.equals(this.area)) {
            this.lastVisitors = -1;
            this.lastPests = -1;
        }
        this.area = area;
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (this.config.infoPanel) {
            out.add(SkyblockTime.format(now));
            int shown = 0;
            for (SkyblockTime.Event event : SkyblockTime.events(now)) {
                if (shown++ >= this.config.infoEvents) {
                    break;
                }
                out.add(event.active(now)
                    ? event.name() + ": now (" + SkyblockTime.duration(event.end() - now) + " left)"
                    : event.name() + ": " + SkyblockTime.duration(event.start() - now));
            }
        }
        if ("Garden".equals(area)) {
            Pests pests = parsePests(tab);
            int visitors = TabList.visitors(tab);
            if (this.config.infoPanel) {
                if (pests.alive() >= 0) {
                    out.add("Pests: " + pests.alive() + (pests.plots().isEmpty() ? "" : " (plots " + pests.plots() + ")"));
                }
                if (visitors >= 0) {
                    out.add("Visitors: " + visitors);
                }
            }
            if (this.config.gardenAlerts) {
                if (this.lastVisitors >= 0 && visitors > this.lastVisitors) {
                    Toasts.push("Garden", "A visitor arrived (" + visitors + " waiting)", Toasts.Kind.INFO);
                }
                if (this.lastPests >= 0 && pests.alive() > this.lastPests && pests.alive() >= this.config.pestAlertAt) {
                    Toasts.push("Pests", pests.alive() + " pests" + (pests.plots().isEmpty() ? "" : " in plots " + pests.plots()), Toasts.Kind.WARNING);
                }
            }
            this.lastVisitors = visitors;
            this.lastPests = pests.alive();
        }
        this.lines = out;
    }

    record Pests(int alive, String plots) {
    }

    /** The Garden's pest widget in the tab list: "Alive: N" and "Infested Plots: a, b". Alive is -1 when missing. */
    static Pests parsePests(List<String> tab) {
        int alive = -1;
        String plots = "";
        for (String line : tab) {
            Matcher m = ALIVE.matcher(line);
            if (m.find()) {
                alive = Integer.parseInt(m.group(1));
                continue;
            }
            m = PLOTS.matcher(line);
            if (m.find() && !m.group(1).trim().equalsIgnoreCase("none")) {
                plots = m.group(1).trim();
            }
        }
        return new Pests(alive, plots);
    }
}
