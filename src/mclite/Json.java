package mclite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON 内容为空");
        }
        Parser parser = new Parser(text);
        Object value = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("JSON 末尾存在多余内容");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return Collections.emptyList();
    }

    public static String str(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value instanceof String s ? s : null;
    }

    public static String str(Map<String, Object> object, String key, String fallback) {
        String value = str(object, key);
        return value == null ? fallback : value;
    }

    public static long lng(Map<String, Object> object, String key, long fallback) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    public static int integer(Map<String, Object> object, String key, int fallback) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, lng(object, key, fallback)));
    }

    public static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    public static boolean has(Map<String, Object> object, String key) {
        return object != null && object.containsKey(key);
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 256;
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private boolean atEnd() {
            return index >= text.length();
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("JSON 嵌套层数过深");
            }
            skipWhitespace();
            if (atEnd()) {
                throw error("JSON 意外结束");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> parseObject(depth + 1);
                case '[' -> parseArray(depth + 1);
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw error("无法识别的 JSON 字符 '" + c + "'");
                }
            };
        }

        private Map<String, Object> parseObject(int depth) {
            expect('{');
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("对象的键必须是字符串");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue(depth);
                result.put(key, value);
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    continue;
                }
                expect('}');
                return result;
            }
        }

        private List<Object> parseArray(int depth) {
            expect('[');
            ArrayList<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(parseValue(depth));
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    continue;
                }
                expect(']');
                return result;
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!atEnd()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("字符串转义不完整");
                    }
                    char escape = text.charAt(index++);
                    switch (escape) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> out.append(readUnicode());
                        default -> throw error("非法转义 \\" + escape);
                    }
                } else if (c < 0x20) {
                    throw error("字符串中包含未转义的控制字符");
                } else {
                    out.append(c);
                }
            }
            throw error("字符串没有结束引号");
        }

        private char readUnicode() {
            if (index + 4 > text.length()) {
                throw error("Unicode 转义不完整");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                char c = text.charAt(index++);
                int digit = Character.digit(c, 16);
                if (digit < 0) {
                    throw error("非法 Unicode 转义");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private Object parseLiteral(String literal, Object value) {
            if (text.startsWith(literal, index)) {
                index += literal.length();
                return value;
            }
            throw error("非法 JSON 字面量");
        }

        private Object parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            if (atEnd()) {
                throw error("数字不完整");
            }
            if (peek('0')) {
                index++;
            } else if (peekBetween('1', '9')) {
                while (!atEnd() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            } else {
                throw error("数字格式错误");
            }
            boolean floating = false;
            if (peek('.')) {
                floating = true;
                index++;
                if (atEnd() || !Character.isDigit(text.charAt(index))) {
                    throw error("小数点后缺少数字");
                }
                while (!atEnd() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (peek('e') || peek('E')) {
                floating = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                if (atEnd() || !Character.isDigit(text.charAt(index))) {
                    throw error("指数部分缺少数字");
                }
                while (!atEnd() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String number = text.substring(start, index);
            try {
                if (floating) {
                    return Double.parseDouble(number);
                }
                return Long.parseLong(number);
            } catch (NumberFormatException ex) {
                throw error("数字超出范围: " + number);
            }
        }

        private boolean peek(char expected) {
            return !atEnd() && text.charAt(index) == expected;
        }

        private boolean peekBetween(char low, char high) {
            return !atEnd() && text.charAt(index) >= low && text.charAt(index) <= high;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (atEnd() || text.charAt(index) != expected) {
                throw error("期望 '" + expected + "'");
            }
            index++;
        }

        private IllegalArgumentException error(String message) {
            int line = 1;
            int column = 1;
            int limit = Math.min(index, text.length());
            for (int i = 0; i < limit; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new IllegalArgumentException(message + "（第 " + line + " 行，第 " + column + " 列）");
        }
    }
}
