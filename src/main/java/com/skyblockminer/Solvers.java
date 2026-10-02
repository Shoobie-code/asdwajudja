package com.skyblockminer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Menu solvers that run whenever you open the matching menu yourself, independent of the macro:
 * the Experimentation Table's Ultrasequencer and Chronomatron, and Melody's Harp.
 */
final class Solvers {
    /** Slot of the "Remember the pattern!" / "Timer" indicator in both experiment menus. */
    private static final int INDICATOR = 49;

    private final MinerConfig config;
    private String screen = "";
    private long nextClickAt;

    // Experiments: slots to click, in order, once the timer phase starts.
    private final List<Integer> sequence = new ArrayList<>();
    private final Set<Integer> litLastTick = new HashSet<>();
    private boolean remembering;
    private int clickIndex;

    // Harp: columns whose key was already pressed for the note currently above it.
    private final Set<Integer> pressed = new HashSet<>();

    Solvers(MinerConfig config) {
        this.config = config;
    }

    void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        String title = Inv.screenTitle(mc);
        if (player == null || title == null) {
            this.screen = "";
            return;
        }
        if (!title.equals(this.screen)) {
            this.screen = title;
            this.clear();
        }
        if (this.config.solveExperiments && title.startsWith("Ultrasequencer")) {
            this.ultrasequencer(mc);
        } else if (this.config.solveExperiments && title.startsWith("Chronomatron")) {
            this.chronomatron(mc);
        } else if (this.config.solveHarp && title.startsWith("Harp")) {
            this.harp(mc, player);
        } else if (this.config.forgeAutoClaim && Inv.contains(title, this.config.forgeMenu)) {
            this.forge(mc);
        }
    }

    private void clear() {
        this.sequence.clear();
        this.litLastTick.clear();
        this.pressed.clear();
        this.remembering = false;
        this.clickIndex = 0;
    }

    /** True while the indicator says to watch; false once the clock (input phase) shows. */
    private static Boolean watchPhase(Minecraft mc) {
        String name = Inv.name(Inv.slot(mc, INDICATOR));
        if (name.startsWith("Remember")) {
            return true;
        }
        return name.startsWith("Timer") ? false : null;
    }

    /** Numbered items show their order as the stack size while remembering; click them in that order. */
    private void ultrasequencer(Minecraft mc) {
        Boolean watching = watchPhase(mc);
        if (watching == null) {
            return;
        }
        if (watching) {
            TreeMap<Integer, Integer> byOrder = new TreeMap<>();
            for (int slot = 0; slot < INDICATOR - 4; slot++) {
                ItemStack stack = Inv.slot(mc, slot);
                if (!stack.isEmpty() && !itemId(stack).endsWith("glass_pane")) {
                    byOrder.putIfAbsent(stack.getCount(), slot);
                }
            }
            if (!byOrder.isEmpty()) {
                this.sequence.clear();
                this.sequence.addAll(byOrder.values());
                this.clickIndex = 0;
            }
            return;
        }
        this.clickNext(mc);
    }

    /** Lit (enchanted) slots flash one after another while remembering; repeat them in that order. */
    private void chronomatron(Minecraft mc) {
        Boolean watching = watchPhase(mc);
        if (watching == null) {
            return;
        }
        if (watching) {
            if (!this.remembering) {
                this.remembering = true;
                this.sequence.clear();
                this.clickIndex = 0;
            }
            Set<Integer> lit = new HashSet<>();
            int first = -1;
            for (int slot = 0; slot < INDICATOR - 4; slot++) {
                if (Inv.slot(mc, slot).hasFoil()) {
                    lit.add(slot);
                    if (first < 0 && !this.litLastTick.contains(slot)) {
                        first = slot;
                    }
                }
            }
            if (first >= 0) {
                this.sequence.add(first);
            }
            this.litLastTick.clear();
            this.litLastTick.addAll(lit);
            return;
        }
        this.remembering = false;
        this.litLastTick.clear();
        this.clickNext(mc);
    }

    private void clickNext(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (this.clickIndex >= this.sequence.size() || now < this.nextClickAt) {
            return;
        }
        Inv.click(mc, this.sequence.get(this.clickIndex++));
        this.nextClickAt = now + Math.max(50, this.config.solverClickDelay);
    }

    /** Claims every finished Forge slot (lore mentions the claim text) while the Forge menu is open. */
    private void forge(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < this.nextClickAt) {
            return;
        }
        int slot = Inv.menuSlotWithLore(mc, this.config.forgeClaimText);
        if (slot >= 0) {
            Inv.click(mc, slot);
            this.nextClickAt = now + Math.max(300, this.config.solverClickDelay * 2);
        }
    }

    /** Presses a column's key (quartz) when a note (wool) reaches the slot right above it. */
    private void harp(Minecraft mc, LocalPlayer player) {
        int size = Math.min(player.containerMenu.slots.size() - 36, 54);
        for (int key = 9; key < size; key++) {
            if (!itemId(Inv.slot(mc, key)).endsWith("quartz_block")) {
                continue;
            }
            int column = key % 9;
            boolean note = itemId(Inv.slot(mc, key - 9)).endsWith("_wool");
            if (!note) {
                this.pressed.remove(column);
            } else if (this.pressed.add(column)) {
                Inv.click(mc, key);
            }
        }
    }

    static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
