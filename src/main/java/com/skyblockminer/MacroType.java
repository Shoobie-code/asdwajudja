package com.skyblockminer;

import java.util.Arrays;
import java.util.List;

public enum MacroType {
    MITHRIL("mithril", "Mithril", "mithril"),
    GEMSTONE("gemstone", "Gemstone", "gemstone"),
    ORE("ore", "Ore", "ore"),
    TUNNELS("tunnel", "Glacite Tunnels", "tunnel"),
    CUSTOM("custom", "Custom Blocks", "custom"),
    ROUTE("route", "Route Miner", null),
    POWDER("powder", "Powder", null),
    COMMISSIONS("commissions", "Commissions", null),
    GOTO("goto", "Walking", null);

    public static final List<MacroType> SELECTABLE = Arrays.stream(values()).filter(type -> type != GOTO).toList();
    public final String id;
    public final String label;
    public final String blocks;

    private MacroType(String id, String label, String blocks) {
        this.id = id;
        this.label = label;
        this.blocks = blocks;
    }

    boolean minesInPlace() {
        return this.blocks != null;
    }

    static MacroType parse(String id) {
        if (id != null) {
            for (MacroType type : values()) {
                if (type.id.equalsIgnoreCase(id.trim()) && type != GOTO) {
                    return type;
                }
            }
        }

        return null;
    }

    static MacroType parseOr(String id, MacroType fallback) {
        MacroType type = parse(id);
        return type == null ? fallback : type;
    }

    MacroType next() {
        int index = SELECTABLE.indexOf(this);
        return SELECTABLE.get((index + 1) % SELECTABLE.size());
    }
}
