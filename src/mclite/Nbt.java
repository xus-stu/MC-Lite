package mclite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 极简 NBT 读取：只支持读，够解析 level.dat / world_gen_settings.dat / 区块。
 *
 * <p>注意 TAG_End（0x00）后面没有名称字段，解析时必须先判断类型再读名称，
 * 否则会读飞（存档解析踩过这个坑）。</p>
 */
final class Nbt {

    private Nbt() {
    }

    static Map<String, Object> read(Path file) throws IOException {
        byte[] raw = Files.readAllBytes(file);
        return parse(raw);
    }

    static Map<String, Object> parse(byte[] raw) throws IOException {
        byte[] data = raw;
        if (raw.length > 1 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                data = in.readAllBytes();
            }
        }
        Cursor cursor = new Cursor(data);
        if (cursor.u8() != 10) {
            throw new IOException("不是 NBT 复合根");
        }
        cursor.skip(cursor.u16());
        return compound(cursor);
    }

    private static Map<String, Object> compound(Cursor cursor) {
        Map<String, Object> out = new LinkedHashMap<>();
        while (true) {
            int type = cursor.u8();
            if (type == 0) {
                return out;
            }
            String name = cursor.text(cursor.u16());
            out.put(name, payload(cursor, type));
        }
    }

    private static Object payload(Cursor cursor, int type) {
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
                cursor.skip(size);
                return null;
            }
            case 8:
                return cursor.text(cursor.u16());
            case 9: {
                int element = cursor.u8();
                int size = cursor.length(5);
                List<Object> list = new ArrayList<>(Math.min(size, 4096));
                for (int i = 0; i < size; i++) {
                    list.add(element == 0 ? null : payload(cursor, element));
                }
                return list;
            }
            case 10:
                return compound(cursor);
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
                cursor.skip(size * 8L);
                return null;
            }
            default:
                throw new IllegalStateException("未知 NBT 类型 " + type);
        }
    }

    /** 按路径取子对象；任一层不是复合就直接返回 null。 */
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

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    static List<?> list(Object value) {
        return value instanceof List ? (List<?>) value : null;
    }

    static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    static long num(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
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
            String value = new String(data, pos, length, java.nio.charset.StandardCharsets.UTF_8);
            pos += length;
            return value;
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
}
