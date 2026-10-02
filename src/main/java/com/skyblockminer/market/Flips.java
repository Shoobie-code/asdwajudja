package com.skyblockminer.market;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Current flip lists, recomputed when new market data arrives or settings change. Bazaar, craft and NPC
 * flips are cheap and run on the caller's thread; auction flips scan every BIN and run in the background.
 */
public final class Flips {
    /** User settings that shape the lists. */
    public record Settings(BazaarFlipper.Settings bazaar, AuctionFlipper.Settings auctions, double craftMinProfit, double npcBudget) {
    }

    private static final long RECIPE_RECHECK_MS = 3000L;

    private volatile List<BazaarFlipper.Flip> bazaarFlips = List.of();
    private volatile List<AuctionFlipper.Flip> auctionFlips = List.of();
    private volatile List<CraftCalc.CraftFlip> craftFlips = List.of();
    private volatile List<NpcFlipper.Flip> npcFlips = List.of();
    private long bazaarSeen = -1L;
    private long auctionsSeen = -1L;
    private Settings settingsSeen;
    private long craftCheckedAt;
    private volatile boolean auctionsComputing;

    public List<BazaarFlipper.Flip> bazaar() {
        return this.bazaarFlips;
    }

    public List<AuctionFlipper.Flip> auctions() {
        return this.auctionFlips;
    }

    public List<CraftCalc.CraftFlip> crafts() {
        return this.craftFlips;
    }

    public List<NpcFlipper.Flip> npc() {
        return this.npcFlips;
    }

    public void update(Market market, Settings settings) {
        Bazaar bazaar = market.bazaar();
        boolean settingsChanged = !settings.equals(this.settingsSeen);
        this.settingsSeen = settings;
        long now = System.currentTimeMillis();

        if (!bazaar.isEmpty() && (bazaar.updatedAt() != this.bazaarSeen || settingsChanged)) {
            this.bazaarSeen = bazaar.updatedAt();
            this.bazaarFlips = BazaarFlipper.find(bazaar, settings.bazaar());
            this.npcFlips = NpcFlipper.find(bazaar, market.npcPrices(), settings.npcBudget(), settings.bazaar().limit());
            this.craftCheckedAt = 0L;
        }
        // Recipes stream in over the first minute, so keep refreshing craft flips while they load.
        if (!bazaar.isEmpty() && (now - this.craftCheckedAt > RECIPE_RECHECK_MS && (this.craftCheckedAt == 0L || market.recipesPending() > 0) || settingsChanged)) {
            this.craftCheckedAt = now;
            this.craftFlips = CraftCalc.craftFlips(bazaar, market::recipe, settings.bazaar().tax(), settings.craftMinProfit(), settings.bazaar().limit());
        }

        long auctionsAt = market.auctionsUpdatedAt();
        if (auctionsAt != 0L && !this.auctionsComputing && (auctionsAt != this.auctionsSeen || settingsChanged)) {
            this.auctionsSeen = auctionsAt;
            this.auctionsComputing = true;
            List<Auctions.Listing> listings = new ArrayList<>(market.auctions());
            CompletableFuture.supplyAsync(() -> AuctionFlipper.find(listings, settings.auctions()))
                .whenComplete((flips, failure) -> {
                    if (flips != null) {
                        this.auctionFlips = flips;
                    }
                    this.auctionsComputing = false;
                });
        }
    }
}
