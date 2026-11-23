package mclite.editor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 只读 NBT + 定点改写。
 *
 * <p>存档的 level.dat 里有各种数组/复合结构，整树重写容易丢数据。
 * 这里只解析、记录目标标签的字节偏移，保存时就地改那几个数值，
 * 然后重新 gzip，最大程度保持原文件不变。</p>
 */
final class Nbt {

    private Nbt() {
    }

    static byte[] gunzip(byte[] raw) throws IOException {
        if (raw.length > 1 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                return in.readAllBytes();
            }
        }
        throw new IOException("level.dat 不是 gzip");
    }

    static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(data);
        }
        return out.toByteArray();
    }

    /** 解析结果：可读的树 + 目标标签的偏移。 */
    record Parsed(Map<String, Object> root, Map<String, int[]> offsets) {
    }

    static Parsed parse(byte[] data) throws IOException {
        Map<String, int[]> offsets = new LinkedHashMap<>();
        Cursor cursor = new Cursor(data);
        if (cursor.u8() != 10) {
            throw new IOException("不是 NBT 复合根");
        }
        cursor.skip(cursor.u16());
        Map<String, Object> root = compound(cursor, "", offsets);
        return new Parsed(root, offsets);
    }

    private static Map<String, Object> compound(Cursor cursor, String prefix,
                                                Map<String, int[]> offsets) {
        Map<String, Object> out = new LinkedHashMap<>();
        while (true) {
            int type = cursor.u8();
            if (type == 0) {
                return out;
            }
            String name = cursor.text(cursor.u16());
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            if (isTarget(path)) {
                offsets.put(path, new int[]{type, cursor.pos});
            }
            out.put(name, payload(cursor, type, path, offsets));
        }
    }

    private static boolean isTarget(String path) {
        return switch (path) {
            case "Data.GameType", "Data.allowCommands", "Data.difficulty_settings.hardcore",
                 "Data.Time", "Data.DayTime", "Data.spawn.pos", "Data.spawn.angle" -> true;
            default -> false;
        };
    }

    private static Object payload(Cursor cursor, int type, String path, Map<String, int[]> offsets) {
        switch (type) {
            case 1:
                return (long) cursor.u8();
            case 2:
                return (long) (short) cursor.u16();
            case 3:
                return (long) cursor.i32();
            case 4:
                return cursor.i64();
            case 5:
                return Float.intBitsToFloat(cursor.i32());
            case 6:
                return Double.longBitsToDouble(cursor.i64());
            case 7: {
                int size = cursor.length(1);
                byte[] bytes = cursor.bytes(size);
                return bytes;
            }
            case 8:
                return cursor.text(cursor.u16());
            case 9: {
                int element = cursor.u8();
                int size = cursor.length(5);
                List<Object> list = new ArrayList<>(Math.min(size, 1024));
                for (int i = 0; i < size; i++) {
                    list.add(element == 0 ? null : payload(cursor, element, path + "[]", offsets));
                }
                return list;
            }
            case 10:
                return compound(cursor, path, offsets);
            case 11: {
                int size = cursor.length(4);
                int[] values = new int[size];
                for (int i = 0; i < size; i++) {
                    values[i] = cursor.i32();
                }
                return values;
            }
            case 12: {
                int size = cursor.length(8);
                long[] values = new long[size];
                for (int i = 0; i < size; i++) {
                    values[i] = cursor.i64();
                }
                return values;
            }
            default:
                throw new IllegalStateException("未知 NBT 类型 " + type);
        }
    }

    static Map<String, Object> map(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = value instanceof Map ? (Map<String, Object>) value : null;
        return map;
    }

    static Object at(Object root, String... path) {
        Object current = root;
        for (String key : path) {
            Map<String, Object> map = map(current);
            if (map == null) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    static long num(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    static List<?> list(Object value) {
        return value instanceof List ? (List<?>) value : null;
    }

    static void putInt(byte[] data, Map<String, int[]> offsets, String path, int value) {
        int[] at = offsets.get(path);
        if (at == null || at[0] != 3) {
            return;
        }
        writeInt(data, at[1], value);
    }

    static void putByte(byte[] data, Map<String, int[]> offsets, String path, int value) {
        int[] at = offsets.get(path);
        if (at == null || at[0] != 1) {
            return;
        }
        data[at[1]] = (byte) value;
    }

    static void putLong(byte[] data, Map<String, int[]> offsets, String path, long value) {
        int[] at = offsets.get(path);
        if (at == null || at[0] != 4) {
            return;
        }
        writeLong(data, at[1], value);
    }

    static void putIntArray(byte[] data, Map<String, int[]> offsets, String path, int[] values) {
        int[] at = offsets.get(path);
        if (at == null || at[0] != 11) {
            return;
        }
        int size = readInt(data, at[1]);
        int start = at[1] + 4;
        for (int i = 0; i < values.length && i < size; i++) {
            writeInt(data, start + i * 4, values[i]);
        }
    }

    static void putFloat(byte[] data, Map<String, int[]> offsets, String path, float value) {
        int[] at = offsets.get(path);
        if (at == null || at[0] != 5) {
            return;
        }
        writeInt(data, at[1], Float.floatToIntBits(value));
    }

    private static int readInt(byte[] data, int at) {
        return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16)
                | ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
    }

    private static void writeInt(byte[] data, int at, int value) {
        data[at] = (byte) (value >>> 24);
        data[at + 1] = (byte) (value >>> 16);
        data[at + 2] = (byte) (value >>> 8);
        data[at + 3] = (byte) value;
    }

    private static void writeLong(byte[] data, int at, long value) {
        writeInt(data, at, (int) (value >>> 32));
        writeInt(data, at + 4, (int) value);
    }

    private static final class Cursor {
        private final byte[] data;
        private int pos;

        Cursor(byte[] data) {
            this.data = data;
        }

        int u8() {
            need(1);
            return data[pos++] & 0xFF;
        }

        int u16() {
            need(2);
            int value = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            return value;
        }

        int i32() {
            need(4);
            int value = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                    | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            pos += 4;
            return value;
        }

        long i64() {
            long high = i32() & 0xFFFFFFFFL;
            long low = i32() & 0xFFFFFFFFL;
            return (high << 32) | low;
        }

        String text(int length) {
            need(length);
            String value = new String(data, pos, length, StandardCharsets.UTF_8);
            pos += length;
            return value;
        }

        byte[] bytes(int length) {
            need(length);
            byte[] out = java.util.Arrays.copyOfRange(data, pos, pos + length);
            pos += length;
            return out;
        }

        int length(int bytesPerElement) {
            int length = i32();
            if (length < 0 || (long) length * bytesPerElement > data.length - pos) {
                throw new IllegalStateException("NBT 长度越界");
            }
            return length;
        }

        void skip(long count) {
            if (count < 0 || count > data.length - pos) {
                throw new IllegalStateException("NBT 越界");
            }
            pos += (int) count;
        }

        private void need(int count) {
            if (count < 0 || pos + count > data.length) {
                throw new IllegalStateException("NBT 数据不完整");
            }
        }
    }

    /** 供 LevelFile 写文件用。 */
    static void write(Path path, byte[] gzipped) throws IOException {
        Files.write(path, gzipped);
    }
}
