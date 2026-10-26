package mclite;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.jar.JarFile;

public final class AuthlibInjector {
    private static final String METADATA = "https://authlib-injector.yushi.moe/artifact/latest.json";

    private AuthlibInjector() {
    }

    public static Path ensure(Config config) {
        Path target = config.cacheDir().resolve("authlib-injector.jar");
        if (Files.isRegularFile(target)) {
            if (validJar(target)) {
                return target;
            }
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
            }
        }
        try {
            Files.createDirectories(target.getParent());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpResponse<String> metaResponse = client.send(HttpRequest.newBuilder(URI.create(METADATA))
                            .timeout(Duration.ofSeconds(20))
                            .header("User-Agent", "MCLite/" + Version.VALUE)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (metaResponse.statusCode() != 200) {
                return null;
            }
            Map<String, Object> meta = Json.obj(Json.parse(metaResponse.body()));
            String url = Json.str(meta, "download_url");
            String sha256 = Json.str(Json.obj(meta.get("checksums")), "sha256");
            if (url == null || url.isBlank()
                    || !"https".equalsIgnoreCase(URI.create(url).getScheme())) {
                return null;
            }
            Path temp = target.resolveSibling("authlib-injector.jar.part");
            HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofMinutes(2))
                            .header("User-Agent", "MCLite/" + Version.VALUE)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(temp);
                return null;
            }
            if (sha256 != null && !sha256.isBlank()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (var in = Files.newInputStream(temp)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        digest.update(buffer, 0, read);
                    }
                }
                String actual = HexFormat.of().formatHex(digest.digest());
                if (!actual.equalsIgnoreCase(sha256)) {
                    Files.deleteIfExists(temp);
                    return null;
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            if (!validJar(target)) {
                Files.deleteIfExists(target);
                Log.warn("authlib-injector 下载结果无效，已删除");
                return null;
            }
            return target;
        } catch (Exception ex) {
            Log.warn("下载 authlib-injector 失败: " + ex.getMessage());
            return null;
        }
    }

    private static boolean validJar(Path file) {
        try {
            if (Files.size(file) < 1024) {
                return false;
            }
            try (JarFile jar = new JarFile(file.toFile())) {
                return jar.size() > 0;
            }
        } catch (Exception ex) {
            return false;
        }
    }
}
