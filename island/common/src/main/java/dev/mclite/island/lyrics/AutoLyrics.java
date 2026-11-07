package dev.mclite.island.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mclite.island.core.Track;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 自动歌词：从 LRCLIB 公开接口按标题/艺术家/时长匹配同步歌词。 */
public final class AutoLyrics {

    private static final String UA = "MCLiteIsland/0.3 (https://github.com/xus-stu/MC-Lite)";

    private AutoLyrics() {
    }

    public record Result(String lrc, String source) {
    }

    public static String cacheName(Track track) {
        String key = track.isRemote()
                ? "bili_" + track.bvid() + "_" + track.cid()
                : track.file().toAbsolutePath().toString();
        return "auto_" + sha1(key) + ".lrc";
    }

    /** @return 命中的同步歌词；没有返回 null。 */
    public static Result fetch(Track track) throws IOException {
        if (track.isRemote()) {
            return null;
        }
        String title = track.title();
        String artist = track.artist();
        if (title == null || title.isBlank()) {
            return null;
        }
        String url = "https://lrclib.net/api/search?track_name=" + enc(title)
                + (artist == null || artist.isBlank() ? "" : "&artist_name=" + enc(artist));
        String json = get(url);
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        JsonObject best = null;
        double bestScore = -1;
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            String synced = item.has("syncedLyrics") && !item.get("syncedLyrics").isJsonNull()
                    ? item.get("syncedLyrics").getAsString() : "";
            if (synced.isBlank()) {
                continue;
            }
            double score = score(track, item);
            if (score > bestScore) {
                best = item;
                bestScore = score;
            }
        }
        if (best == null || bestScore <= 0) {
            return null;
        }
        String lrc = best.get("syncedLyrics").getAsString();
        return new Result(lrc, "LRCLIB");
    }

    private static double score(Track track, JsonObject item) {
        String title = str(item, "trackName");
        String artist = str(item, "artistName");
        double score = 0;
        String want = normalize(track.title());
        String got = normalize(title);
        if (want.equals(got)) {
            score += 0.6;
        } else if (got.contains(want) || want.contains(got)) {
            score += 0.35;
        } else {
            return 0;
        }
        if (track.artist() != null && !track.artist().isBlank()
                && normalize(track.artist()).equals(normalize(artist))) {
            score += 0.2;
        }
        double duration = item.has("duration") && !item.get("duration").isJsonNull()
                ? item.get("duration").getAsDouble() : 0;
        if (track.durationMs() > 0 && duration > 0) {
            double diff = Math.abs(track.durationMs() / 1000.0 - duration);
            if (diff > 5) {
                return 0;
            }
            score += 0.2 * (1 - diff / 5.0);
        }
        return score;
    }

    private static String str(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\s\\-_/\\\\()\\[\\]{}'\\\".,!?，。！？、]", "");
    }

    private static String sha1(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String get(String url) throws IOException {
        java.net.URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(12_000);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Accept", "application/json");
        try (java.io.InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
