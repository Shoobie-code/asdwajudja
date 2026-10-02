package com.skyblockminer.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Items the bazaar sells for less than an NPC pays for them. */
public final class NpcFlipper {
    public record Flip(String id, double buyEach, double npcEach, double profitEach, long amount, double profit) {
    }

    private NpcFlipper() {
    }

    public static List<Flip> find(Bazaar bazaar, Map<String, Double> npcPrices, double budget, int limit) {
        List<Flip> flips = new ArrayList<>();
        for (Bazaar.Product product : bazaar.products().values()) {
            Double npc = npcPrices.get(product.id());
            double buy = product.topSellOffer();
            if (npc == null || buy <= 0.0 || npc <= buy) {
                continue;
            }
            long amount = Math.min((long) (budget / buy), (long) (product.dailyVolume() * 0.05));
            if (amount <= 0) {
                continue;
            }
            double each = npc - buy;
            flips.add(new Flip(product.id(), buy, npc, each, amount, each * amount));
        }
        flips.sort(Comparator.comparingDouble(Flip::profit).reversed());
        return flips.size() > limit ? List.copyOf(flips.subList(0, limit)) : flips;
    }
}
