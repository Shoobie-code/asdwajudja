package com.skyblockminer;

import java.util.Arrays;
import java.util.List;

public enum MacroType {
    MITHRIL("mithril", "Mithril", Category.MINING, "mithril"),
    GEMSTONE("gemstone", "Gemstone", Category.MINING, "gemstone"),
    ORE("ore", "Ore", Category.MINING, "ore"),
    TUNNELS("tunnel", "Glacite Tunnels", Category.MINING, "tunnel"),
    CUSTOM("custom", "Custom Blocks", Category.MINING, "custom"),
    ROUTE("route", "Route Miner", Category.MINING, null),
    POWDER("powder", "Powder", Category.MINING, null),
    COMMISSIONS("commissions", "Commissions", Category.MINING, null),
    FARMING("farming", "Farming", Category.FARMING, null),
    FORAGING("foraging", "Foraging", Category.FORAGING, null),
    FISHING("fishing", "Fishing", Category.FISHING, null),
    GOTO("goto", "Walking", Category.MINING, null);

    public enum Category {
        MINING("Mining"),
        FARMING("Farming"),
        FORAGING("Foraging"),
        FISHING("Fishing");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public static final List<MacroType> SELECTABLE = Arrays.stream(values()).filter(type -> type != GOTO).toList();
    public final String id;
    public final String label;
    public final Category category;
    public final String blocks;

    MacroType(String id, String label, Category category, String blocks) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.blocks = blocks;
    }

    boolean minesInPlace() {
        return this.blocks != null;
    }

    public static MacroType parse(String id) {
        if (id != null) {
            for (MacroType type : values()) {
                if (type.id.equalsIgnoreCase(id.trim()) && type != GOTO) {
                    return type;
                }
            }
        }
        return null;
    }

    public static MacroType parseOr(String id, MacroType fallback) {
        MacroType type = parse(id);
        return type == null ? fallback : type;
    }

    public MacroType next() {
        return this.step(1);
    }

    public MacroType previous() {
        return this.step(-1);
    }

    private MacroType step(int delta) {
        int index = SELECTABLE.indexOf(this);
        return SELECTABLE.get(Math.floorMod(index + delta, SELECTABLE.size()));
    }
}
