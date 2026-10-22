package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public record GamePaths(Path gameDir) {
    public GamePaths {
        gameDir = gameDir.toAbsolutePath().normalize();
    }

    public Path versionsDir() {
        return gameDir.resolve("versions");
    }

    public Path librariesDir() {
        return gameDir.resolve("libraries");
    }

    public Path assetsDir() {
        return gameDir.resolve("assets");
    }

    public Path assetsIndexesDir() {
        return assetsDir().resolve("indexes");
    }

    public Path assetObjectsDir() {
        return assetsDir().resolve("objects");
    }

    public Path virtualAssetsDir(String assetId) {
        return assetsDir().resolve("virtual").resolve(safeId(assetId));
    }

    public Path resourcesDir() {
        return gameDir.resolve("resources");
    }

    public Path versionDir(String versionId) {
        return versionsDir().resolve(safeId(versionId));
    }

    public Path versionJson(String versionId) {
        return versionDir(versionId).resolve(safeId(versionId) + ".json");
    }

    public Path clientJar(String versionId) {
        return versionDir(versionId).resolve(safeId(versionId) + ".jar");
    }

    public Path nativesDir(String versionId) {
        return versionDir(versionId).resolve("natives");
    }

    public Path marker(String versionId) {
        return versionDir(versionId).resolve(".mclite.properties");
    }

    public Path logConfig(String fileId) {
        return assetsDir().resolve("log_configs").resolve(safeFileName(fileId));
    }

    public Path assetIndex(String assetId) {
        return assetsIndexesDir().resolve(safeId(assetId) + ".json");
    }

    public Path assetObject(String hash) {
        if (hash == null || !hash.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("非法资源哈希");
        }
        String lower = hash.toLowerCase();
        return assetObjectsDir().resolve(lower.substring(0, 2)).resolve(lower);
    }

    public Path gameLogsDir() {
        return gameDir.resolve("logs");
    }

    public boolean isInstalled(String versionId) {
        return Files.isRegularFile(versionJson(versionId)) && Files.isRegularFile(clientJar(versionId));
    }

    public boolean assetsComplete(String versionId) {
        return Boolean.parseBoolean(readMarker(versionId).getProperty("assetsComplete", "false"));
    }

    public boolean hasMarker(String versionId) {
        return Files.isRegularFile(marker(versionId));
    }

    public Properties readMarker(String versionId) {
        Properties properties = new Properties();
        Path file = marker(versionId);
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException ex) {
                Log.warn("读取安装标记失败: " + ex.getMessage());
            }
        }
        return properties;
    }

    public void writeMarker(String versionId, Properties properties) throws IOException {
        Path file = marker(versionId);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(".mclite.properties.tmp");
        try (OutputStream out = Files.newOutputStream(temp)) {
            properties.store(out, "MC Lite install marker");
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Path resolveUnder(Path base, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("路径为空");
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path child;
        try {
            child = Path.of(relative);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("非法路径");
        }
        if (child.isAbsolute()) {
            throw new IllegalArgumentException("不允许绝对路径");
        }
        Path resolved = normalizedBase.resolve(child).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("路径越界");
        }
        return resolved;
    }

    public static String safeId(String id) {
        if (!VersionIds.valid(id)) {
            throw new IllegalArgumentException("非法版本标识");
        }
        return id;
    }

    public static String safeFileName(String name) {
        if (!VersionIds.validFileName(name)) {
            throw new IllegalArgumentException("非法文件名");
        }
        return name;
    }
}
