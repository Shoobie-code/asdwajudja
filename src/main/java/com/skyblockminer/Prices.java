package com.skyblockminer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Instant-sell prices from Hypixel's public Bazaar endpoint (no API key needed), refreshed every 15 minutes
 * while asked for. Used to turn the loot tracker into a profit estimate.
 */
final class Prices {
    private static final URI BAZAAR = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
    private static final long REFRESH_MS = 15 * 60_000L;
    private static final Map<String, String> ALIASES = Map.of(
        "MITHRIL", "MITHRIL_ORE",
        "TITANIUM", "TITANIUM_ORE",
        "COBBLESTONE", "COBBLESTONE",
        "RAW_FISH", "RAW_FISH",
        "CACTUS_GREEN", "INK_SACK:2",
        "COCOA_BEANS", "INK_SACK:3",
        "NETHER_WART", "NETHER_STALK",
        "CARROT", "CARROT_ITEM",
        "POTATO", "POTATO_ITEM"
    );

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    private final Map<String, Double> sell = new ConcurrentHashMap<>();
    private volatile long fetchedAt;
    private volatile boolean fetching;

    /** Starts a background refresh when the prices are missing or stale. */
    void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (this.fetching || now - this.fetchedAt < REFRESH_MS) {
            return;
        }
        this.fetching = true;
        HttpRequest request = HttpRequest.newBuilder(BAZAAR).timeout(Duration.ofSeconds(15L)).GET().build();
        this.client.sendAsync(request, BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() == 200) {
                this.parse(response.body());
                this.fetchedAt = System.currentTimeMillis();
            } else {
                this.fetchedAt = System.currentTimeMillis() - REFRESH_MS + 60_000L;
            }
        }).whenComplete((ok, error) -> {
            if (error != null) {
                MinerMod.LOGGER.warn("Could not fetch Bazaar prices", error);
                this.fetchedAt = System.currentTimeMillis() - REFRESH_MS + 60_000L;
            }
            this.fetching = false;
        });
    }

    void parse(String json) {
        JsonObject products = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("products");
        if (products == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
            JsonObject status = entry.getValue().getAsJsonObject().getAsJsonObject("quick_status");
            if (status != null && status.has("sellPrice")) {
                this.sell.put(entry.getKey(), status.get("sellPrice").getAsDouble());
            }
        }
    }

    boolean ready() {
        return !this.sell.isEmpty();
    }

    /** Instant-sell price of an item by display name, or 0 when the Bazaar does not list it. */
    double price(String displayName) {
        return this.sell.getOrDefault(productId(displayName), 0.0);
    }

    /** Best-effort Bazaar product id for a display name, e.g. "Enchanted Mithril" -> ENCHANTED_MITHRIL. */
    static String productId(String displayName) {
        String id = displayName.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (id.matches("(ROUGH|FLAWED|FINE|FLAWLESS|PERFECT)_[A-Z]+_GEMSTONE")) {
            return id.substring(0, id.length() - "STONE".length());
        }
        return ALIASES.getOrDefault(id, id);
    }
}
