package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.config.MinerConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class HelpersTest {
    @Test
    void chatFilterHidesSpamButNeverMessagesToYou() {
        MinerConfig config = new MinerConfig();
        config.chatFilter = true;
        assertTrue(ChatFilter.hide("[MVP+] Someone joined the lobby!", config));
        assertTrue(ChatFilter.hide(" >>> [MVP++] Someone joined the lobby! <<<", config));
        assertTrue(ChatFilter.hide("[WATCHDOG ANNOUNCEMENT]", config));
        assertTrue(ChatFilter.hide("You do not have enough mana to do this!", config));
        assertTrue(ChatFilter.hide("+5 Kill Combo +3% ✯ Magic Find", config));
        assertTrue(ChatFilter.hide("You are playing on profile: Mango", config));
        assertFalse(ChatFilter.hide("From [MVP+] Someone: hey you there?", config));
        assertFalse(ChatFilter.hide("[MVP+] Someone joined the lobby! are you macroing", config));
        assertFalse(ChatFilter.hide("RARE DROP! Enchanted Diamond", config));
        config.filterCustom = List.of("rare drop");
        assertTrue(ChatFilter.hide("RARE DROP! Enchanted Diamond", config));
        config.chatFilter = false;
        assertFalse(ChatFilter.hide("[WATCHDOG ANNOUNCEMENT]", config));
    }

    @Test
    void pestWidgetIsParsed() {
        Helpers.Pests pests = Helpers.parsePests(List.of("Area: Garden", "Pests:", "Alive: 5", "Infested Plots: 3, 12", "Spray: None"));
        assertEquals(5, pests.alive());
        assertEquals("3, 12", pests.plots());
        Helpers.Pests none = Helpers.parsePests(List.of("Area: Garden", "Alive: 0", "Plots: None"));
        assertEquals(0, none.alive());
        assertEquals("", none.plots());
        assertEquals(-1, Helpers.parsePests(List.of("Area: Hub")).alive());
    }
}
