package com.skyblockminer.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A crafting-table recipe from the NEU item repository: ingredient ids with total counts. */
public record Recipe(String id, String name, Map<String, Integer> ingredients, int outputCount) {
    private static final String[] SLOTS = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

    /** Parses one NEU item file; null when it has no crafting recipe. */
    public static Recipe parse(JsonObject item) {
        if (item == null || !item.has("recipe") || !item.get("recipe").isJsonObject()) {
            return null;
        }
        JsonObject grid = item.getAsJsonObject("recipe");
        Map<String, Integer> ingredients = new LinkedHashMap<>();
        for (String slot : SLOTS) {
            JsonElement cell = grid.get(slot);
            if (cell == null || cell.isJsonNull()) {
                continue;
            }
            String text = cell.getAsString().trim();
            if (text.isEmpty()) {
                continue;
            }
            int colon = text.lastIndexOf(':');
            String id = colon < 0 ? text : text.substring(0, colon);
            int count = 1;
            if (colon >= 0) {
                try {
                    count = (int) Math.round(Double.parseDouble(text.substring(colon + 1)));
                } catch (NumberFormatException e) {
                    count = 1;
                }
            }
            ingredients.merge(id, count, Integer::sum);
        }
        if (ingredients.isEmpty()) {
            return null;
        }
        int output = grid.has("count") ? Math.max(1, grid.get("count").getAsInt()) : 1;
        String id = item.has("internalname") ? item.get("internalname").getAsString() : "";
        String name = item.has("displayname") ? item.get("displayname").getAsString().replaceAll("§.", "") : id;
        return new Recipe(id, name, Collections.unmodifiableMap(ingredients), output);
    }
}
