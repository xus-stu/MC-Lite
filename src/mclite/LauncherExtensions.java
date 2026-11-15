package mclite;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    static List<Entry> fetch() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MANIFEST_URL))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "MCLite/" + GameLauncher.LAUNCHER_VERSION)
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("清单 HTTP " + response.statusCode());
        }
        List<Entry> out = new ArrayList<>();
        Map<String, Object> root = Json.obj(Json.parse(response.body()));
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
            HttpRequest request = HttpRequest.newBuilder(URI.create(entry.url()))
                    .timeout(Duration.ofMinutes(5))
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
            if (response.statusCode() != 200) {
                throw new IOException("下载 HTTP " + response.statusCode());
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
