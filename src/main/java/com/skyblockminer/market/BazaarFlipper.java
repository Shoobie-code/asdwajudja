package com.skyblockminer.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds order flips: place a buy order just above the best buy order, then a sell offer just below the best
 * sell offer. Ranked by the profit the budget can make in one turnaround, limited by how much the item
 * actually trades so slow items do not top the list.
 */
public final class BazaarFlipper {
    /** Bazaar sell tax without the Bazaar Flipper account upgrade. */
    public static final double DEFAULT_TAX = 0.0125;
    private static final double OUTBID = 0.1;
    /** Share of daily volume one player can expect to fill. */
    private static final double FILL_SHARE = 0.05;
    private static final int MAX_ORDER = 71680;

    public record Settings(double budget, double minDailyVolume, double minMarginPercent, double tax, int limit) {
    }

    public record Flip(String id, double buyAt, double sellAt, double marginEach, double marginPercent, long amount,
                       double profit, double dailyVolume) {
    }

    private BazaarFlipper() {
    }

    public static List<Flip> find(Bazaar bazaar, Settings settings) {
        List<Flip> flips = new ArrayList<>();
        for (Bazaar.Product product : bazaar.products().values()) {
            if (!product.tradable() || product.dailyVolume() < settings.minDailyVolume()) {
                continue;
            }
            double buyAt = product.topBuyOrder() + OUTBID;
            double sellAt = product.topSellOffer() - OUTBID;
            double margin = sellAt * (1.0 - settings.tax()) - buyAt;
            if (margin <= 0.0 || buyAt <= 0.0) {
                continue;
            }
            double percent = margin / buyAt * 100.0;
            if (percent < settings.minMarginPercent()) {
                continue;
            }
            long affordable = (long) Math.floor(settings.budget() / buyAt);
            long fillable = (long) Math.floor(product.dailyVolume() * FILL_SHARE);
            long amount = Math.min(Math.min(affordable, fillable), MAX_ORDER);
            if (amount <= 0) {
                continue;
            }
            flips.add(new Flip(product.id(), buyAt, sellAt, margin, percent, amount, margin * amount, product.dailyVolume()));
        }
        flips.sort(Comparator.comparingDouble(Flip::profit).reversed());
        return flips.size() > settings.limit() ? List.copyOf(flips.subList(0, settings.limit())) : flips;
    }
}
