package com.skyblockminer;

import java.util.Arrays;

/**
 * Open-addressing map from {@code long} keys to objects, without boxing. The pathfinder and its block cache
 * make hundreds of thousands of lookups per search, where {@code HashMap<Long, ...>} allocates on every call.
 */
final class LongMap<V> {
    private static final long FREE = Long.MIN_VALUE;
    private long[] keys;
    private Object[] values;
    private int size;
    private int mask;

    LongMap() {
        this(1024);
    }

    LongMap(int expected) {
        int capacity = Integer.highestOneBit(Math.max(16, expected * 2) - 1) << 1;
        this.keys = new long[capacity];
        Arrays.fill(this.keys, FREE);
        this.values = new Object[capacity];
        this.mask = capacity - 1;
    }

    int size() {
        return this.size;
    }

    @SuppressWarnings("unchecked")
    V get(long key) {
        for (int i = slot(key) & this.mask; ; i = i + 1 & this.mask) {
            long k = this.keys[i];
            if (k == key) {
                return (V) this.values[i];
            }
            if (k == FREE) {
                return null;
            }
        }
    }

    void put(long key, V value) {
        if (key == FREE) {
            throw new IllegalArgumentException("reserved key");
        }
        int i = slot(key) & this.mask;
        while (true) {
            long k = this.keys[i];
            if (k == key) {
                this.values[i] = value;
                return;
            }
            if (k == FREE) {
                this.keys[i] = key;
                this.values[i] = value;
                if (++this.size * 2 > this.keys.length) {
                    this.grow();
                }
                return;
            }
            i = i + 1 & this.mask;
        }
    }

    void clear() {
        Arrays.fill(this.keys, FREE);
        Arrays.fill(this.values, null);
        this.size = 0;
    }

    private void grow() {
        long[] oldKeys = this.keys;
        Object[] oldValues = this.values;
        this.keys = new long[oldKeys.length * 2];
        Arrays.fill(this.keys, FREE);
        this.values = new Object[oldKeys.length * 2];
        this.mask = this.keys.length - 1;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != FREE) {
                int j = slot(oldKeys[i]) & this.mask;
                while (this.keys[j] != FREE) {
                    j = j + 1 & this.mask;
                }
                this.keys[j] = oldKeys[i];
                this.values[j] = oldValues[i];
            }
        }
    }

    private static int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ h >>> 32);
    }

    /** Packs block coordinates (x and z within ±33M, y within ±2048) into one key. */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }
}
