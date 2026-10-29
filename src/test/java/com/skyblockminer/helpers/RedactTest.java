package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedactTest {
    @Test
    void removesDiscordWebhookUrls() {
        String text = "sending to https://discord.com/api/webhooks/123/abcSECRETtoken now";
        String scrubbed = Redact.scrub(text);
        assertFalse(scrubbed.contains("abcSECRETtoken"));
        assertTrue(scrubbed.contains("<webhook removed>"));
    }

    @Test
    void leavesOrdinaryTextAlone() {
        assertEquals("macro started", Redact.scrub("macro started"));
    }

    @Test
    void nullInNullOut() {
        assertNull(Redact.scrub(null));
    }
}
