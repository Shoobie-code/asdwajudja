package com.skyblockminer.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prices crafts from bazaar data. Ingredients are bought instantly when the bazaar has them; otherwise they
 * are crafted from their own recipe (a few levels deep). Anything that can be neither bought nor crafted is
 * reported as unpriced so totals are never silently wrong.
 */
public final class CraftCalc {
    private static final int MAX_DEPTH = 4;
    private static final Pattern MINION = Pattern.compile("^(.+)_GENERATOR_(\\d+)$");

    /** Total coins, whether every part had a price, and the raw items to buy. */
    public record Cost(double coins, boolean complete, Map<String, Long> shopping) {
    }

    public record CraftFlip(Recipe recipe, double cost, double sellEach, double profitEach, double marginPercent,
                            double dailyVolume) {
    }

    private CraftCalc() {
    }

    /**
     * Cost of crafting {@code amount} of {@code id}. Ingredients the bazaar sells are bought; minion tiers
     * below the target are always crafted, since that is how upgrades are made.
     */
    public static Cost cost(String id, long amount, Bazaar bazaar, Function<String, Recipe> recipes) {
        Map<String, Long> shopping = new LinkedHashMap<>();
        boolean[] complete = {true};
        double coins = cost(id, amount, bazaar, recipes, shopping, complete, 0, true);
        return new Cost(coins, complete[0], shopping);
    }

    private static double cost(String id, long amount, Bazaar bazaar, Function<String, Recipe> recipes,
        Map<String, Long> shopping, boolean[] complete, int depth, boolean root) {
        double price = bazaar.instaBuy(id);
        boolean minion = MINION.matcher(id).matches();
        if (!root && !minion && !Double.isNaN(price)) {
            shopping.merge(id, amount, Long::sum);
            return price * amount;
        }
        Recipe recipe = depth < MAX_DEPTH ? recipes.apply(id) : null;
        if (recipe == null) {
            if (!Double.isNaN(price)) {
                shopping.merge(id, amount, Long::sum);
                return price * amount;
            }
            shopping.merge(id, amount, Long::sum);
            complete[0] = false;
            return 0.0;
        }
        long crafts = (amount + recipe.outputCount() - 1) / recipe.outputCount();
        double total = 0.0;
        for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
            total += cost(ingredient.getKey(), ingredient.getValue() * crafts, bazaar, recipes, shopping, complete, depth + 1, false);
        }
        return total;
    }

    /** Bazaar items worth crafting from instantly bought ingredients and selling with a sell offer. */
    public static List<CraftFlip> craftFlips(Bazaar bazaar, Function<String, Recipe> recipes, double tax, double minProfit, int limit) {
        List<CraftFlip> flips = new ArrayList<>();
        for (Bazaar.Product product : bazaar.products().values()) {
            Recipe recipe = recipes.apply(product.id());
            if (recipe == null || !product.tradable()) {
                continue;
            }
            double cost = 0.0;
            boolean priced = true;
            for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
                double each = bazaar.instaBuy(ingredient.getKey());
                if (Double.isNaN(each)) {
                    priced = false;
                    break;
                }
                cost += each * ingredient.getValue();
            }
            if (!priced) {
                continue;
            }
            cost /= recipe.outputCount();
            double sell = product.topSellOffer() - 0.1;
            double profit = sell * (1.0 - tax) - cost;
            if (profit >= minProfit) {
                flips.add(new CraftFlip(recipe, cost, sell, profit, profit / cost * 100.0, product.dailyVolume()));
            }
        }
        flips.sort(Comparator.comparingDouble((CraftFlip f) -> f.profitEach() * Math.min(f.dailyVolume(), 2000.0)).reversed());
        return flips.size() > limit ? List.copyOf(flips.subList(0, limit)) : flips;
    }

    /** "WHEAT_GENERATOR_5" -> {"WHEAT", 5}, or null. */
    public static Object[] minion(String id) {
        Matcher matcher = MINION.matcher(id);
        return matcher.matches() ? new Object[]{matcher.group(1), Integer.parseInt(matcher.group(2))} : null;
    }
}
