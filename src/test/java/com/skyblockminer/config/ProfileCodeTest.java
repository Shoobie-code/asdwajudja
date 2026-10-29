package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProfileCodeTest {
    @Test
    void roundTripsSettingsThroughACode() {
        MinerConfig config = new MinerConfig();
        config.reach = 3.25;
        config.weaponSlot = 5;
        config.farmWarpCommand = "warp garden";

        String code = MinerConfig.encode(config);
        assertTrue(code.startsWith(MinerConfig.CODE_PREFIX));

        MinerConfig back = MinerConfig.decode(code);
        assertNotNull(back);
        assertEquals(3.25, back.reach, 1e-9);
        assertEquals(5, back.weaponSlot);
        assertEquals("warp garden", back.farmWarpCommand);
    }

    @Test
    void codeNeverCarriesTheWebhook() {
        MinerConfig config = new MinerConfig();
        config.webhookUrl = "https://discord.com/api/webhooks/1/secret-token";
        String code = MinerConfig.encode(config);
        assertEquals("", MinerConfig.decode(code).webhookUrl);
    }

    @Test
    void importKeepsTheLocalWebhook() {
        MinerConfig source = new MinerConfig();
        source.reach = 2.0;
        String code = MinerConfig.encode(source);

        MinerConfig local = new MinerConfig();
        local.webhookUrl = "https://discord.com/api/webhooks/1/keepme";
        assertTrue(local.applyCode(code));
        assertEquals(2.0, local.reach, 1e-9);
        assertEquals("https://discord.com/api/webhooks/1/keepme", local.webhookUrl);
    }

    @Test
    void rejectsGarbageCodes() {
        assertNull(MinerConfig.decode(null));
        assertNull(MinerConfig.decode(""));
        assertNull(MinerConfig.decode("not a code"));
        assertNull(MinerConfig.decode(MinerConfig.CODE_PREFIX + "!!!not-base64!!!"));
        assertFalse(new MinerConfig().applyCode("nope"));
    }
}
