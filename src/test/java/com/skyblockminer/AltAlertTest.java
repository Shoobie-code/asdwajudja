package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AltAlertTest {
    private static MinerConfig config(String name) {
        MinerConfig config = new MinerConfig();
        config.altName = name;
        return config;
    }

    @Test
    void sendsDefaultMessageToAlt() {
        assertEquals("msg MyAlt yo im getting macro checked", AltAlert.command(config(" MyAlt ")));
    }

    @Test
    void needsAValidName() {
        assertNull(AltAlert.command(config("")));
        assertNull(AltAlert.command(config("bad name")));
        assertNull(AltAlert.command(config("way_too_long_for_minecraft")));
    }

    @Test
    void cleansAndCapsTheMessage() {
        MinerConfig config = config("Alt");
        config.altMessage = "§chelp\n  me ";
        assertEquals("msg Alt help me", AltAlert.command(config));
        config.altMessage = "   ";
        assertEquals("msg Alt " + AltAlert.DEFAULT_MESSAGE, AltAlert.command(config));
        config.altMessage = "x".repeat(400);
        assertTrue(AltAlert.command(config).length() <= 256);
    }

    @Test
    void sendsOncePerCooldown() {
        MinerConfig config = config("Alt");
        config.altCooldown = 5;
        AltAlert alert = new AltAlert();
        assertEquals("msg Alt yo im getting macro checked", alert.onCheck(config, 1_000L));
        assertNull(alert.onCheck(config, 1_000L + 4 * 60_000L));
        assertEquals("msg Alt yo im getting macro checked", alert.onCheck(config, 1_000L + 5 * 60_000L));
    }

    @Test
    void offOrUnsetSendsNothing() {
        MinerConfig config = config("Alt");
        config.altAlert = false;
        assertNull(new AltAlert().onCheck(config, 0L));
        AltAlert alert = new AltAlert();
        assertNull(alert.onCheck(config(""), 0L));
        // a check with no name set does not start the cooldown
        assertEquals("msg Alt yo im getting macro checked", alert.onCheck(config("Alt"), 1L));
    }
}
