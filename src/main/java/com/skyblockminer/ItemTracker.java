package com.skyblockminer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Session loot tracker: counts items that appear in the inventory (by display name) plus items reported by
 * "[Sacks]" messages. Changes made while a menu is open are ignored, so moving items around or taking them
 * out of chests does not count as loot.
 */
final class ItemTracker {
    private static final Pattern SACKS = Pattern.compile("\\[Sacks] \\+([\\d,]+) items?");
    private static final int SCAN_EVERY = 20;

    private final Map<String, Integer> last = new HashMap<>();
    private final Map<String, Integer> gained = new LinkedHashMap<>();
    private final Map<String, Integer> scratch = new HashMap<>();
    private boolean primed;
    private int scanIn;
    private long sacks;

    void reset() {
        this.last.clear();
        this.gained.clear();
        this.primed = false;
        this.sacks = 0L;
    }

    void tick(Minecraft mc, LocalPlayer player) {
        if (--this.scanIn > 0) {
            return;
        }
        this.scanIn = SCAN_EVERY;
        this.scratch.clear();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                this.scratch.merge(Inv.name(stack), stack.getCount(), Integer::sum);
            }
        }
        if (this.primed && mc.gui.screen() == null) {
            for (Map.Entry<String, Integer> entry : this.scratch.entrySet()) {
                int delta = entry.getValue() - this.last.getOrDefault(entry.getKey(), 0);
                if (delta > 0) {
                    this.gained.merge(entry.getKey(), delta, Integer::sum);
                }
            }
        }
        this.primed = true;
        this.last.clear();
        this.last.putAll(this.scratch);
    }

    void onChat(String text) {
        Matcher matcher = SACKS.matcher(text);
        if (matcher.find()) {
            try {
                this.sacks += Long.parseLong(matcher.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
                // Malformed numbers are not worth failing over.
            }
        }
    }

    long sacks() {
        return this.sacks;
    }

    /** Total value of everything gained, priced per display name (sack items are not counted). */
    double value(java.util.function.ToDoubleFunction<String> price) {
        double total = 0.0;
        for (Map.Entry<String, Integer> entry : this.gained.entrySet()) {
            total += entry.getValue() * price.applyAsDouble(entry.getKey());
        }
        return total;
    }

    /** The {@code limit} items gained most, largest first. */
    List<Map.Entry<String, Integer>> top(int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(this.gained.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }
}
