package com.skyblockminer;

import com.skyblockminer.gui.Toasts;
import com.skyblockminer.market.AuctionFlipper;
import com.skyblockminer.market.Auctions;
import com.skyblockminer.market.Market;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

/**
 * Watches each auction scan for new BIN listings that are flips, opens them with {@code /viewauction} and,
 * when enabled, buys them. Before clicking it checks the auction screen: the buy button must be there and
 * the shown price must match the flip, so a changed or stale listing is never bought blindly.
 */
final class AuctionSniper {
    private static final Pattern PRICE = Pattern.compile("(?:Buy it now|Price):\\s*([\\d,]+)\\s*coins", Pattern.CASE_INSENSITIVE);
    private static final int BUY_SLOT = 31;
    private static final int CONFIRM_SLOT = 11;
    private static final long STEP_TIMEOUT = 3000L;
    private static final long BETWEEN_OPENS = 2500L;

    private enum State {
        IDLE,
        OPENING,
        CONFIRMING
    }

    private final MinerConfig config;
    private final Macro macro;
    private final ConcurrentLinkedQueue<AuctionFlipper.Flip> queue = new ConcurrentLinkedQueue<>();
    private final Set<String> handled = new HashSet<>();
    private State state = State.IDLE;
    private AuctionFlipper.Flip current;
    private long deadline;
    private long nextOpenAt;
    private int bought;

    AuctionSniper(MinerConfig config, Macro macro, Market market) {
        this.config = config;
        this.macro = macro;
        market.onNewListings(fresh -> this.onFresh(market, fresh));
    }

    int bought() {
        return this.bought;
    }

    /** Runs on an HTTP thread after each scan; only queues work for the game thread. */
    private void onFresh(Market market, List<Auctions.Listing> fresh) {
        if (!this.config.auctionScan || !this.config.auctionAutoOpen) {
            return;
        }
        Set<String> freshIds = new HashSet<>();
        fresh.forEach(listing -> freshIds.add(listing.uuid()));
        for (AuctionFlipper.Flip flip : AuctionFlipper.find(market.auctions(), MarketSettings.auctions(this.config))) {
            if (freshIds.contains(flip.listing().uuid())) {
                this.queue.add(flip);
            }
        }
    }

    void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        String title = Inv.screenTitle(mc);
        switch (this.state) {
            case IDLE -> {
                if (now < this.nextOpenAt || mc.player == null || mc.gui.screen() != null || this.macro.running()) {
                    return;
                }
                AuctionFlipper.Flip flip = this.queue.poll();
                if (this.handled.size() > 5000) {
                    this.handled.clear();
                }
                if (flip == null || !this.handled.add(flip.listing().uuid())) {
                    return;
                }
                this.current = flip;
                this.state = State.OPENING;
                this.deadline = now + STEP_TIMEOUT;
                this.nextOpenAt = now + BETWEEN_OPENS;
                mc.getConnection().sendCommand("viewauction " + flip.listing().uuid());
                Toasts.push("Auction flip", String.format(Locale.ROOT, "%s  +%,d", flip.listing().name(), flip.profit()), Toasts.Kind.INFO);
            }
            case OPENING -> {
                if (title != null && title.contains("BIN Auction View")) {
                    if (!this.config.auctionAutoBuy) {
                        this.state = State.IDLE;
                        return;
                    }
                    if (!this.priceMatches(mc)) {
                        this.giveUp("price on the auction does not match the flip");
                        return;
                    }
                    Inv.click(mc, BUY_SLOT);
                    this.state = State.CONFIRMING;
                    this.deadline = now + STEP_TIMEOUT;
                } else if (now > this.deadline) {
                    this.giveUp("the auction did not open (probably sold)");
                }
            }
            case CONFIRMING -> {
                if (title != null && title.contains("Confirm Purchase")) {
                    if (!Inv.name(Inv.slot(mc, CONFIRM_SLOT)).toLowerCase(Locale.ROOT).contains("confirm")) {
                        this.giveUp("no confirm button");
                        return;
                    }
                    Inv.click(mc, CONFIRM_SLOT);
                    this.bought++;
                    MinerMod.message(String.format(Locale.ROOT, "Bought %s for %,d (resell at %,d for about +%,d)",
                        this.current.listing().name(), this.current.listing().price(), this.current.resellAt(), this.current.profit()),
                        ChatFormatting.GREEN);
                    Toasts.push("Bought", this.current.listing().name(), Toasts.Kind.SUCCESS);
                    this.state = State.IDLE;
                } else if (now > this.deadline) {
                    this.giveUp("no confirmation screen (someone else bought it)");
                }
            }
        }
    }

    /** The BIN view's buy button exists and shows the price we expect (or less). */
    private boolean priceMatches(Minecraft mc) {
        if (!Inv.name(Inv.slot(mc, BUY_SLOT)).toLowerCase(Locale.ROOT).contains("buy item")) {
            return false;
        }
        long expected = this.current.listing().price();
        if (expected > this.config.auctionMaxPrice) {
            return false;
        }
        for (String line : Inv.lore(Inv.slot(mc, BUY_SLOT))) {
            Matcher matcher = PRICE.matcher(line);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1).replace(",", "")) <= expected;
            }
        }
        // Some layouts put the price on the item itself.
        for (String line : Inv.lore(Inv.slot(mc, 13))) {
            Matcher matcher = PRICE.matcher(line);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1).replace(",", "")) <= expected;
            }
        }
        return false;
    }

    private void giveUp(String why) {
        MinerMod.LOGGER.info("Auction sniper: skipped {} ({})", this.current == null ? "?" : this.current.listing().uuid(), why);
        this.state = State.IDLE;
        this.current = null;
    }
}
