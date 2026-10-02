package com.skyblockminer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Targets {
    public static final List<String> MODES = List.of("mithril", "gemstone", "ore", "tunnel", "custom");
    public static final Map<String, String> GEMSTONES = new LinkedHashMap<>();
    private static final List<String> HOLLOWS_GEMS = List.of("ruby", "amber", "sapphire", "jade", "amethyst", "topaz", "jasper");
    private static final List<String> TUNNEL_GEMS = List.of("onyx", "aquamarine", "citrine", "peridot");
    private static final List<String> ORE_BLOCKS = List.of(
        "coal_block", "quartz_block", "iron_block", "redstone_block", "gold_block", "diamond_block", "emerald_block", "lapis_block"
    );
    private static final List<String> TUNNEL_BLOCKS = List.of(
        "packed_ice", "smooth_red_sandstone", "terracotta", "brown_terracotta", "clay", "infested_cobblestone"
    );
    private static final List<String> POWDER_BLOCKS = List.of(
        "stone", "coal_ore", "iron_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore"
    );

    private Targets() {
    }

    public static Map<String, Integer> costs(String blocks, MinerConfig config) {
        Map<String, Integer> costs = new HashMap<>();
        switch (blocks == null ? "mithril" : blocks) {
            case "gemstone":
                HOLLOWS_GEMS.forEach(gem -> gem(costs, config, gem));
                break;
            case "ore":
                ORE_BLOCKS.forEach(block -> costs.put("minecraft:" + block, 4));
                break;
            case "tunnel":
                TUNNEL_BLOCKS.forEach(block -> costs.put("minecraft:" + block, 4));
                TUNNEL_GEMS.forEach(gem -> gem(costs, config, gem));
                break;
            case "custom":
                config.customBlocks.forEach(block -> costs.put(normalize(block), 4));
                break;
            default:
                costs.putAll(mithril(config.prioritizeTitanium));
        }

        return costs;
    }

    public static Map<String, Integer> powder() {
        Map<String, Integer> costs = new HashMap<>();
        POWDER_BLOCKS.forEach(block -> costs.put("minecraft:" + block, block.equals("stone") ? 1 : 4));
        mithril(false).keySet().forEach(block -> costs.put(block, 4));
        return costs;
    }

    public static Map<String, Integer> mithril(boolean titaniumFirst) {
        Map<String, Integer> costs = new HashMap<>();
        costs.put("minecraft:polished_diorite", titaniumFirst ? 1 : 30);
        costs.put("minecraft:light_blue_wool", 3);
        costs.put("minecraft:prismarine", 10);
        costs.put("minecraft:prismarine_bricks", 10);
        costs.put("minecraft:dark_prismarine", 10);
        costs.put("minecraft:gray_wool", 20);
        costs.put("minecraft:cyan_terracotta", 20);
        return costs;
    }

    public static String normalize(String block) {
        String id = block.trim().toLowerCase();
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static void gem(Map<String, Integer> costs, MinerConfig config, String gem) {
        if (config.gemstones.contains(gem)) {
            String color = GEMSTONES.get(gem);
            costs.put("minecraft:" + color + "_stained_glass", 4);
            costs.put("minecraft:" + color + "_stained_glass_pane", 4);
        }
    }

    static {
        GEMSTONES.put("ruby", "red");
        GEMSTONES.put("amber", "orange");
        GEMSTONES.put("sapphire", "light_blue");
        GEMSTONES.put("jade", "lime");
        GEMSTONES.put("amethyst", "purple");
        GEMSTONES.put("topaz", "yellow");
        GEMSTONES.put("jasper", "magenta");
        GEMSTONES.put("onyx", "black");
        GEMSTONES.put("aquamarine", "blue");
        GEMSTONES.put("citrine", "brown");
        GEMSTONES.put("peridot", "green");
    }
}
