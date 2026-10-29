package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigListsTest {
    @Test
    void addsTrimmedUniqueItems() {
        List<String> list = new ArrayList<>();
        assertTrue(ConfigLists.addUnique(list, "  rare drop  "));
        assertEquals(List.of("rare drop"), list);
        assertFalse(ConfigLists.addUnique(list, "rare drop"));
        assertFalse(ConfigLists.addUnique(list, "   "));
        assertFalse(ConfigLists.addUnique(list, null));
        assertEquals(1, list.size());
    }
}
