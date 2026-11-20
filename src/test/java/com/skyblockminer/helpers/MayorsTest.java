package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class MayorsTest {
    @Test
    void findsKnownMayor() {
        assertEquals("Paul", Mayors.find(List.of("Calendar and Events", "Mayor Paul", "Perks...")));
        assertEquals("Diana", Mayors.find(List.of("Current: Diana")));
    }

    @Test
    void nullWhenNoMayorNamed() {
        assertNull(Mayors.find(List.of("Nothing here", "Election closed")));
        assertNull(Mayors.find(null));
    }

    @Test
    void doesNotMatchPartialWords() {
        // "Colessus" should not match mayor "Cole".
        assertNull(Mayors.find(List.of("Colessus the boss")));
    }
}
