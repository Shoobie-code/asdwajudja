package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class RouteFlagsTest {
    @Test
    void readsOptionalFlagsAndKeepsPlainPoints() {
        String json = "[{\"x\": 1, \"y\": 2, \"z\": 3, \"walk\": true, \"wait\": 500}, {\"x\": 4, \"y\": 5, \"z\": 6}, [7, 8, 9]]";
        assertEquals(3, Routes.parse(json).size());
        Map<BlockPos, Routes.Flags> flags = Routes.parseFlags(json);
        assertEquals(1, flags.size());
        assertTrue(flags.values().iterator().next().walk());
        assertEquals(500, flags.values().iterator().next().waitMs());
    }

    @Test
    void defaultFlagsAreNotStored() {
        assertTrue(new Routes.Flags(false, 0).isDefault());
        assertTrue(Routes.parseFlags("[{\"x\": 1, \"y\": 2, \"z\": 3, \"walk\": false}]").isEmpty());
        assertTrue(Routes.toJson(List.of(), Map.of()).startsWith("["));
    }
}
