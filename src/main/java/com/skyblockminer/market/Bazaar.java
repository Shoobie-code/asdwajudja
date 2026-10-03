package com.skyblockminer.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One snapshot of the bazaar. Hypixel names its order books from the seller's side: {@code sell_summary}
 * holds buy orders (what an instant sell receives) and {@code buy_summary} holds sell offers (what an
 * instant buy costs).
 */
public final class Bazaar {
    /**
     * @param topBuyOrder  highest buy order, the price an instant sell gets
     * @param topSellOffer lowest sell offer, the price an instant buy pays
     */
    public record Product(String id, double topBuyOrder, double topSellOffer, long buyMovingWeek, long sellMovingWeek,
                          int buyOrders, int sellOffers) {
        /** Items moved per day, the smaller of the two sides (a flip needs both). */
        public double dailyVolume() {
            return Math.min(this.buyMovingWeek, this.sellMovingWeek) / 7.0;
        }

        public boolean tradable() {
            return this.topBuyOrder > 0.0 && this.topSellOffer > 0.0;
        }
    }

    public static final Bazaar EMPTY = new Bazaar(Map.of(), 0L);

    private final Map<String, Product> products;
    private final long updatedAt;

    public Bazaar(Map<String, Product> products, long updatedAt) {
        this.products = Collections.unmodifiableMap(products);
        this.updatedAt = updatedAt;
    }

    public Map<String, Product> products() {
        return this.products;
    }

    public Product get(String id) {
        return this.products.get(id);
    }

    public long updatedAt() {
        return this.updatedAt;
    }

    public boolean isEmpty() {
        return this.products.isEmpty();
    }

    /** Cost of buying one item instantly, or NaN when it is not on the bazaar. */
    public double instaBuy(String id) {
        Product p = this.products.get(id);
        return p == null || p.topSellOffer <= 0.0 ? Double.NaN : p.topSellOffer;
    }

    /** Money received for selling one item instantly, or NaN. */
    public double instaSell(String id) {
        Product p = this.products.get(id);
        return p == null || p.topBuyOrder <= 0.0 ? Double.NaN : p.topBuyOrder;
    }

    /** Parses {@code /v2/skyblock/bazaar}. */
    public static Bazaar parse(JsonObject root) {
        if (root == null || !root.has("products")) {
            return EMPTY;
        }
        Map<String, Product> products = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("products").entrySet()) {
            JsonObject product = entry.getValue().getAsJsonObject();
            JsonObject quick = product.has("quick_status") ? product.getAsJsonObject("quick_status") : new JsonObject();
            JsonArray buyOrders = product.has("sell_summary") ? product.getAsJsonArray("sell_summary") : new JsonArray();
            JsonArray sellOffers = product.has("buy_summary") ? product.getAsJsonArray("buy_summary") : new JsonArray();
            products.put(entry.getKey(), new Product(
                entry.getKey(),
                top(buyOrders),
                top(sellOffers),
                number(quick, "buyMovingWeek"),
                number(quick, "sellMovingWeek"),
                (int) number(quick, "buyOrders"),
                (int) number(quick, "sellOrders")));
        }
        long updated = root.has("lastUpdated") ? root.get("lastUpdated").getAsLong() : System.currentTimeMillis();
        return new Bazaar(products, updated);
    }

    private static double top(JsonArray book) {
        return book.isEmpty() ? 0.0 : book.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
    }

    private static long number(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsLong() : 0L;
    }
}
