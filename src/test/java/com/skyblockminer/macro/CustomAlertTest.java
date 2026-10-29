package com.skyblockminer.macro;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.helpers.ChatFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomAlertTest {
    // The custom chat-alert feature matches lines with the same helper the chat filter uses.
    @Test
    void matchesConfiguredPhraseIgnoringCase() {
        List<String> words = List.of("rare drop", "Mythic");
        assertTrue(ChatFilter.matchesCustom("RARE DROP! Something shiny", words));
        assertTrue(ChatFilter.matchesCustom("You found a mythic pet", words));
    }

    @Test
    void noMatchForUnrelatedLines() {
        assertFalse(ChatFilter.matchesCustom("just mining along", List.of("rare drop")));
        assertFalse(ChatFilter.matchesCustom("anything", List.of()));
    }
}
