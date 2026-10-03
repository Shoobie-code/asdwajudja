package com.skyblockminer.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BIN flips: the cheapest listing of an item that sits well below the next cheapest one. Profit is what
 * relisting just under that next price nets after the auction house's listing fee and claim tax.
 */
public final class AuctionFlipper {
    public record Settings(long minProfit, double minMarginPercent, int minListings, long maxPrice, int limit) {
    }

    public record Flip(Auctions.Listing listing, long resellAt, long profit, double marginPercent, int listings) {
    }

    private AuctionFlipper() {
    }

    /** Listing fee by price, as charged when creating a BIN auction. */
    public static double listingFee(long price) {
        if (price >= 100_000_000L) {
            return 0.025;
        }
        return price >= 10_000_000L ? 0.02 : 0.01;
    }

    /** Coins kept when a BIN sells for {@code price}: listing fee and the 1% claim tax above 1M. */
    public static long net(long price) {
        double kept = price * (1.0 - listingFee(price));
        if (price > 1_000_000L) {
            kept -= price * 0.01;
        }
        return (long) Math.floor(kept);
    }

    public static List<Flip> find(List<Auctions.Listing> listings, Settings settings) {
        Map<String, long[]> lowest = new HashMap<>();
        Map<String, Auctions.Listing> cheapest = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Auctions.Listing listing : listings) {
            counts.merge(listing.key(), 1, Integer::sum);
            long[] two = lowest.computeIfAbsent(listing.key(), k -> new long[]{Long.MAX_VALUE, Long.MAX_VALUE});
            if (listing.price() < two[0]) {
                two[1] = two[0];
                two[0] = listing.price();
                cheapest.put(listing.key(), listing);
            } else if (listing.price() < two[1]) {
                two[1] = listing.price();
            }
        }

        List<Flip> flips = new ArrayList<>();
        for (Map.Entry<String, Auctions.Listing> entry : cheapest.entrySet()) {
            Auctions.Listing listing = entry.getValue();
            long second = lowest.get(entry.getKey())[1];
            int count = counts.get(entry.getKey());
            if (second == Long.MAX_VALUE || count < settings.minListings() || listing.price() > settings.maxPrice()) {
                continue;
            }
            long resell = second - 1;
            long profit = net(resell) - listing.price();
            double percent = listing.price() == 0 ? 0.0 : profit * 100.0 / listing.price();
            if (profit >= settings.minProfit() && percent >= settings.minMarginPercent()) {
                flips.add(new Flip(listing, resell, profit, percent, count));
            }
        }
        flips.sort(Comparator.comparingLong(Flip::profit).reversed());
        return flips.size() > settings.limit() ? List.copyOf(flips.subList(0, settings.limit())) : flips;
    }
}
