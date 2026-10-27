package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.config.MinerConfig;
import org.junit.jupiter.api.Test;

class ChatFilterTest {
    private static MinerConfig on() {
        MinerConfig config = new MinerConfig();
        config.chatFilter = true;
        config.filterLobby = true;
        config.filterWatchdog = true;
        config.filterProfile = true;
        config.filterAbility = true;
        config.filterCombo = true;
        return config;
    }

    @Test
    void offHidesNothing() {
        MinerConfig config = on();
        config.chatFilter = false;
        assertFalse(ChatFilter.hide("Player joined the lobby!", config));
    }

    @Test
    void hidesSpamCategories() {
        MinerConfig config = on();
        assertTrue(ChatFilter.hide("Player joined the lobby!", config));
        assertTrue(ChatFilter.hide("[WATCHDOG ANNOUNCEMENT]", config));
        assertTrue(ChatFilter.hide("Watchdog has banned 1,234 players in the last 7 days.", config));
        assertTrue(ChatFilter.hide("You are playing on profile: Apple", config));
        assertTrue(ChatFilter.hide("You do not have enough mana to cast this!", config));
        assertTrue(ChatFilter.hide("+5 Kill Combo", config));
    }

    @Test
    void categoriesCanBeSwitchedOffIndividually() {
        MinerConfig config = on();
        config.filterLobby = false;
        assertFalse(ChatFilter.hide("Player joined the lobby!", config));
        assertTrue(ChatFilter.hide("+5 Kill Combo", config));
    }

    @Test
    void neverHidesMessagesAimedAtYou() {
        MinerConfig config = on();
        assertFalse(ChatFilter.hide("From Friend: Player joined the lobby!", config));
        assertFalse(ChatFilter.hide("Friend: is the macro running?", config));
        assertFalse(ChatFilter.hide("Someone said MACRO in the lobby! joined the lobby!", config));
    }

    @Test
    void customPhrasesMatchIgnoringCase() {
        MinerConfig config = on();
        config.filterCustom.add("  ");
        config.filterCustom.add("Bazaar Flip");
        assertTrue(ChatFilter.hide("you got a bazaar flip today", config));
        assertFalse(ChatFilter.hide("nothing to see", config));
    }

    @Test
    void blankLinesAreNotHidden() {
        assertFalse(ChatFilter.hide("   ", on()));
    }
}
