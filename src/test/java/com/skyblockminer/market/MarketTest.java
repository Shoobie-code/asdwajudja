package com.skyblockminer.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class MarketTest {
    // ---- fixtures ----

    private static JsonObject neu(String id) throws IOException {
        try (InputStream in = MarketTest.class.getResourceAsStream("/neu/" + id + ".json")) {
            assertNotNull(in, id);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Bazaar.Product product(String id, double buyOrder, double sellOffer, long weekly) {
        return new Bazaar.Product(id, buyOrder, sellOffer, weekly, weekly, 10, 10);
    }

    private static Bazaar bazaar(Bazaar.Product... products) {
        Map<String, Bazaar.Product> map = new HashMap<>();
        for (Bazaar.Product p : products) {
            map.put(p.id(), p);
        }
        return new Bazaar(map, 1L);
    }

    /** Builds base64(gzip(nbt)) the way the auction API encodes item_bytes. */
    private static String itemBytes(String id, String petInfo) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(9);
            out.writeUTF("i");
            out.writeByte(10);
            out.writeInt(1);
            out.writeByte(2);
            out.writeUTF("id");
            out.writeShort(397);
            out.writeByte(10);
            out.writeUTF("tag");
            out.writeByte(10);
            out.writeUTF("ExtraAttributes");
            out.writeByte(8);
            out.writeUTF("id");
            out.writeUTF(id);
            if (petInfo != null) {
                out.writeByte(8);
                out.writeUTF("petInfo");
                out.writeUTF(petInfo);
            }
            out.writeByte(11);
            out.writeUTF("ints");
            out.writeInt(2);
            out.writeInt(7);
            out.writeInt(9);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
        }
        return Base64.getEncoder().encodeToString(raw.toByteArray());
    }

    // ---- NBT and auctions ----

    @Test
    void nbtReadsNestedCompoundsListsAndArrays() throws IOException {
        Map<String, Object> root = Nbt.readBase64(itemBytes("HYPERION", null));
        List<?> items = (List<?>) root.get("i");
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) items.get(0);
        assertEquals((short) 397, item.get("id"));
        assertEquals("HYPERION", Nbt.path(item, "tag", "ExtraAttributes", "id"));
        assertEquals(9, ((int[]) Nbt.path(item, "tag", "ExtraAttributes", "ints"))[1]);
        assertNull(Nbt.path(item, "tag", "missing", "id"));
    }

    @Test
    void auctionKeysUseIdsAndPetTypeWithTier() throws IOException {
        assertEquals("ASPECT_OF_THE_END", Auctions.keyFromBytes(itemBytes("ASPECT_OF_THE_END", null)));
        assertEquals("PET_ENDER_DRAGON_LEGENDARY",
            Auctions.keyFromBytes(itemBytes("PET", "{\"type\":\"ENDER_DRAGON\",\"tier\":\"legendary\",\"exp\":0}")));
        assertNull(Auctions.keyFromBytes("not base64 at all!"));
    }

    @Test
    void auctionPageKeepsOnlyUnclaimedBins() throws IOException {
        String bytes = itemBytes("JUJU_SHORTBOW", null);
        JsonObject page = JsonParser.parseString("""
            {"page":0,"totalPages":3,"lastUpdated":5,"auctions":[
              {"uuid":"a","bin":true,"claimed":false,"item_name":"Juju","tier":"EPIC","starting_bid":100,"start":1,"item_bytes":"%s"},
              {"uuid":"b","bin":false,"item_name":"Juju","starting_bid":50,"item_bytes":"%s"},
              {"uuid":"c","bin":true,"claimed":true,"item_name":"Juju","starting_bid":60,"item_bytes":"%s"}
            ]}""".formatted(bytes, bytes, bytes)).getAsJsonObject();
        Auctions.Page parsed = Auctions.parsePage(page);
        assertEquals(3, parsed.totalPages());
        assertEquals(1, parsed.listings().size());
        assertEquals("JUJU_SHORTBOW", parsed.listings().get(0).key());
    }

    @Test
    void auctionTaxesFollowHypixelBrackets() {
        assertEquals(0.01, AuctionFlipper.listingFee(5_000_000));
        assertEquals(0.02, AuctionFlipper.listingFee(10_000_000));
        assertEquals(0.025, AuctionFlipper.listingFee(150_000_000));
        assertEquals(990_000, AuctionFlipper.net(1_000_000), "no claim tax at exactly 1M");
        assertEquals(1_960_000, AuctionFlipper.net(2_000_000));
    }

    @Test
    void auctionFlipsNeedAGapAndEnoughListings() {
        List<Auctions.Listing> listings = List.of(
            new Auctions.Listing("1", "A", "A", "", 1_000_000, 0),
            new Auctions.Listing("2", "A", "A", "", 2_000_000, 0),
            new Auctions.Listing("3", "A", "A", "", 2_100_000, 0),
            new Auctions.Listing("4", "B", "B", "", 1_000_000, 0),
            new Auctions.Listing("5", "B", "B", "", 1_005_000, 0),
            new Auctions.Listing("6", "C", "C", "", 10, 0));
        List<AuctionFlipper.Flip> flips = AuctionFlipper.find(listings, new AuctionFlipper.Settings(100_000, 5.0, 2, Long.MAX_VALUE, 10));
        assertEquals(1, flips.size(), "B has no gap and C has a single listing");
        AuctionFlipper.Flip flip = flips.get(0);
        assertEquals("1", flip.listing().uuid());
        assertEquals(1_999_999, flip.resellAt());
        assertEquals(AuctionFlipper.net(1_999_999) - 1_000_000, flip.profit());
    }

    // ---- bazaar ----

    @Test
    void bazaarParsesOrderBooksFromTheSellersSide() {
        JsonObject root = JsonParser.parseString("""
            {"lastUpdated":42,"products":{"ENCHANTED_SUGAR":{
              "sell_summary":[{"pricePerUnit":100.0}],
              "buy_summary":[{"pricePerUnit":130.0}],
              "quick_status":{"buyMovingWeek":70000,"sellMovingWeek":140000,"buyOrders":5,"sellOrders":6}}}}""").getAsJsonObject();
        Bazaar bazaar = Bazaar.parse(root);
        assertEquals(42, bazaar.updatedAt());
        assertEquals(130.0, bazaar.instaBuy("ENCHANTED_SUGAR"));
        assertEquals(100.0, bazaar.instaSell("ENCHANTED_SUGAR"));
        assertEquals(10000.0, bazaar.get("ENCHANTED_SUGAR").dailyVolume());
        assertTrue(Double.isNaN(bazaar.instaBuy("NOPE")));
    }

    @Test
    void bazaarFlipsRespectTaxBudgetAndVolume() {
        Bazaar bazaar = bazaar(
            product("GOOD", 100.0, 130.0, 700_000),
            product("TAXED_AWAY", 100.0, 101.5, 700_000),
            product("TOO_SLOW", 100.0, 200.0, 70));
        List<BazaarFlipper.Flip> flips = BazaarFlipper.find(bazaar, new BazaarFlipper.Settings(1_000_000, 100, 1.0, BazaarFlipper.DEFAULT_TAX, 10));
        assertEquals(1, flips.size());
        BazaarFlipper.Flip flip = flips.get(0);
        assertEquals("GOOD", flip.id());
        assertEquals(100.1, flip.buyAt(), 1e-9);
        assertEquals(129.9, flip.sellAt(), 1e-9);
        assertEquals(129.9 * (1 - BazaarFlipper.DEFAULT_TAX) - 100.1, flip.marginEach(), 1e-9);
        assertEquals(5000, flip.amount(), "5% of 100k daily volume, below the 9,990 the budget allows");
    }

    @Test
    void npcFlipsOnlyWhereTheNpcPaysMore() {
        Bazaar bazaar = bazaar(product("CHEAP", 1.0, 2.0, 7_000_000), product("FAIR", 1.0, 5.0, 7_000_000));
        List<NpcFlipper.Flip> flips = NpcFlipper.find(bazaar, Map.of("CHEAP", 3.0, "FAIR", 4.0), 1_000, 10);
        assertEquals(1, flips.size());
        assertEquals("CHEAP", flips.get(0).id());
        assertEquals(500, flips.get(0).amount(), "limited by the 1,000 coin budget at 2 each");
    }

    // ---- recipes ----

    @Test
    void recipesSumIngredientsAcrossTheGrid() throws IOException {
        Recipe sugar = Recipe.parse(neu("ENCHANTED_SUGAR"));
        assertEquals(Map.of("SUGAR_CANE", 160), sugar.ingredients());
        assertEquals("Enchanted Sugar", sugar.name());
        Recipe minion = Recipe.parse(neu("WHEAT_GENERATOR_5"));
        assertEquals(8, minion.ingredients().get("ENCHANTED_WHEAT"));
        assertEquals(1, minion.ingredients().get("WHEAT_GENERATOR_4"));
        assertNull(Recipe.parse(JsonParser.parseString("{\"internalname\":\"X\"}").getAsJsonObject()));
    }

    @Test
    void craftCostRecursesThroughMinionTiersAndFlagsUnpricedParts() throws IOException {
        Map<String, Recipe> recipes = new HashMap<>();
        recipes.put("WHEAT_GENERATOR_5", Recipe.parse(neu("WHEAT_GENERATOR_5")));
        recipes.put("WHEAT_GENERATOR_4", new Recipe("WHEAT_GENERATOR_4", "Wheat Minion IV", Map.of("ENCHANTED_WHEAT", 4, "WHEAT_GENERATOR_3", 1), 1));
        recipes.put("WHEAT_GENERATOR_3", new Recipe("WHEAT_GENERATOR_3", "Wheat Minion III", Map.of("WHEAT", 64, "WOOD_HOE", 1), 1));
        Bazaar bazaar = bazaar(product("ENCHANTED_WHEAT", 150.0, 160.0, 7000), product("WHEAT", 1.0, 2.0, 7000));

        CraftCalc.Cost cost = CraftCalc.cost("WHEAT_GENERATOR_5", 1, bazaar, recipes::get);
        assertEquals(12 * 160.0 + 64 * 2.0, cost.coins(), 1e-9);
        assertFalse(cost.complete(), "the wooden hoe has no price");
        assertEquals(12L, cost.shopping().get("ENCHANTED_WHEAT"));
        assertEquals(1L, cost.shopping().get("WOOD_HOE"));
    }

    @Test
    void craftFlipsCompareInstantBuysWithASellOffer() throws IOException {
        Map<String, Recipe> recipes = Map.of("ENCHANTED_SUGAR", Recipe.parse(neu("ENCHANTED_SUGAR")));
        Bazaar bazaar = bazaar(product("ENCHANTED_SUGAR", 500.0, 700.0, 70_000), product("SUGAR_CANE", 2.0, 3.0, 7_000_000));
        List<CraftCalc.CraftFlip> flips = CraftCalc.craftFlips(bazaar, recipes::get, BazaarFlipper.DEFAULT_TAX, 1.0, 10);
        assertEquals(1, flips.size());
        assertEquals(480.0, flips.get(0).cost(), 1e-9);
        assertEquals(699.9 * (1 - BazaarFlipper.DEFAULT_TAX) - 480.0, flips.get(0).profitEach(), 1e-9);
    }
}
