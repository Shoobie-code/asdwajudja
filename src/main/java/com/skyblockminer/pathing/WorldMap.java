package com.skyblockminer.pathing;

import com.skyblockminer.MinerMod;
import com.skyblockminer.game.TabList;
import com.skyblockminer.macro.mining.OreIndex;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldMap {
    static final byte EMPTY_CODE = 1;
    static final byte HAZARD_CODE = 2;
    private static final int MIN_SECTION = -4;
    private static final int SECTIONS = 24;
    private static final int MAGIC = 1397571920;
    private static final int VERSION = 1;
    private static final int V5_MAGIC = 1555033003;
    private static final int SECTIONS_PER_TICK = 24;
    private static final long SCAN_NANOS = 2_000_000L;
    private static final long SETTLE_MS = 2500L;
    private static final long AUTOSAVE_MS = 300000L;
    private static final double[][] DECODED = new double[256][];
    private static final int V5_PASSABLE = 1;
    private static final int V5_SOLID = 2;
    private static final int V5_FLUID = 16;
    private static final int V5_SLAB_BOTTOM = 32;
    private static final int V5_SLAB_TOP = 64;
    private static final int V5_FENCE = 128;
    private Map<Long, byte[][]> chunks = new HashMap<>();
    private final OreIndex ores = new OreIndex();
    private int[] oreScratch = new int[4096];
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private final IdentityHashMap<BlockState, Byte> codes = new IdentityHashMap<>();
    private ClientLevel level;
    private long levelSince;
    private String area;
    private CompletableFuture<Map<Long, byte[][]>> loading;
    private boolean imported;
    private boolean dirty;
    private long savedAt;

    public String area() {
        return this.area;
    }

    /** Known mineable blocks in the chunks seen since arriving in this world. */
    public OreIndex ores() {
        return this.ores;
    }

    public int chunkCount() {
        return this.chunks.size();
    }

    public boolean loading() {
        return this.loading != null;
    }

    public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        if (level == this.level) {
            this.pending.add(key(chunk.getPos().x(), chunk.getPos().z()));
        }
    }

    public void onBlockChanged(ClientLevel level, BlockPos pos, BlockState state) {
        if (level == this.level) {
            this.ores.onBlockChanged(pos, state);
        }
        if (level == this.level && this.area != null) {
            byte[][] chunk = this.chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
            if (chunk != null) {
                int index = (pos.getY() >> 4) - MIN_SECTION;
                if (index >= 0 && index < SECTIONS) {
                    byte code = this.code(state);
                    if (chunk[index] == null) {
                        if (code == EMPTY_CODE) {
                            return;
                        }

                        chunk[index] = new byte[4096];
                        Arrays.fill(chunk[index], EMPTY_CODE);
                    }

                    chunk[index][(pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | pos.getX() & 15] = code;
                    this.dirty = true;
                }
            }
        }
    }

    public void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (mc.level != this.level) {
            this.saveAsync();
            this.chunks = new HashMap<>();
            this.ores.clear();
            this.area = null;
            this.loading = null;
            this.level = mc.level;
            this.levelSince = now;
            this.pending.clear();
        } else if (this.level != null) {
            if (this.area == null) {
                if (now - this.levelSince >= SETTLE_MS) {
                    String found = areaName(mc);
                    if (found != null) {
                        this.area = found;
                        Path own = file(this.area);
                        Path v5 = FabricLoader.getInstance().getGameDir().resolve("pathfinder_cache").resolve("map_" + fileName(this.area) + ".bin");
                        this.imported = !Files.exists(own) && Files.exists(v5);
                        Path source = this.imported ? v5 : own;
                        boolean fromV5 = this.imported;
                        this.loading = CompletableFuture.supplyAsync(() -> {
                            try {
                                if (!Files.exists(source)) {
                                    return new HashMap<>();
                                }
                                return fromV5 ? readV5(source) : read(source);
                            } catch (RuntimeException | IOException e) {
                                MinerMod.LOGGER.warn("Could not read map {}", source, e);
                                return new HashMap<>();
                            }
                        });
                        int radius = mc.options.getEffectiveRenderDistance() + 1;
                        if (mc.player != null) {
                            int cx = mc.player.blockPosition().getX() >> 4;
                            int cz = mc.player.blockPosition().getZ() >> 4;

                            for (int dx = -radius; dx <= radius; dx++) {
                                for (int dz = -radius; dz <= radius; dz++) {
                                    if (this.level.hasChunk(cx + dx, cz + dz)) {
                                        this.pending.add(key(cx + dx, cz + dz));
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (this.loading != null) {
                    if (!this.loading.isDone()) {
                        return;
                    }

                    Map<Long, byte[][]> saved = this.loading.join();
                    this.loading = null;
                    saved.putAll(this.chunks);
                    this.chunks = saved;
                    if (this.imported) {
                        this.dirty = true;
                        MinerMod.LOGGER.info("Imported V5's map of {} ({} chunks)", this.area, this.chunks.size());
                    }
                }

                // Chunk scans are spread over ticks by time, so loading a new area never drops frames.
                int budget = SECTIONS_PER_TICK * 4;
                long deadline = System.nanoTime() + SCAN_NANOS;
                Iterator<Long> it = this.pending.iterator();

                while (it.hasNext() && budget > 0 && System.nanoTime() < deadline) {
                    long key = it.next();
                    it.remove();
                    int cx = (int)(key >> 32);
                    int cz = (int)key;
                    if (this.level.hasChunk(cx, cz)) {
                        budget -= this.record(this.level.getChunk(cx, cz));
                    }
                }

                if (this.dirty && now - this.savedAt > AUTOSAVE_MS) {
                    this.saveAsync();
                }
            }
        }
    }

    private int record(LevelChunk chunk) {
        byte[][] sections = new byte[SECTIONS][];
        LevelChunkSection[] levelSections = chunk.getSections();
        int minSection = chunk.getMinSectionY();
        int read = 0;
        int oreCount = 0;

        for (int i = 0; i < levelSections.length; i++) {
            int index = minSection + i - MIN_SECTION;
            LevelChunkSection section = levelSections[i];
            if (index >= 0 && index < SECTIONS && section != null && !section.hasOnlyAir()) {
                byte[] blocks = new byte[4096];
                boolean any = false;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            byte code = this.code(state);
                            blocks[y << 8 | z << 4 | x] = code;
                            any |= code != EMPTY_CODE;
                            int type = this.ores.type(state);
                            if (type >= 0) {
                                if (oreCount == this.oreScratch.length) {
                                    this.oreScratch = Arrays.copyOf(this.oreScratch, oreCount * 2);
                                }
                                this.oreScratch[oreCount++] = OreIndex.pack(type, x, (minSection + i << 4) + y, z);
                            }
                        }
                    }
                }

                if (any) {
                    sections[index] = blocks;
                }

                read++;
            }
        }

        this.chunks.put(key(chunk.getPos().x(), chunk.getPos().z()), sections);
        this.ores.putChunk(key(chunk.getPos().x(), chunk.getPos().z()), this.oreScratch, oreCount);
        this.dirty = true;
        return Math.max(1, read);
    }

    private byte code(BlockState state) {
        Byte cached = this.codes.get(state);
        if (cached != null) {
            return cached;
        } else {
            byte code;
            if (state.isAir()) {
                code = EMPTY_CODE;
            } else if (Pathfinder.hazard(state)) {
                code = HAZARD_CODE;
            } else if (Pathfinder.kind(state) == Pathfinder.WATER) {
                code = 3;
            } else if (Pathfinder.kind(state) == Pathfinder.CLIMB) {
                code = 4;
            } else if (Pathfinder.kind(state) == Pathfinder.SOFT) {
                code = 5;
            } else {
                VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                code = shape.isEmpty() ? EMPTY_CODE : encode(shape.min(Axis.Y), shape.max(Axis.Y));
            }

            this.codes.put(state, code);
            return code;
        }
    }

    static byte encode(double minY, double maxY) {
        int min = Math.max(0, Math.min(7, (int)Math.floor(minY * 8.0 + 1.0E-6)));
        int max = Math.max(1, Math.min(15, (int)Math.ceil(maxY * 8.0 - 1.0E-6)));
        return (byte)(128 | min << 4 | max);
    }

    static double[] decode(byte code) {
        double[] shape = DECODED[code & 255];
        return shape == null ? Pathfinder.EMPTY : shape;
    }

    double[] shape(int x, int y, int z) {
        return shape(this.chunks, x, y, z);
    }

    static double[] shape(Map<Long, byte[][]> chunks, int x, int y, int z) {
        byte[][] chunk = chunks.get(key(x >> 4, z >> 4));
        if (chunk == null) {
            return null;
        } else {
            int index = (y >> 4) - MIN_SECTION;
            return index >= 0 && index < SECTIONS && chunk[index] != null ? decode(chunk[index][(y & 15) << 8 | (z & 15) << 4 | x & 15]) : Pathfinder.EMPTY;
        }
    }

    Pathfinder.Shapes shapes(ClientLevel level) {
        WorldMap.Live live = new WorldMap.Live(level);
        WorldMap.Reader known = new WorldMap.Reader(level == this.level && this.area != null ? this.chunks : Map.of());
        return (x, y, z) -> {
            double[] shape = live.get(x, y, z);
            if (shape == null) {
                shape = known.get(x, y, z);
            }

            return shape == null ? Pathfinder.EMPTY : shape;
        };
    }

    public static long key(int chunkX, int chunkZ) {
        return (long)chunkX << 32 | chunkZ & 4294967295L;
    }

    static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyblockminer").resolve("maps");
    }

    static String fileName(String area) {
        return area.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static Path file(String area) {
        return dir().resolve(fileName(area) + ".bin");
    }

    private static String areaName(Minecraft mc) {
        String area = TabList.area(mc);
        if (area != null) {
            return area;
        } else {
            return mc.getSingleplayerServer() != null ? "singleplayer-" + mc.getSingleplayerServer().getWorldData().getLevelName() : null;
        }
    }

    void saveAsync() {
        if (this.area != null && this.dirty && this.loading == null) {
            Path target = file(this.area);
            Map<Long, byte[][]> snapshot = new HashMap<>(this.chunks);
            this.dirty = false;
            this.savedAt = System.currentTimeMillis();
            CompletableFuture.runAsync(() -> {
                try {
                    write(target, snapshot);
                } catch (IOException e) {
                    MinerMod.LOGGER.warn("Could not save map {}", target, e);
                }
            });
        }
    }

    public void saveNow() {
        if (this.area != null && this.dirty && this.loading == null) {
            try {
                write(file(this.area), this.chunks);
                this.dirty = false;
            } catch (IOException e) {
                MinerMod.LOGGER.warn("Could not save map {}", this.area, e);
            }
        }
    }

    public void clear() {
        this.chunks = new HashMap<>();
        this.dirty = false;
        if (this.area != null) {
            try {
                Files.deleteIfExists(file(this.area));
            } catch (IOException e) {
            }
        }
    }

    static synchronized void write(Path file, Map<Long, byte[][]> chunks) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp), 65536)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(chunks.size());

            for (Entry<Long, byte[][]> entry : chunks.entrySet()) {
                byte[][] sections = entry.getValue();
                int mask = 0;

                for (int i = 0; i < SECTIONS; i++) {
                    if (sections[i] != null) {
                        mask |= 1 << i;
                    }
                }

                out.writeLong(entry.getKey());
                out.writeInt(mask);

                for (int ix = 0; ix < SECTIONS; ix++) {
                    if (sections[ix] != null) {
                        out.write(sections[ix]);
                    }
                }
            }
        }

        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    static Map<Long, byte[][]> read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file), 65536)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IOException("not a Skyblock Miner map");
            }

            int count = in.readInt();
            Map<Long, byte[][]> chunks = new HashMap<>(count * 2);

            for (int c = 0; c < count; c++) {
                long key = in.readLong();
                int mask = in.readInt();
                byte[][] sections = new byte[SECTIONS][];

                for (int i = 0; i < SECTIONS; i++) {
                    if ((mask & 1 << i) != 0) {
                        sections[i] = new byte[4096];
                        in.readFully(sections[i]);
                    }
                }

                chunks.put(key, sections);
            }

            return chunks;
        }
    }

    static Map<Long, byte[][]> readV5(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file), 65536)))) {
            if (in.readInt() != V5_MAGIC) {
                throw new IOException("not a V5 map");
            }

            int version = in.readInt();
            if (version != 5) {
                throw new IOException("unsupported V5 map version " + version);
            }

            int count = in.readInt();
            Map<Long, byte[][]> chunks = new HashMap<>(count * 2);
            byte[] raw = new byte[8192];

            for (int c = 0; c < count; c++) {
                long key = in.readLong();
                int minY = in.readInt();
                int maxY = in.readInt();
                long mask = in.readLong();
                int sectionCount = maxY - minY + 15 >> 4;
                byte[][] sections = new byte[SECTIONS][];

                for (int i = 0; i < sectionCount && i < 64; i++) {
                    if ((mask & 1L << i) != 0L) {
                        in.readFully(raw);
                        int index = (minY >> 4) + i - MIN_SECTION;
                        if (index >= 0 && index < SECTIONS) {
                            byte[] blocks = new byte[4096];
                            boolean any = false;

                            for (int b = 0; b < 4096; b++) {
                                int flags = (raw[b * 2] & 255) << 8 | raw[b * 2 + 1] & 255;
                                blocks[b] = fromV5(flags);
                                any |= blocks[b] != EMPTY_CODE;
                            }

                            if (any) {
                                sections[index] = blocks;
                            }
                        }
                    }
                }

                chunks.put(key, sections);
            }

            return chunks;
        }
    }

    static byte fromV5(int flags) {
        if ((flags & V5_FLUID) != 0) {
            return HAZARD_CODE;
        } else if ((flags & V5_SOLID) == 0) {
            return EMPTY_CODE;
        } else if ((flags & V5_SLAB_BOTTOM) != 0) {
            return encode(0.0, 0.5);
        } else if ((flags & V5_SLAB_TOP) != 0) {
            return encode(0.5, 1.0);
        } else if ((flags & V5_FENCE) != 0) {
            return encode(0.0, 1.5);
        } else {
            return (flags & V5_PASSABLE) != 0 ? EMPTY_CODE : encode(0.0, 1.0);
        }
    }

    static {
        DECODED[EMPTY_CODE] = Pathfinder.EMPTY;
        DECODED[HAZARD_CODE] = Pathfinder.HAZARD;
        DECODED[3] = Pathfinder.WATER;
        DECODED[4] = Pathfinder.CLIMB;
        DECODED[5] = Pathfinder.SOFT;

        for (int code = 128; code < 256; code++) {
            DECODED[code] = new double[]{(code >> 4 & 7) / 8.0, (code & 15) / 8.0};
        }
    }

    private final class Live {
        private final ClientLevel level;
        private long lastKey;
        private LevelChunk last;

        Live(ClientLevel level) {
            this.lastKey = Long.MIN_VALUE;
            this.level = level;
        }

        double[] get(int x, int y, int z) {
            long key = WorldMap.key(x >> 4, z >> 4);
            if (key != this.lastKey) {
                this.lastKey = key;
                this.last = this.level.hasChunk(x >> 4, z >> 4) ? this.level.getChunk(x >> 4, z >> 4) : null;
            }

            if (this.last == null) {
                return null;
            } else {
                int index = this.last.getSectionIndex(y);
                if (index >= 0 && index < this.last.getSectionsCount()) {
                    LevelChunkSection section = this.last.getSection(index);
                    return section.hasOnlyAir() ? Pathfinder.EMPTY : WorldMap.decode(WorldMap.this.code(section.getBlockState(x & 15, y & 15, z & 15)));
                } else {
                    return Pathfinder.EMPTY;
                }
            }
        }
    }

    private static final class Reader {
        private final Map<Long, byte[][]> chunks;
        private long lastKey = Long.MIN_VALUE;
        private byte[][] last;

        Reader(Map<Long, byte[][]> chunks) {
            this.chunks = chunks;
        }

        double[] get(int x, int y, int z) {
            long key = WorldMap.key(x >> 4, z >> 4);
            if (key != this.lastKey) {
                this.last = this.chunks.get(key);
                this.lastKey = key;
            }

            if (this.last == null) {
                return null;
            } else {
                int index = (y >> 4) - MIN_SECTION;
                return index >= 0 && index < SECTIONS && this.last[index] != null
                    ? WorldMap.decode(this.last[index][(y & 15) << 8 | (z & 15) << 4 | x & 15])
                    : Pathfinder.EMPTY;
            }
        }
    }
}
