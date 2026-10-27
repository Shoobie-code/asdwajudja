package com.skyblockminer.macro;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.config.MinerConfig;
import org.junit.jupiter.api.Test;

class WebhookEventsTest {
    @Test
    void sendsOnlyTheEventsTheUserKeeps() {
        MinerConfig config = new MinerConfig();
        config.discordEvents = "stop, pest";
        assertTrue(Webhook.wants(config, "stop"));
        assertTrue(Webhook.wants(config, "pest"));
        assertFalse(Webhook.wants(config, "rejoin"));
    }

    @Test
    void everyEventIsOnByDefault() {
        MinerConfig config = new MinerConfig();
        for (String event : Webhook.EVENTS) {
            assertTrue(Webhook.wants(config, event), event);
        }
    }
}
