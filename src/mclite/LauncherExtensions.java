package mclite;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 启动器扩展（插件）清单与安装。插件 jar 不进启动器包，按需下载到
 * {@code ~/.mclite/plugins/}，下次启动时由 {@link PluginLoader} 加载。
 */
final class LauncherExtensions {

    /** 插件清单放在独立的 launcher-plugins release，与启动器版本解耦。 */
    static final String MANIFEST_URL =
            "https://github.com/xus-stu/MC-Lite/releases/download/launcher-plugins/plugin-manifest.json";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    record Entry(String id, String name, String version, String desc, String url,
                 String sha256, long size) {
        String label() {
            return name + " v" + version;
        }
    }

    private LauncherExtensions() {
    }

    /** 清单缓存文件；网络慢时先显示上次结果。 */
    static Path cacheFile(Config config) {
        return config.cacheDir().resolve("plugin-manifest.json");
    }

    static List<Entry> cached(Config config) {
        try {
            Path file = cacheFile(config);
            if (!Files.isRegularFile(file)) {
                return List.of();
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8), config);
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * 多镜像并发竞速：GitHub 直连经常超时，第一个成功的镜像结果直接返回。
     *
     * @param cachedOnly 只读缓存（不联网）
     */
    static List<Entry> fetch(Config config) throws IOException, InterruptedException {
        List<String> urls = GithubSources.candidates(MANIFEST_URL);
        java.util.concurrent.atomic.AtomicReference<String> winner = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        for (String url : urls) {
            Thread thread = new Thread(() -> {
                try {
                    String body = getText(url, 8);
                    if (body != null && body.contains("plugins") && winner.compareAndSet(null, body)) {
                        latch.countDown();
                    }
                } catch (Exception ignored) {
                    // 单个镜像失败不影响其它。
                }
            }, "mclite-plugin-manifest");
            thread.setDaemon(true);
            thread.start();
        }
        latch.await(9, java.util.concurrent.TimeUnit.SECONDS);
        String body = winner.get();
        if (body == null) {
            throw new IOException("所有镜像都超时或失败（可用「刷新列表」重试）");
        }
        List<Entry> entries = parse(body, config);
        if (entries.isEmpty()) {
            throw new IOException("清单格式不正确");
        }
        try {
            Files.createDirectories(cacheFile(config).getParent());
            Files.writeString(cacheFile(config), body, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 缓存失败不影响使用。
        }
        return entries;
    }

    private static String getText(String url, int timeoutSeconds) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", "MCLite/" + GameLauncher.LAUNCHER_VERSION)
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? response.body() : null;
    }

    private static List<Entry> parse(String body, Config config) {
        List<Entry> out = new ArrayList<>();
        Map<String, Object> root = Json.obj(Json.parse(body));
        for (Object raw : Json.arr(root == null ? null : root.get("plugins"))) {
            Map<String, Object> item = Json.obj(raw);
            if (item == null) {
                continue;
            }
            String id = Json.str(item, "id");
            String url = Json.str(item, "url");
            if (id == null || id.isBlank() || url == null || url.isBlank()) {
                continue;
            }
            out.add(new Entry(id, Json.str(item, "name", id), Json.str(item, "version", "?"),
                    Json.str(item, "desc", ""), url, Json.str(item, "sha256", ""),
                    Json.lng(item, "size", -1)));
        }
        return out;
    }

    static Path target(Config config, String id) {
        return PluginLoader.pluginsDir(config.appHome()).resolve(id + ".jar");
    }

    static boolean installed(Config config, String id) {
        return Files.isRegularFile(target(config, id));
    }

    static void uninstall(Config config, String id) throws IOException {
        Files.deleteIfExists(target(config, id));
    }

    /** 下载并校验 SHA-256，成功后原子替换旧 jar。 */
    static void install(Config config, Entry entry) throws IOException {
        if (entry.sha256() == null || entry.sha256().isBlank()) {
            throw new IOException("插件清单缺少 sha256，已拒绝安装");
        }
        Path dir = PluginLoader.pluginsDir(config.appHome());
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, entry.id(), ".part");
        try {
            boolean downloaded = false;
            for (String url : GithubSources.candidates(entry.url())) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .header("User-Agent", "MCLite/" + GameLauncher.LAUNCHER_VERSION)
                            .GET()
                            .build();
                    HttpResponse<Path> response;
                    try {
                        response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(temp));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IOException("下载被中断", ex);
                    }
                    if (response.statusCode() == 200) {
                        downloaded = true;
                        break;
                    }
                } catch (IOException ignored) {
                    // 换下一个镜像。
                }
            }
            if (!downloaded) {
                throw new IOException("所有镜像都下载失败");
            }
            String actual = sha256(temp);
            if (!actual.equalsIgnoreCase(entry.sha256())) {
                throw new IOException("哈希不匹配：" + actual);
            }
            Files.move(temp, target(config, entry.id()),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException("缺少 SHA-256", ex);
        }
    }
}
