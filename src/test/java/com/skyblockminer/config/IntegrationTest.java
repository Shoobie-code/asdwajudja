package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.game.Sidebar;
import com.skyblockminer.helpers.CoinTracker;
import com.skyblockminer.helpers.PriceHistory;
import com.skyblockminer.helpers.SessionHistory;
import com.skyblockminer.market.Bazaar;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Integration tests across components (config pipeline, sidebar->trackers, bazaar->history). */
class IntegrationTest {
    @Test
    void profileCodeRoundTripProducesNoDiff() {
        MinerConfig original = new MinerConfig();
        original.reach = 3.75;
        original.weaponSlot = 6;
        MinerConfig restored = MinerConfig.decode(MinerConfig.encode(original));
        restored.sanitize();
        assertTrue(MinerConfig.diff(original, restored).isEmpty(), () -> MinerConfig.diff(original, restored).toString());
    }

    @Test
    void sidebarPurseFeedsCoinsPerHour() {
        CoinTracker coins = new CoinTracker();
        coins.update(Sidebar.purse(List.of("Purse: 1,000")), 0L);
        coins.update(Sidebar.purse(List.of("Purse: 1,000,000")), 3_600_000L);
        assertEquals(999_000L, coins.gained());
        assertEquals(999_000.0, coins.perHour(), 1.0);
    }

    @Test
    void coinsSessionBecomesHistoryEntry() {
        CoinTracker coins = new CoinTracker();
        coins.update(0, 0L);
        coins.update(500_000, 1_800_000L);
        SessionHistory.Entry e = new SessionHistory.Entry(1L, "Mithril", 30.0, coins.gained());
        SessionHistory.Entry back = SessionHistory.parse(SessionHistory.format(e));
        assertEquals(500_000L, back.coins());
        assertEquals(1_000_000.0, back.coinsPerHour(), 1.0);
        assertSame(e, SessionHistory.best(List.of(e)));
    }

    @Test
    void bazaarReadingsFlowIntoPriceHistory() {
        Bazaar.Product p = new Bazaar.Product("MITHRIL_ORE", 2.0, 3.0, 7000, 7000, 1, 1);
        Bazaar bazaar = new Bazaar(Map.of("MITHRIL_ORE", p), 0L);
        PriceHistory history = new PriceHistory(10);
        history.add("MITHRIL_ORE", bazaar.instaBuy("MITHRIL_ORE"), bazaar.instaSell("MITHRIL_ORE"), 1L);
        PriceHistory.Stats s = history.stats("MITHRIL_ORE");
        assertEquals(2.0, s.latestSell(), 1e-9);
        assertEquals(1, s.samples());
    }
}
