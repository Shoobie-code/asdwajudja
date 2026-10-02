package com.skyblockminer.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parsing of {@code /v2/skyblock/auctions} pages into BIN listings keyed by a comparable item id. */
public final class Auctions {
    /**
     * @param key   what makes listings comparable: the SkyBlock id, plus type and rarity for pets
     * @param start when the listing was created (ms), used to spot new snipes
     */
    public record Listing(String uuid, String key, String name, String tier, long price, long start) {
    }

    /** One page: listings plus paging info. */
    public record Page(int page, int totalPages, long lastUpdated, List<Listing> listings) {
    }

    private Auctions() {
    }

    public static Page parsePage(JsonObject root) {
        int page = root.has("page") ? root.get("page").getAsInt() : 0;
        int total = root.has("totalPages") ? root.get("totalPages").getAsInt() : 0;
        long updated = root.has("lastUpdated") ? root.get("lastUpdated").getAsLong() : 0L;
        List<Listing> listings = new ArrayList<>();
        if (root.has("auctions")) {
            for (JsonElement element : root.getAsJsonArray("auctions")) {
                Listing listing = parseListing(element.getAsJsonObject());
                if (listing != null) {
                    listings.add(listing);
                }
            }
        }
        return new Page(page, total, updated, listings);
    }

    /** A BIN listing, or null for normal auctions and items that cannot be identified. */
    static Listing parseListing(JsonObject auction) {
        if (!auction.has("bin") || !auction.get("bin").getAsBoolean()) {
            return null;
        }
        if (auction.has("claimed") && auction.get("claimed").getAsBoolean()) {
            return null;
        }
        String key = auction.has("item_bytes") ? keyFromBytes(auction.get("item_bytes").getAsString()) : null;
        if (key == null) {
            return null;
        }
        return new Listing(
            auction.get("uuid").getAsString(),
            key,
            auction.has("item_name") ? auction.get("item_name").getAsString() : key,
            auction.has("tier") ? auction.get("tier").getAsString() : "",
            auction.get("starting_bid").getAsLong(),
            auction.has("start") ? auction.get("start").getAsLong() : 0L);
    }

    /** SkyBlock id of the item in {@code item_bytes}; pets become {@code PET_<TYPE>_<TIER>}. */
    static String keyFromBytes(String itemBytes) {
        try {
            Map<String, Object> root = Nbt.readBase64(itemBytes);
            Object items = root.get("i");
            if (!(items instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?>)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) list.get(0);
            Object id = Nbt.path(item, "tag", "ExtraAttributes", "id");
            if (!(id instanceof String text)) {
                return null;
            }
            if (text.equals("PET") && Nbt.path(item, "tag", "ExtraAttributes", "petInfo") instanceof String info) {
                JsonObject pet = JsonParser.parseString(info).getAsJsonObject();
                return "PET_" + pet.get("type").getAsString() + "_" + pet.get("tier").getAsString().toUpperCase(Locale.ROOT);
            }
            return text;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
