package com.skyblockminer;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Sells configured items through the {@code /trades} menu when the inventory fills, then hands control back
 * to the running macro. Only the main inventory is sold from; the hotbar (tools, weapons) is never touched.
 */
final class AutoSell {
    private static final long TIMEOUT_MS = 15000L;

    private boolean active;
    private long startedAt;
    private long nextActionAt;
    private int sold;
    private int soldThisRun;

    boolean active() {
        return this.active;
    }

    int sold() {
        return this.sold;
    }

    void reset() {
        this.active = false;
        this.sold = 0;
    }

    void begin() {
        if (!this.active) {
            this.active = true;
            this.startedAt = System.currentTimeMillis();
            this.nextActionAt = 0L;
            this.soldThisRun = 0;
        }
    }

    /** True when an item name contains one of the configured words (case-insensitive). */
    static boolean matches(String name, List<String> words) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String word : words) {
            String w = word.trim().toLowerCase(Locale.ROOT);
            if (!w.isEmpty() && lower.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Runs one tick of selling and returns the HUD status. Clears {@link #active()} when done. */
    String tick(Macro macro, Minecraft mc, LocalPlayer player) {
        long now = System.currentTimeMillis();
        String title = Inv.screenTitle(mc);
        if (title == null) {
            if (now - this.startedAt > TIMEOUT_MS) {
                this.active = false;
                macro.stop("Inventory full and the Trades menu would not open");
                return "Off";
            }
            if (now >= this.nextActionAt) {
                macro.command("trades");
                this.nextActionAt = now + 3000L;
            }
            return "Opening Trades to sell";
        }
        if (!title.contains("Trades")) {
            player.closeContainer();
            return "Opening Trades to sell";
        }
        if (now < this.nextActionAt) {
            return "Selling";
        }
        // The player's inventory is the last 36 slots of the menu; the final 9 of those are the hotbar.
        int size = player.containerMenu.slots.size();
        for (int slot = Math.max(0, size - 36); slot < size - 9; slot++) {
            ItemStack stack = Inv.slot(mc, slot);
            String name = Inv.name(stack);
            if (!stack.isEmpty() && !Inv.isMiningTool(stack) && matches(name, macro.config.sellItems)) {
                Inv.click(mc, slot);
                this.sold++;
                this.soldThisRun++;
                this.nextActionAt = now + 250L + macro.random.nextInt(200);
                return "Selling " + name;
            }
        }
        player.closeContainer();
        this.active = false;
        if (this.soldThisRun == 0) {
            macro.stop("Inventory full and nothing on the sell list to sell");
            return "Off";
        }
        return "Sold";
    }
}
