package com.skyblockminer.macro.mining;

import com.skyblockminer.config.MinerConfig;
import com.skyblockminer.pathing.WorldMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where every mineable block is in the chunks seen this visit, so macros can head for the nearest vein
 * instead of stopping when nothing is in reach. Filled by {@link WorldMap}'s chunk scan and kept current by
 * block updates. Positions are packed per chunk as {@code type << 17 | (y + 64) << 8 | z << 4 | x}.
 */
public final class OreIndex {
    /** Every block any mining mode can target. Indexes into this list are the stored type ids. */
    static final List<String> TRACKED;
    private static final Map<String, Integer> TYPE_OF = new HashMap<>();
    private static final int NONE = -1;

    static {
        Set<String> ids = new LinkedHashSet<>(Targets.mithril(true).keySet());
        ids.addAll(Targets.powder().keySet());
        ids.remove("minecraft:stone");
        MinerConfig all = new MinerConfig();
        all.gemstones = List.copyOf(Targets.GEMSTONES.keySet());
        for (String mode : List.of("gemstone", "ore", "tunnel")) {
            ids.addAll(Targets.costs(mode, all).keySet());
        }
        TRACKED = List.copyOf(ids);
        for (int i = 0; i < TRACKED.size(); i++) {
            TYPE_OF.put(TRACKED.get(i), i);
        }
    }

    private final Map<Long, int[]> chunks = new HashMap<>();
    private final IdentityHashMap<BlockState, Integer> types = new IdentityHashMap<>();
    private int total;

    public void clear() {
        this.chunks.clear();
        this.total = 0;
    }

    public int size() {
        return this.total;
    }

    /** Tracked type of a block state, or -1. Cached per state, so a full chunk scan stays cheap. */
    public int type(BlockState state) {
        Integer cached = this.types.get(state);
        if (cached == null) {
            if (state.isAir()) {
                cached = NONE;
            } else {
                cached = TYPE_OF.getOrDefault(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), NONE);
            }
            this.types.put(state, cached);
        }
        return cached;
    }

    public static int pack(int type, int x, int y, int z) {
        return type << 17 | (y + 64 & 511) << 8 | (z & 15) << 4 | x & 15;
    }

    // Unpack helpers (inverse of pack); x/z are the in-chunk low 4 bits, so add the chunk origin to get world coords.
    public static int unpackType(int packed) {
        return packed >>> 17;
    }

    public static int unpackLocalX(int packed) {
        return packed & 15;
    }

    public static int unpackY(int packed) {
        return ((packed >> 8) & 511) - 64;
    }

    public static int unpackLocalZ(int packed) {
        return (packed >> 4) & 15;
    }

    /** Name of a tracked type id, or null when out of range. */
    public static String typeName(int type) {
        return type >= 0 && type < TRACKED.size() ? TRACKED.get(type) : null;
    }

    public interface OreVisitor {
        void visit(int x, int y, int z, String type);
    }

    /** Visits known ore blocks within {@code radius} of (cx,cy,cz), up to {@code limit}, for rendering an ore ESP. */
    public void forEachNear(int cx, int cy, int cz, int radius, int limit, OreVisitor visitor) {
        int chunkRadius = (radius >> 4) + 1;
        int originChunkX = cx >> 4;
        int originChunkZ = cz >> 4;
        long radiusSq = (long) radius * radius;
        int count = 0;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int[] list = this.chunks.get(WorldMap.key(originChunkX + dx, originChunkZ + dz));
                if (list == null) {
                    continue;
                }
                int baseX = (originChunkX + dx) << 4;
                int baseZ = (originChunkZ + dz) << 4;
                for (int packed : list) {
                    int x = baseX + unpackLocalX(packed);
                    int y = unpackY(packed);
                    int z = baseZ + unpackLocalZ(packed);
                    long distSq = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy) + (long) (z - cz) * (z - cz);
                    if (distSq <= radiusSq) {
                        String name = typeName(unpackType(packed));
                        if (name != null) {
                            visitor.visit(x, y, z, name);
                            if (++count >= limit) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    /** Replaces everything known about one chunk. */
    public void putChunk(long key, int[] packed, int count) {
        int[] old = this.chunks.remove(key);
        if (old != null) {
            this.total -= old.length;
        }
        if (count > 0) {
            this.chunks.put(key, Arrays.copyOf(packed, count));
            this.total += count;
        }
    }

    /** Keeps the index right as blocks are mined or regenerate. */
    public void onBlockChanged(BlockPos pos, BlockState state) {
        long key = WorldMap.key(pos.getX() >> 4, pos.getZ() >> 4);
        int[] list = this.chunks.get(key);
        int location = pack(0, pos.getX(), pos.getY(), pos.getZ());
        int type = this.type(state);
        if (list != null) {
            for (int i = 0; i < list.length; i++) {
                if ((list[i] & 0x1FFFF) == location) {
                    if (type == NONE) {
                        int[] shorter = new int[list.length - 1];
                        System.arraycopy(list, 0, shorter, 0, i);
                        System.arraycopy(list, i + 1, shorter, i, list.length - i - 1);
                        this.replace(key, shorter);
                        this.total--;
                    } else {
                        list[i] = location | type << 17;
                    }
                    return;
                }
            }
        }
        if (type != NONE) {
            int[] longer = list == null ? new int[1] : Arrays.copyOf(list, list.length + 1);
            longer[longer.length - 1] = location | type << 17;
            this.chunks.put(key, longer);
            this.total++;
        }
    }

    private void replace(long key, int[] list) {
        if (list.length == 0) {
            this.chunks.remove(key);
        } else {
            this.chunks.put(key, list);
        }
    }

    /**
     * The best known block of a wanted type within {@code radius} blocks: closest first, with each type's
     * mining cost added as extra distance so cheap blocks (titanium, gems) win close calls.
     */
    BlockPos nearest(Vec3 from, Map<String, Integer> costs, double radius, Predicate<BlockPos> skip) {
        int[] wanted = new int[TRACKED.size()];
        boolean any = false;
        for (int i = 0; i < wanted.length; i++) {
            Integer cost = costs.get(TRACKED.get(i));
            wanted[i] = cost == null ? -1 : cost;
            any |= cost != null;
        }
        if (!any || this.chunks.isEmpty()) {
            return null;
        }

        int cx = (int) Math.floor(from.x) >> 4;
        int cz = (int) Math.floor(from.z) >> 4;
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        double radiusSq = radius * radius;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int[] list = this.chunks.get(WorldMap.key(cx + dx, cz + dz));
                if (list == null) {
                    continue;
                }
                int baseX = cx + dx << 4;
                int baseZ = cz + dz << 4;
                for (int entry : list) {
                    int cost = wanted[entry >>> 17];
                    if (cost < 0) {
                        continue;
                    }
                    int x = baseX + (entry & 15);
                    int z = baseZ + (entry >> 4 & 15);
                    int y = (entry >> 8 & 511) - 64;
                    double ddx = x + 0.5 - from.x;
                    double ddy = y + 0.5 - from.y;
                    double ddz = z + 0.5 - from.z;
                    double distSq = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (distSq > radiusSq) {
                        continue;
                    }
                    double score = Math.sqrt(distSq) + cost;
                    if (score < bestScore && !skip.test(probe.set(x, y, z))) {
                        bestScore = score;
                        best = probe.immutable();
                    }
                }
            }
        }
        return best;
    }
}
