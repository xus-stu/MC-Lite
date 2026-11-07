package dev.mclite.island.bili;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * B 站 Web 接口客户端：扫码登录、搜索、收藏夹、视频信息、音频流。
 *
 * <p>只使用用户自己的 cookie；不内置任何账号或 appkey。
 */
public final class BiliApi {

    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String REFERER = "https://www.bilibili.com/";
    private static final String API = "https://api.bilibili.com";
    private static final String PASSPORT = "https://passport.bilibili.com";

    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private final HttpClient client;
    private BiliCookies cookies;
    private String mixinKey = "";
    private long mixinKeyAt = 0L;

    public BiliApi(BiliCookies cookies) {
        this.cookies = cookies == null ? new BiliCookies() : cookies;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void setCookies(BiliCookies cookies) {
        this.cookies = cookies == null ? new BiliCookies() : cookies;
        this.mixinKey = "";
    }

    public BiliCookies cookies() {
        return cookies;
    }

    // ------------------------------------------------------------ 扫码登录

    public record Qr(String url, String key) {
    }

    public record QrPoll(int code, String message) {
    }

    public Qr qrGenerate() throws IOException {
        JsonObject data = getJson(PASSPORT + "/x/passport-login/web/qrcode/generate", false).data();
        return new Qr(str(data, "url"), str(data, "qrcode_key"));
    }

    /**
     * 轮询二维码；成功时把 Set-Cookie 里的三项写入内存 cookies（调用方负责 save）。
     */
    public QrPoll qrPoll(String key) throws IOException {
        HttpResponse<String> response = send(URI.create(
                PASSPORT + "/x/passport-login/web/qrcode/poll?qrcode_key=" + enc(key)), false);
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject data = obj(root, "data");
        int code = integer(data, "code", -1);
        if (code == 0) {
            captureCookies(response.headers().allValues("set-cookie"), data);
        }
        return new QrPoll(code, str(data, "message"));
    }

    private void captureCookies(List<String> setCookies, JsonObject data) {
        for (String header : setCookies) {
            String lower = header.toLowerCase();
            if (lower.startsWith("sessdata=")) {
                cookies.sessdata = cookieValue(header);
            } else if (lower.startsWith("bili_jct=")) {
                cookies.biliJct = cookieValue(header);
            } else if (lower.startsWith("dedeuserid=")) {
                cookies.dedeUserId = cookieValue(header);
            }
        }
        // 有些环境只在 data.url 的 query 里给 SESSDATA
        String url = str(data, "url");
        if ((cookies.sessdata == null || cookies.sessdata.isBlank()) && !url.isBlank()) {
            cookies.sessdata = queryParam(url, "SESSDATA");
            cookies.biliJct = cookies.biliJct == null || cookies.biliJct.isBlank()
                    ? queryParam(url, "bili_jct") : cookies.biliJct;
            cookies.dedeUserId = cookies.dedeUserId == null || cookies.dedeUserId.isBlank()
                    ? queryParam(url, "DedeUserID") : cookies.dedeUserId;
        }
        cookies.savedAt = System.currentTimeMillis();
    }

    private static String cookieValue(String header) {
        int end = header.indexOf(';');
        String pair = end < 0 ? header : header.substring(0, end);
        int eq = pair.indexOf('=');
        return eq < 0 ? "" : pair.substring(eq + 1);
    }

    private static String queryParam(String url, String key) {
        try {
            int q = url.indexOf('?');
            if (q < 0) {
                return "";
            }
            for (String part : url.substring(q + 1).split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).equals(key)) {
                    return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
            // 解析失败当没有
        }
        return "";
    }

    public boolean isLoggedIn() throws IOException {
        if (!cookies.loggedIn()) {
            return false;
        }
        JsonObject nav = rawNav();
        return integer(nav, "code", -1) == 0 && bool(obj(nav, "data"), "isLogin", false);
    }

    public String userName() throws IOException {
        JsonObject data = getJson(API + "/x/web-interface/nav", true).data();
        return str(data, "uname");
    }

    // ------------------------------------------------------------ WBI

    private void ensureMixinKey() throws IOException {
        if (!mixinKey.isBlank() && System.currentTimeMillis() - mixinKeyAt < 6 * 3600_000L) {
            return;
        }
        JsonObject data = obj(rawNav(), "data");
        JsonObject wbi = obj(data, "wbi_img");
        String img = basename(str(wbi, "img_url"));
        String sub = basename(str(wbi, "sub_url"));
        if (img.isBlank() || sub.isBlank()) {
            throw new IOException("B 站 WBI key 获取失败");
        }
        StringBuilder mixed = new StringBuilder(32);
        String raw = img + sub;
        for (int i = 0; i < 32; i++) {
            mixed.append(raw.charAt(MIXIN_KEY_ENC_TAB[i]));
        }
        mixinKey = mixed.toString();
        mixinKeyAt = System.currentTimeMillis();
    }

    private static String basename(String url) {
        String value = url == null ? "" : url;
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String signedQuery(Map<String, String> params) throws IOException {
        ensureMixinKey();
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            sorted.put(entry.getKey(), value.replaceAll("[!'()*]", ""));
        }
        sorted.put("wts", String.valueOf(System.currentTimeMillis() / 1000L));
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(enc(entry.getKey())).append('=').append(enc(entry.getValue()));
        }
        String wRid = md5(query + mixinKey);
        return query.append("&w_rid=").append(wRid).toString();
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------ 搜索/收藏

    public record Video(String bvid, String title, String author, long durationSeconds,
                        String cover) {
    }

    public List<Video> search(String keyword, int page) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search_type", "video");
        params.put("keyword", keyword);
        params.put("page", String.valueOf(Math.max(1, page)));
        String query = signedQuery(params);
        JsonObject data = getJson(API + "/x/web-interface/wbi/search/type?" + query, true).data();
        JsonArray array = arr(data, "result");
        List<Video> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            result.add(new Video(str(item, "bvid"), stripHtml(str(item, "title")),
                    str(item, "author"), parseDuration(str(item, "duration")),
                    str(item, "pic")));
        }
        return result;
    }

    public record FavFolder(long id, String title, int count) {
    }

    public List<FavFolder> favFolders() throws IOException {
        String mid = cookies.dedeUserId;
        if (mid == null || mid.isBlank()) {
            return List.of();
        }
        JsonObject data = getJson(API + "/x/v3/fav/folder/created/list-all?up_mid=" + enc(mid), true).data();
        JsonArray array = arr(data, "list");
        List<FavFolder> folders = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            folders.add(new FavFolder(lng(item, "id"), str(item, "title"),
                    integer(item, "media_count", 0)));
        }
        return folders;
    }

    public List<Video> favItems(long mediaId, int page) throws IOException {
        String url = API + "/x/v3/fav/resource/list?media_id=" + mediaId
                + "&pn=" + Math.max(1, page) + "&ps=20&platform=web";
        JsonObject data = getJson(url, true).data();
        JsonArray array = arr(data, "medias");
        List<Video> videos = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            String bvid = str(item, "bvid");
            if (bvid.isBlank()) {
                continue;
            }
            JsonObject upper = obj(item, "upper");
            videos.add(new Video(bvid, str(item, "title"), str(upper, "name"),
                    lng(item, "duration"), str(item, "cover")));
        }
        return videos;
    }

    // ------------------------------------------------------------ 播放

    public record View(String bvid, int cid, String title, String owner, long durationSeconds) {
    }

    public View view(String bvid) throws IOException {
        JsonObject data = getJson(API + "/x/web-interface/view?bvid=" + enc(bvid), true).data();
        return new View(bvid, integer(data, "cid", 0), str(data, "title"),
                str(obj(data, "owner"), "name"), lng(data, "duration"));
    }

    public record AudioStream(int audioId, String url, long durationMs, String codecs) {
    }

    public AudioStream playUrl(String bvid, int cid, BiliQuality quality) throws IOException {
        String url = API + "/x/player/playurl?bvid=" + enc(bvid) + "&cid=" + cid
                + "&fnval=16&fourk=1";
        JsonObject data = getJson(url, true).data();
        long lengthMs = lng(data, "timelength");
        JsonObject dash = obj(data, "dash");
        JsonArray audios = arr(dash, "audio");
        AudioStream best = null;
        for (JsonElement element : audios) {
            JsonObject item = element.getAsJsonObject();
            int id = integer(item, "id", 0);
            String codecs = str(item, "codecs");
            if (codecs != null && !codecs.isBlank() && !codecs.startsWith("mp4a")) {
                continue;
            }
            String base = str(item, "baseUrl");
            if (base.isBlank()) {
                base = str(item, "base_url");
            }
            if (base.isBlank() || id == 0) {
                continue;
            }
            AudioStream candidate = new AudioStream(id, base, lengthMs, codecs);
            if (quality != BiliQuality.AUTO && id == quality.audioId()) {
                return candidate;
            }
            if (best == null || rank(id) > rank(best.audioId())) {
                best = candidate;
            }
        }
        if (best == null) {
            throw new IOException("该视频没有可解码的 AAC 音轨");
        }
        return best;
    }

    private static int rank(int audioId) {
        return switch (audioId) {
            case 30280 -> 3;
            case 30232 -> 2;
            case 30216 -> 1;
            default -> 0;
        };
    }

    /**
     * 下载到内存；音频 m4s 通常几 MB。
     * 用 HttpURLConnection 而不是 java.net.http：B 站部分 CDN 对后者直接连接超时。
     */
    public byte[] download(String url) throws IOException {
        java.net.URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Referer", REFERER);
        connection.setRequestProperty("Accept", "*/*");
        if (connection instanceof java.net.HttpURLConnection http) {
            int code = http.getResponseCode();
            if (code / 100 != 2) {
                throw new IOException("音频下载失败: HTTP " + code);
            }
        }
        try (java.io.InputStream in = connection.getInputStream();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    /** B 站官方字幕转 LRC；没有字幕返回 null。 */
    public String subtitleLrc(String bvid, int cid) {
        try {
            String url = API + "/x/player/v2?bvid=" + enc(bvid) + "&cid=" + cid;
            JsonObject data = getJson(url, true).data();
            JsonObject subtitle = obj(data, "subtitle");
            JsonArray subtitles = arr(subtitle, "subtitles");
            if (subtitles.isEmpty()) {
                return null;
            }
            JsonObject picked = null;
            for (JsonElement element : subtitles) {
                JsonObject item = element.getAsJsonObject();
                String lan = str(item, "lan");
                if (picked == null || lan.startsWith("zh")) {
                    picked = item;
                    if (lan.startsWith("zh")) {
                        break;
                    }
                }
            }
            String subtitleUrl = str(picked, "subtitle_url");
            if (subtitleUrl.isBlank()) {
                return null;
            }
            if (subtitleUrl.startsWith("//")) {
                subtitleUrl = "https:" + subtitleUrl;
            }
            JsonObject root = JsonParser.parseString(downloadText(subtitleUrl)).getAsJsonObject();
            JsonArray body = arr(root, "body");
            StringBuilder lrc = new StringBuilder();
            for (JsonElement element : body) {
                JsonObject cue = element.getAsJsonObject();
                double from = cue.has("from") ? cue.get("from").getAsDouble() : 0;
                String content = str(cue, "content").replace("\n", " ").trim();
                if (content.isEmpty()) {
                    continue;
                }
                long total = Math.max(0L, (long) (from * 1000));
                lrc.append(String.format("[%02d:%02d.%02d]%s\n",
                        total / 60_000, total / 1000 % 60, total % 1000 / 10, content));
            }
            return lrc.length() == 0 ? null : lrc.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String downloadText(String url) throws IOException {
        java.net.URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(12_000);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Referer", REFERER);
        try (java.io.InputStream in = connection.getInputStream();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public void saveCookies(Path file) throws IOException {
        cookies.save(file);
    }

    // ------------------------------------------------------------ HTTP/JSON

    private record Root(JsonObject json) {
        JsonObject data() {
            return obj(json, "data");
        }
    }

    /** nav 在未登录时会返回 -101，但 data.wbi_img 仍然可用，所以这里不按 code 抛错。 */
    private JsonObject rawNav() throws IOException {
        HttpResponse<String> response = send(URI.create(API + "/x/web-interface/nav"), true);
        if (response.statusCode() / 100 != 2) {
            throw new IOException("B 站 nav HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private Root getJson(String url, boolean withCookies) throws IOException {
        HttpResponse<String> response = send(URI.create(url), withCookies);
        if (response.statusCode() / 100 != 2) {
            throw new IOException("B 站接口 HTTP " + response.statusCode());
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        int code = integer(root, "code", 0);
        if (code != 0) {
            throw new IOException("B 站接口错误 " + code + ": " + str(root, "message"));
        }
        return new Root(root);
    }

    private HttpResponse<String> send(URI uri, boolean withCookies) throws IOException {
        HttpRequest.Builder builder = baseRequest(uri).GET();
        if (withCookies) {
            String header = cookies.cookieHeader();
            if (!header.isBlank()) {
                builder.header("Cookie", header);
            }
        }
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("B 站请求被中断", ex);
        }
    }

    private static HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .header("Accept", "application/json, text/plain, */*");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static JsonObject obj(JsonObject parent, String key) {
        if (parent == null) {
            return new JsonObject();
        }
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray arr(JsonObject parent, String key) {
        if (parent == null) {
            return new JsonArray();
        }
        JsonElement element = parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String str(JsonObject parent, String key) {
        if (parent == null) {
            return "";
        }
        JsonElement element = parent.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int integer(JsonObject parent, String key, int fallback) {
        try {
            return parent != null && parent.has(key) ? parent.get(key).getAsInt() : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static long lng(JsonObject parent, String key) {
        try {
            return parent != null && parent.has(key) ? parent.get(key).getAsLong() : 0L;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static boolean bool(JsonObject parent, String key, boolean fallback) {
        try {
            return parent != null && parent.has(key) ? parent.get(key).getAsBoolean() : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", "");
    }

    private static long parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        String[] parts = value.split(":");
        try {
            if (parts.length == 2) {
                return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
            }
            if (parts.length == 3) {
                return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60
                        + Long.parseLong(parts[2]);
            }
        } catch (NumberFormatException ignored) {
            // 忽略
        }
        return 0L;
    }
}
