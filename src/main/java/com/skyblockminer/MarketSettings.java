package com.skyblockminer;

import com.skyblockminer.market.AuctionFlipper;
import com.skyblockminer.market.BazaarFlipper;
import com.skyblockminer.market.Flips;

/** Turns the market options in {@link MinerConfig} into flip-finder settings. */
public final class MarketSettings {
    private static final int LIST_LIMIT = 100;
    /** Bazaar tax with the Bazaar Flipper account upgrade. */
    private static final double PERK_TAX = 0.01125;

    private MarketSettings() {
    }

    public static Flips.Settings flips(MinerConfig c) {
        return new Flips.Settings(bazaar(c), auctions(c), c.craftMinProfit, c.npcBudget);
    }

    static BazaarFlipper.Settings bazaar(MinerConfig c) {
        return new BazaarFlipper.Settings(c.bazaarBudget, c.bazaarMinVolume, c.bazaarMinMargin,
            c.bazaarFlipperPerk ? PERK_TAX : BazaarFlipper.DEFAULT_TAX, LIST_LIMIT);
    }

    static AuctionFlipper.Settings auctions(MinerConfig c) {
        return new AuctionFlipper.Settings(c.auctionMinProfit, c.auctionMinMargin, c.auctionMinListings, c.auctionMaxPrice, LIST_LIMIT);
    }
}
