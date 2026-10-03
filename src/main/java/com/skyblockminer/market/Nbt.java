package com.skyblockminer.market;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal reader for the binary NBT format, used to decode the base64 + gzip {@code item_bytes} that the
 * auction API returns. Compounds become {@code Map<String, Object>}, lists become {@code List<Object>},
 * numbers their boxed Java types and arrays their primitive arrays.
 */
public final class Nbt {
    private static final int MAX_DEPTH = 64;

    private Nbt() {
    }

    /** Decodes {@code base64(gzip(nbt))} and returns the root compound. */
    public static Map<String, Object> readBase64(String base64) throws IOException {
        return readGzip(Base64.getDecoder().decode(base64));
    }

    public static Map<String, Object> readGzip(byte[] gzip) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            return read(new DataInputStream(in));
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> read(DataInputStream in) throws IOException {
        int type = in.readByte();
        if (type != 10) {
            throw new IOException("root is not a compound (type " + type + ")");
        }
        in.readUTF();
        return (Map<String, Object>) payload(in, 10, 0);
    }

    private static Object payload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nested too deeply");
        }
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] bytes = new byte[length(in)];
                in.readFully(bytes);
                yield bytes;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                int elementType = in.readByte();
                int size = length(in);
                List<Object> list = new ArrayList<>(Math.min(size, 1024));
                for (int i = 0; i < size; i++) {
                    list.add(payload(in, elementType, depth + 1));
                }
                yield list;
            }
            case 10 -> {
                Map<String, Object> compound = new LinkedHashMap<>();
                for (int child = in.readByte(); child != 0; child = in.readByte()) {
                    String name = in.readUTF();
                    compound.put(name, payload(in, child, depth + 1));
                }
                yield compound;
            }
            case 11 -> {
                int[] ints = new int[length(in)];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = in.readInt();
                }
                yield ints;
            }
            case 12 -> {
                long[] longs = new long[length(in)];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = in.readLong();
                }
                yield longs;
            }
            default -> throw new IOException("unknown NBT tag type " + type);
        };
    }

    private static int length(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 1 << 24) {
            throw new IOException("bad NBT length " + length);
        }
        return length;
    }

    /** Follows a path of compound keys ({@code "tag", "ExtraAttributes", "id"}); null when any step is missing. */
    @SuppressWarnings("unchecked")
    public static Object path(Map<String, Object> root, String... keys) {
        Object at = root;
        for (String key : keys) {
            if (!(at instanceof Map<?, ?> map)) {
                return null;
            }
            at = ((Map<String, Object>) map).get(key);
        }
        return at;
    }
}
