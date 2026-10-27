package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DiscordEventsTest {
    @Test
    void sanitizeDropsUnknownEventsAndDuplicates() {
        MinerConfig config = new MinerConfig();
        config.discordEvents = "stop, bogus, stop,pest";
        config.sanitize();
        assertEquals("stop,pest", config.discordEvents);
    }

    @Test
    void missingListFallsBackToEveryEvent() {
        MinerConfig config = new MinerConfig();
        config.discordEvents = null;
        config.sanitize();
        assertEquals(String.join(",", com.skyblockminer.macro.Webhook.EVENTS), config.discordEvents);
    }
}
