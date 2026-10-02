package com.skyblockminer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

final class Inv {
    private Inv() {
    }

    static String name(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            String text = ChatFormatting.stripFormatting(stack.getHoverName().getString());
            return text == null ? "" : text.trim();
        } else {
            return "";
        }
    }

    static List<String> lore(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        if (stack != null && !stack.isEmpty()) {
            ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
            if (lore == null) {
                return lines;
            } else {
                for (Component line : lore.lines()) {
                    String text = ChatFormatting.stripFormatting(line.getString());
                    if (text != null) {
                        lines.add(text.trim());
                    }
                }

                return lines;
            }
        } else {
            return lines;
        }
    }

    static boolean isMiningTool(ItemStack stack) {
        String name = name(stack).toLowerCase();
        return name.contains("drill") || name.contains("pickaxe") || name.contains("gauntlet") || name.contains("pickonimbus");
    }

    static int hotbar(LocalPlayer player, Predicate<ItemStack> match) {
        for (int slot = 0; slot < 9; slot++) {
            if (match.test(player.getInventory().getItem(slot))) {
                return slot;
            }
        }

        return -1;
    }

    static String screenTitle(Minecraft mc) {
        if (mc.gui.screen() == null) {
            return null;
        } else {
            String title = ChatFormatting.stripFormatting(mc.gui.screen().getTitle().getString());
            return title == null ? "" : title.trim();
        }
    }

    static void click(Minecraft mc, int slot) {
        LocalPlayer player = mc.player;
        if (player != null && mc.gameMode != null) {
            mc.gameMode.handleContainerInput(player.containerMenu.containerId, slot, 0, ContainerInput.PICKUP, player);
        }
    }

    static ItemStack slot(Minecraft mc, int slot) {
        LocalPlayer player = mc.player;
        return player != null && slot >= 0 && slot < player.containerMenu.slots.size() ? player.containerMenu.getSlot(slot).getItem() : ItemStack.EMPTY;
    }

    /** Number of slots that belong to the open menu itself (everything before the player's 36 inventory slots). */
    static int menuSize(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player == null ? 0 : Math.max(0, player.containerMenu.slots.size() - 36);
    }

    /** First menu slot (not the player's inventory) whose item name contains {@code text}, ignoring case; -1 if none. */
    static int menuSlotNamed(Minecraft mc, String text) {
        return findSlot(mc, 0, menuSize(mc), stack -> contains(name(stack), text));
    }

    /** First menu slot whose lore has a line containing {@code text}, ignoring case; -1 if none. */
    static int menuSlotWithLore(Minecraft mc, String text) {
        return findSlot(mc, 0, menuSize(mc), stack -> lore(stack).stream().anyMatch(line -> contains(line, text)));
    }

    /** Slot index (in the open menu) of a player-inventory item whose name contains {@code text}; -1 if none. */
    static int playerSlotNamed(Minecraft mc, String text) {
        LocalPlayer player = mc.player;
        return player == null ? -1 : findSlot(mc, menuSize(mc), player.containerMenu.slots.size(), stack -> contains(name(stack), text));
    }

    static int findSlot(Minecraft mc, int from, int to, Predicate<ItemStack> match) {
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = slot(mc, slot);
            if (!stack.isEmpty() && match.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /** Case-insensitive match of {@code part} as whole words, so "Tier I" does not match "Tier II". */
    static boolean contains(String text, String part) {
        if (part == null || part.isBlank()) {
            return false;
        }
        String p = java.util.regex.Pattern.quote(part.trim().toLowerCase());
        return java.util.regex.Pattern.compile("(^|\\W)" + p + "($|\\W)").matcher(text.toLowerCase()).find();
    }

    /** True when any of the 36 inventory slots holds an item whose name contains {@code text}. */
    static boolean has(LocalPlayer player, String text) {
        for (int slot = 0; slot < 36; slot++) {
            if (contains(name(player.getInventory().getItem(slot)), text)) {
                return true;
            }
        }
        return false;
    }

    static boolean inventoryFull(LocalPlayer player) {
        for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /** First hotbar slot whose item name contains one of the lower-case keywords, or -1. */
    static int hotbarNamed(LocalPlayer player, List<String> keywords) {
        return hotbar(player, stack -> {
            String name = name(stack).toLowerCase();
            for (String keyword : keywords) {
                if (name.contains(keyword)) {
                    return true;
                }
            }
            return false;
        });
    }

    /** Picks {@code configured} (1-9) when set, otherwise the first hotbar item matching the keywords; -1 if none. */
    static int toolSlot(LocalPlayer player, int configured, List<String> keywords) {
        return configured >= 1 && configured <= 9 ? configured - 1 : hotbarNamed(player, keywords);
    }
}
