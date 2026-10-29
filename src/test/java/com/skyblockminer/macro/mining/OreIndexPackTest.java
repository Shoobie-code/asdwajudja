package com.skyblockminer.macro.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OreIndexPackTest {
    @Test
    void packUnpackRoundTripsCoordinates() {
        int packed = OreIndex.pack(5, 11, 70, 3);
        assertEquals(5, OreIndex.unpackType(packed));
        assertEquals(11, OreIndex.unpackLocalX(packed));
        assertEquals(70, OreIndex.unpackY(packed));
        assertEquals(3, OreIndex.unpackLocalZ(packed));
    }

    @Test
    void handlesNegativeYAndChunkLocalWrap() {
        int packed = OreIndex.pack(0, 16 + 4, -40, 32 + 9); // world x/z keep only low 4 bits
        assertEquals(4, OreIndex.unpackLocalX(packed));
        assertEquals(9, OreIndex.unpackLocalZ(packed));
        assertEquals(-40, OreIndex.unpackY(packed));
    }

    @Test
    void typeNameOutOfRangeIsNull() {
        assertEquals(null, OreIndex.typeName(-1));
        assertEquals(null, OreIndex.typeName(99999));
    }
}
