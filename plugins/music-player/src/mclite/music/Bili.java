package mclite.music;

import mclite.Json;

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

/** B 站最小客户端：登录同步/扫码、搜索、取播放地址。 */
final class Bili {

    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String API = "https://api.bilibili.com";
    private static final int[] MIXIN = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    record Video(String bvid, String title, String author, long seconds, String cover) {
    }

    record Song(String bvid, String title, String author, String url, String cover, String quality) {
    }

    /** 音频质量：对应 B 站 DASH 的 audio id。 */
    enum Quality {
        STANDARD("标准 64K", 30216),
        HIGH("高 132K", 30232),
        VERY_HIGH("极高 192K", 30280),
        LOSSLESS("无损 FLAC", 30251);

        final String label;
        final int id;

        Quality(String label, int id) {
            this.label = label;
            this.id = id;
        }
    }

    record Profile(boolean login, String name, long mid, String face) {
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Path cookieFile;

    String sessdata = "";
    String biliJct = "";
    String dedeUserId = "";
    private String mixinKey = "";
    private long mixinAt;

    Bili(Path cookieFile) {
        this.cookieFile = cookieFile;
        load();
    }

    Path cookieFile() {
        return cookieFile;
    }

    boolean loggedIn() {
        return !sessdata.isBlank();
    }

    /** 优先用自己的登录；没登录时尝试从当前实例的 mclite-island 登录同步。 */
    void syncFromInstance(Path instanceDir) {
        if (loggedIn() || instanceDir == null) {
            return;
        }
        Path island = instanceDir.resolve("config").resolve("mclite-island").resolve("bilibili.json");
        if (!Files.isRegularFile(island)) {
            return;
        }
        try {
            Map<String, Object> root = Json.obj(Json.parse(Files.readString(island, StandardCharsets.UTF_8)));
            if (root == null) {
                return;
            }
            // 岛的 BiliCookies 是扁平结构；兼容旧版嵌套 cookies。
            Map<String, Object> cookies = root.containsKey("cookies")
                    ? Json.obj(root.get("cookies")) : root;
            if (cookies == null) {
                return;
            }
            sessdata = Json.str(cookies, "sessdata", "");
            biliJct = Json.str(cookies, "biliJct", "");
            dedeUserId = Json.str(cookies, "dedeUserId", "");
            save();
        } catch (Exception ignored) {
            // 同步失败就当没登录。
        }
    }

    private void load() {
        try {
            if (!Files.isRegularFile(cookieFile)) {
                return;
            }
            Map<String, Object> root = Json.obj(Json.parse(Files.readString(cookieFile, StandardCharsets.UTF_8)));
            if (root == null) {
                return;
            }
            sessdata = Json.str(root, "sessdata", "");
            biliJct = Json.str(root, "biliJct", "");
            dedeUserId = Json.str(root, "dedeUserId", "");
        } catch (Exception ignored) {
            // 坏文件按未登录处理。
        }
    }

    void save() {
        try {
            Files.createDirectories(cookieFile.getParent());
            String json = "{\"sessdata\":\"" + esc(sessdata) + "\",\"biliJct\":\""
                    + esc(biliJct) + "\",\"dedeUserId\":\"" + esc(dedeUserId) + "\"}";
            Files.writeString(cookieFile, json, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 存盘失败只影响下次启动。
        }
    }

    Profile profile() {
        try {
            Map<String, Object> data = data(get("/x/web-interface/nav", true));
            boolean login = data != null && Boolean.TRUE.equals(data.get("isLogin"));
            return new Profile(login, login ? Json.str(data, "uname", "") : "",
                    login ? Json.lng(data, "mid", 0) : 0,
                    login ? Art.normalize(Json.str(data, "face", "")) : "");
        } catch (Exception ex) {
            return new Profile(false, "", 0, "");
        }
    }

    // ---------------------------------------------------------------- 扫码登录

    record Qr(String url, String key) {
    }

    Qr qrGenerate() throws IOException {
        Map<String, Object> data = data(get("https://passport.bilibili.com/x/passport-login/web/qrcode/generate", false));
        if (data == null) {
            throw new IOException("二维码获取失败");
        }
        return new Qr(Json.str(data, "url", ""), Json.str(data, "qrcode_key", ""));
    }

    /** 返回 0=成功，其它=未完成/过期。 */
    int qrPoll(Qr qr, List<String> setCookies) throws IOException {
        HttpRequest request = base("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key="
                + enc(qr.key()), false).GET().build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("登录轮询被中断", ex);
        }
        response.headers().allValues("set-cookie").forEach(setCookies::add);
        Map<String, Object> root = Json.obj(Json.parse(response.body()));
        Map<String, Object> data = Json.obj(root == null ? null : root.get("data"));
        if (data == null) {
            return -1;
        }
        int code = (int) Json.lng(data, "code", -1);
        if (code == 0) {
            for (String cookie : setCookies) {
                String[] parts = cookie.split(";", 2);
                int eq = parts[0].indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = parts[0].substring(0, eq).trim();
                String value = parts[0].substring(eq + 1).trim();
                switch (name) {
                    case "SESSDATA" -> sessdata = value;
                    case "bili_jct" -> biliJct = value;
                    case "DedeUserID" -> dedeUserId = value;
                    default -> {
                    }
                }
            }
            save();
        }
        return code;
    }

    // ---------------------------------------------------------------- 搜索

    List<Video> search(String keyword, int page) throws IOException {
        ensureMixin();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search_type", "video");
        params.put("keyword", keyword);
        params.put("page", String.valueOf(page));
        Map<String, Object> data = data(get("/x/web-interface/wbi/search/type?" + signed(params), true));
        List<Video> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        for (Object raw : Json.arr(data.get("result"))) {
            Map<String, Object> item = Json.obj(raw);
            if (item == null) {
                continue;
            }
            String bvid = Json.str(item, "bvid", "");
            if (bvid.isBlank()) {
                continue;
            }
            out.add(new Video(bvid, strip(Json.str(item, "title", bvid)),
                    Json.str(item, "author", ""), parseDuration(Json.str(item, "duration", "")),
                    Art.normalize(Json.str(item, "pic", ""))));
        }
        return out;
    }

    // ---------------------------------------------------------------- 收藏夹

    record FavFolder(long id, String title, int count) {
    }

    /** 当前登录用户创建的全部收藏夹（含默认收藏夹）。 */
    List<FavFolder> favFolders(long mid) throws IOException {
        Map<String, Object> data = get("/x/v3/fav/folder/created/list-all?up_mid=" + mid, true);
        List<FavFolder> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        for (Object raw : Json.arr(data.get("list"))) {
            Map<String, Object> item = Json.obj(raw);
            if (item == null) {
                continue;
            }
            long id = Json.lng(item, "id", 0);
            if (id == 0) {
                continue;
            }
            out.add(new FavFolder(id, strip(Json.str(item, "title", "收藏夹")),
                    (int) Json.lng(item, "media_count", 0)));
        }
        return out;
    }

    /** 某个收藏夹里的视频，每页 20 个。 */
    List<Video> favVideos(long mediaId, int page) throws IOException {
        Map<String, Object> data = get("/x/v3/fav/resource/list?media_id=" + mediaId
                + "&pn=" + page + "&ps=20&order=mtime&type=0&platform=web", true);
        List<Video> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        for (Object raw : Json.arr(data.get("medias"))) {
            Map<String, Object> item = Json.obj(raw);
            if (item == null) {
                continue;
            }
            String bvid = Json.str(item, "bvid", "");
            if (bvid.isBlank()) {
                continue;
            }
            String author = Json.str(Json.obj(item.get("upper")), "name", "");
            out.add(new Video(bvid, strip(Json.str(item, "title", bvid)), author,
                    Json.lng(item, "duration", 0), Art.normalize(Json.str(item, "cover", ""))));
        }
        return out;
    }

    int searchPageCount(String keyword) {
        return 0; // 简化：只取第一页。
    }

    // ---------------------------------------------------------------- 播放

    Song resolve(Video video) throws IOException {
        return resolve(video, Quality.VERY_HIGH);
    }

    /** 从 bvid 重新取播放地址（队列恢复/链接过期时用）。 */
    Song resolveByBvid(String bvid, Quality quality) throws IOException {
        Map<String, Object> view = data(get("/x/web-interface/view?bvid=" + enc(bvid), true));
        if (view == null) {
            throw new IOException("取视频信息失败");
        }
        long cid = Json.lng(view, "cid", 0);
        if (cid == 0) {
            List<Object> pages = Json.arr(view.get("pages"));
            if (pages != null && !pages.isEmpty()) {
                cid = Json.lng(Json.obj(pages.get(0)), "cid", 0);
            }
        }
        if (cid == 0) {
            throw new IOException("找不到 cid");
        }
        Map<String, Object> play = data(get("/x/player/playurl?bvid=" + enc(bvid)
                + "&cid=" + cid + "&fnval=16&fourk=1", true));
        if (play == null) {
            throw new IOException("取播放地址失败");
        }
        String[] picked = bestAudio(play, quality);
        if (picked[0].isBlank()) {
            throw new IOException("没有可用的音频流");
        }
        return new Song(bvid, Json.str(view, "title", bvid), Json.str(Json.obj(view.get("owner")), "name", ""),
                picked[0], Art.normalize(Json.str(view, "pic", "")), picked[1]);
    }

    Song resolve(Video video, Quality quality) throws IOException {
        Map<String, Object> view = data(get("/x/web-interface/view?bvid=" + enc(video.bvid()), true));
        if (view == null) {
            throw new IOException("取视频信息失败");
        }
        long cid = Json.lng(view, "cid", 0);
        if (cid == 0) {
            List<Object> pages = Json.arr(view.get("pages"));
            if (pages != null && !pages.isEmpty()) {
                cid = Json.lng(Json.obj(pages.get(0)), "cid", 0);
            }
        }
        if (cid == 0) {
            throw new IOException("找不到 cid");
        }
        Map<String, Object> play = data(get("/x/player/playurl?bvid=" + enc(video.bvid())
                + "&cid=" + cid + "&fnval=16&fourk=1", true));
        if (play == null) {
            throw new IOException("取播放地址失败");
        }
        String[] picked = bestAudio(play, quality);
        if (picked[0].isBlank()) {
            throw new IOException("没有可用的音频流");
        }
        return new Song(video.bvid(), video.title(), video.author(), picked[0], video.cover(), picked[1]);
    }

    /** 返回 {url, 实际音质标签}。优先取所选音质，没有就取最高码率。 */
    private static String[] bestAudio(Map<String, Object> play, Quality quality) {
        Map<String, Object> dash = Json.obj(play.get("dash"));
        String exact = "";
        String exactLabel = "";
        String best = "";
        String bestLabel = "";
        long bestBandwidth = -1;
        if (dash != null) {
            for (Object raw : Json.arr(dash.get("audio"))) {
                Map<String, Object> audio = Json.obj(raw);
                if (audio == null) {
                    continue;
                }
                Object first = firstUrl(audio);
                String url = first == null ? "" : String.valueOf(first);
                if (url.isBlank()) {
                    continue;
                }
                long id = Json.lng(audio, "id", 0);
                long bandwidth = Json.lng(audio, "bandwidth", 0);
                String label = qualityLabel((int) id);
                if (id == quality.id) {
                    exact = url;
                    exactLabel = label;
                }
                if (bandwidth > bestBandwidth) {
                    bestBandwidth = bandwidth;
                    best = url;
                    bestLabel = label;
                }
            }
            if (!exact.isBlank()) {
                return new String[]{exact, exactLabel};
            }
            if (!best.isBlank()) {
                return new String[]{best, bestLabel};
            }
        }
        for (Object raw : Json.arr(play.get("durl"))) {
            Map<String, Object> durl = Json.obj(raw);
            if (durl != null) {
                String url = Json.str(durl, "url", "");
                if (!url.isBlank()) {
                    return new String[]{url, "标准"};
                }
            }
        }
        return new String[]{"", ""};
    }

    private static String qualityLabel(int id) {
        for (Quality quality : Quality.values()) {
            if (quality.id == id) {
                return quality.label;
            }
        }
        return "其它";
    }

    private static Object firstUrl(Map<String, Object> audio) {
        List<Object> backups = Json.arr(audio.get("backup_url"));
        if (backups != null && !backups.isEmpty()) {
            return backups.get(0);
        }
        Object base = audio.get("base_url");
        return base != null ? base : audio.get("baseUrl");
    }

    // ---------------------------------------------------------------- HTTP

    private Map<String, Object> get(String pathOrUrl, boolean api) throws IOException {
        String url = api && !pathOrUrl.startsWith("http") ? API + pathOrUrl : pathOrUrl;
        HttpRequest request = base(url, true).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            Map<String, Object> root = Json.obj(Json.parse(response.body()));
            if (root == null) {
                return null;
            }
            long code = Json.lng(root, "code", -1);
            if (code != 0 && code != -101) {
                return null;
            }
            return Json.obj(root.get("data"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", ex);
        }
    }

    private static Map<String, Object> data(Map<String, Object> value) {
        return value;
    }

    private HttpRequest.Builder base(String url, boolean withCookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Referer", "https://www.bilibili.com/");
        if (withCookie && loggedIn()) {
            builder.header("Cookie", "SESSDATA=" + sessdata + "; bili_jct=" + biliJct
                    + "; DedeUserID=" + dedeUserId);
        }
        return builder;
    }

    private void ensureMixin() throws IOException {
        if (!mixinKey.isBlank() && System.currentTimeMillis() - mixinAt < 6 * 3600_000L) {
            return;
        }
        Map<String, Object> nav = data(get("/x/web-interface/nav", true));
        Map<String, Object> wbi = Json.obj(nav == null ? null : nav.get("wbi_img"));
        if (wbi == null) {
            throw new IOException("WBI key 获取失败");
        }
        String img = base(Json.str(wbi, "img_url", ""));
        String sub = base(Json.str(wbi, "sub_url", ""));
        if (img.isBlank() || sub.isBlank()) {
            throw new IOException("WBI key 为空");
        }
        StringBuilder mixed = new StringBuilder(32);
        String raw = img + sub;
        for (int index : MIXIN) {
            if (index < raw.length()) {
                mixed.append(raw.charAt(index));
            }
        }
        mixinKey = mixed.toString();
        mixinAt = System.currentTimeMillis();
    }

    private String signed(Map<String, String> params) throws IOException {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sorted.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().replaceAll("[!'()*]", ""));
        }
        sorted.put("wts", String.valueOf(System.currentTimeMillis() / 1000L));
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(enc(entry.getKey())).append('=').append(enc(entry.getValue()));
        }
        return query + "&w_rid=" + md5(query + mixinKey);
    }

    private static String base(String url) {
        String value = url == null ? "" : url;
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest.digest(value.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String strip(String html) {
        return html.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    private static long parseDuration(String text) {
        long total = 0;
        for (String part : text.split(":")) {
            try {
                total = total * 60 + Long.parseLong(part.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return total;
    }
}
