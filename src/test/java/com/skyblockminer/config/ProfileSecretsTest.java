package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ProfileSecretsTest {
    @Test
    void sharedProfilesDropTheWebhookButKeepTheRest() {
        MinerConfig config = new MinerConfig();
        config.webhookUrl = "https://discord.com/api/webhooks/123/secret-token";
        config.reach = 3.5;

        var shared = MinerConfig.shareable(config);

        assertEquals("", shared.get(MinerConfig.WEBHOOK_FIELD).getAsString());
        assertEquals(3.5, shared.get("reach").getAsDouble(), 1e-9);
        assertFalse(shared.toString().contains("secret-token"));
    }
}
