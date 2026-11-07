package dev.mclite.island.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 解析。故意不依赖任何第三方库：歌词文件可能来自任何播放器，
 * 格式容错比“严格”重要。
 */
public final class LrcParser {

    /** 时间标签：[mm:ss]、[mm:ss.xx]、[mm:ss:xx]，允许一行多个。 */
    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern META_TAG =
            Pattern.compile("\\[(ti|ar|al|by|offset):([^]]*)]", Pattern.CASE_INSENSITIVE);

    private LrcParser() {
    }

    /** 解析结果：排好序的歌词行与元数据。 */
    public record Document(List<LrcLine> lines, long offsetMs, String title, String artist) {
        public static final Document EMPTY = new Document(List.of(), 0L, "", "");
    }

    public static Document parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Document.EMPTY;
        }
        String text = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        String title = "";
        String artist = "";
        long offset = 0L;
        List<LrcLine> lines = new ArrayList<>();

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher meta = META_TAG.matcher(line);
            if (meta.find()) {
                String key = meta.group(1).toLowerCase(java.util.Locale.ROOT);
                String value = meta.group(2).trim();
                switch (key) {
                    case "offset" -> offset = parseLong(value, offset);
                    case "ti" -> title = value;
                    case "ar" -> artist = value;
                    default -> {
                        // 其余元数据（al/by）当前不使用
                    }
                }
                // 同一行可能还带时间标签，继续往下解析
            }

            Matcher matcher = TIME_TAG.matcher(line);
            List<Long> times = new ArrayList<>();
            int lastEnd = -1;
            while (matcher.find()) {
                long minutes = parseLong(matcher.group(1), 0L);
                long seconds = parseLong(matcher.group(2), 0L);
                long fraction = fractionMillis(matcher.group(3));
                times.add(minutes * 60_000L + seconds * 1_000L + fraction);
                lastEnd = matcher.end();
            }
            if (times.isEmpty() || lastEnd < 0) {
                continue;
            }
            String content = line.substring(lastEnd).trim();
            for (long time : times) {
                long effective = Math.max(0L, time - offset);
                lines.add(new LrcLine(effective, content));
            }
        }

        lines.sort(Comparator.comparingLong(LrcLine::timeMs));
        return new Document(List.copyOf(lines), offset, title, artist);
    }

    /** 读取歌词文件：UTF-8（严格）→ GBK → ISO-8859-1。 */
    public static String read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            // 中文 LRC 常见的 GBK 编码
            try {
                return Charset.forName("GBK").newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (Exception ignoredAgain) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        }
    }

    public static Document readDocument(Path file) throws IOException {
        return parse(read(file));
    }

    /**
     * 找同名的 .lrc：优先与音频同目录，其次在歌词目录按文件名主干匹配。
     */
    public static Path findSidecar(Track track, Path lyricsDir) {
        if (track.file() == null) {
            return null;
        }
        String name = track.file().getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        // 导入到 lyrics 目录的优先；用户显式导入应当覆盖音频旁旧 sidecar
        if (lyricsDir != null) {
            Path inLyrics = lyricsDir.resolve(stem + ".lrc");
            if (Files.isRegularFile(inLyrics)) {
                return inLyrics;
            }
            // Windows/Linux 上歌词文件常见 .LRC 大写
            Path upper = lyricsDir.resolve(stem + ".LRC");
            if (Files.isRegularFile(upper)) {
                return upper;
            }
        }
        Path nextToAudio = track.file().resolveSibling(stem + ".lrc");
        if (Files.isRegularFile(nextToAudio)) {
            return nextToAudio;
        }
        return null;
    }

    private static long fractionMillis(String fraction) {
        if (fraction == null || fraction.isEmpty()) {
            return 0L;
        }
        return switch (fraction.length()) {
            case 1 -> parseLong(fraction, 0L) * 100L;
            case 2 -> parseLong(fraction, 0L) * 10L;
            default -> parseLong(fraction.substring(0, 3), 0L);
        };
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
