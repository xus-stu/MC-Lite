package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public final class Versions {
    public static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private Versions() {
    }

    public record FetchResult(VersionManifest manifest, boolean fromCache, Instant fetchedAt) {
    }

    public static FetchResult fetch(Config config, ProgressSink progress, CancellationToken token)
            throws IOException {
        Path cache = config.cacheDir().resolve("version_manifest_v2.json");
        boolean offline = Boolean.getBoolean("mclite.offline");
        IOException networkError = null;

        if (!offline) {
            progress.stage("正在从 Mojang 获取版本列表…");
            progress.indeterminate(true);
            Net net = new Net(progress, token);
            try {
                byte[] bytes = net.getUnverifiedBytes(MANIFEST_URL, "官方版本列表");
                VersionManifest manifest = VersionManifest.parse(new String(bytes, StandardCharsets.UTF_8));
                saveCache(cache, bytes);
                return new FetchResult(manifest, false, Instant.now());
            } catch (CancellationToken.CancelledException ex) {
                throw ex;
            } catch (IOException | RuntimeException ex) {
                networkError = ex instanceof IOException io ? io : new IOException(ex.getMessage(), ex);
                Log.warn("在线获取版本列表失败，尝试本地缓存: " + ex.getMessage());
            }
        }

        if (Files.isRegularFile(cache)) {
            try {
                byte[] bytes = Files.readAllBytes(cache);
                VersionManifest manifest = VersionManifest.parse(new String(bytes, StandardCharsets.UTF_8));
                return new FetchResult(manifest, true, Files.getLastModifiedTime(cache).toInstant());
            } catch (IOException | RuntimeException ex) {
                Log.warn("本地版本列表缓存不可用: " + ex.getMessage());
                if (networkError == null) {
                    networkError = ex instanceof IOException io ? io : new IOException(ex.getMessage(), ex);
                }
            }
        }

        if (networkError != null) {
            throw new IOException("无法获取官方版本列表，且没有可用缓存。请检查网络连接。", networkError);
        }
        throw new IOException("无法获取官方版本列表，且没有可用缓存。");
    }

    private static void saveCache(Path cache, byte[] bytes) {
        try {
            Files.createDirectories(cache.getParent());
            Path temp = cache.resolveSibling("version_manifest_v2.json.tmp");
            Files.write(temp, bytes);
            Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Log.warn("写入版本列表缓存失败: " + ex.getMessage());
        }
    }
}
