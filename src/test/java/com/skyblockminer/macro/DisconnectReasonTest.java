package com.skyblockminer.macro;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisconnectReasonTest {
    @Test
    void recognisesBanScreens() {
        assertTrue(DisconnectReason.isBan("You are temporarily banned for 30d for Cheating. Appeal at hypixel.net/appeal"));
        assertTrue(DisconnectReason.isBan("Ban ID: #ABC123 ... https://www.hypixel.net/appeal"));
        assertTrue(DisconnectReason.isBan("You were banned for boosting"));
    }

    @Test
    void ordinaryDisconnectsAreNotBans() {
        assertFalse(DisconnectReason.isBan("Disconnected: Timed out"));
        assertFalse(DisconnectReason.isBan("Server closed"));
        assertFalse(DisconnectReason.isBan("You are not banned"));
        assertFalse(DisconnectReason.isBan(null));
        assertFalse(DisconnectReason.isBan("  "));
    }
}
