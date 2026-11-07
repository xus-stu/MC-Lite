package mclite;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** 下载、校验、安装/卸载 MCLite 岛模组。不联网的纯逻辑部分可单测。 */
final class IslandInstaller {

    private static final String PREFIX = "mclite-island-";

    private IslandInstaller() {
    }

    static Path modsDir(Config config, InstalledInstance instance) {
        return config.effectiveGameDir(instance.id()).resolve("mods");
    }

    static Path install(Net net, Config config, InstalledInstance instance,
                        IslandCatalog.Build build) throws IOException {
        if (!build.mc().equals(instance.mcVersion())) {
            throw new IOException("组件 MC 版本不匹配: " + build.mc() + " vs " + instance.mcVersion());
        }
        if (!build.loader().equals(instance.loader())) {
            throw new IOException("组件加载器不匹配: " + build.loader() + " vs " + instance.loader());
        }
        byte[] bytes = null;
        IOException last = null;
        for (String url : GithubSources.candidates(build.url())) {
            try {
                byte[] candidate = net.getUnverifiedBytes(url, "MCLite 岛 v" + build.version());
                if (candidate.length != build.size()) {
                    last = new IOException("组件大小校验失败: 期望 " + build.size()
                            + "，实际 " + candidate.length);
                    continue;
                }
                if (!sha256Hex(candidate).equals(build.sha256())) {
                    last = new IOException("组件 SHA-256 校验失败，已拒绝安装");
                    continue;
                }
                bytes = candidate;
                break;
            } catch (IOException ex) {
                last = ex;
            }
        }
        if (bytes == null) {
            throw last == null ? new IOException("组件下载失败") : last;
        }
        Path mods = modsDir(config, instance);
        Files.createDirectories(mods);
        String fileName = safeFileName(build);
        Path target = mods.resolve(fileName).normalize();
        if (!target.startsWith(mods.toAbsolutePath().normalize())) {
            throw new IOException("组件文件名越出 mods 目录，已拒绝");
        }
        Path temp = mods.resolve(fileName + ".part");
        Files.write(temp, bytes);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        // 新 jar 落盘成功后再清理旧版本；清理失败不影响本次安装成功，
        // 残留的旧 jar 会被 uninstall() 一并处理。
        try {
            cleanupOldJars(mods, fileName);
        } catch (IOException ignored) {
            // 文件被占用/权限不足时保留旧版本
        }
        return target;
    }

    static int uninstall(Config config, InstalledInstance instance) throws IOException {
        Path mods = modsDir(config, instance);
        if (!Files.isDirectory(mods)) {
            return 0;
        }
        int removed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (!name.startsWith(PREFIX)) {
                    continue;
                }
                if (name.endsWith(".jar") || name.endsWith(".jar.bak") || name.endsWith(".part")) {
                    Files.deleteIfExists(file);
                    removed++;
                }
            }
        }
        return removed;
    }

    static List<Path> installedJars(Config config, InstalledInstance instance) throws IOException {
        List<Path> result = new ArrayList<>();
        Path mods = modsDir(config, instance);
        if (!Files.isDirectory(mods)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, PREFIX + "*.jar")) {
            for (Path jar : stream) {
                result.add(jar);
            }
        }
        result.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return result;
    }

    /** 只允许 .jar 文件名，并且必须是 mclite-island- 前缀，防止清单里塞路径。 */
    static String safeFileName(IslandCatalog.Build build) {
        String name = "";
        try {
            String path = URI.create(build.url()).getPath();
            int slash = path.lastIndexOf('/');
            name = slash >= 0 ? path.substring(slash + 1) : path;
        } catch (Exception ignored) {
            // 用兜底名字
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")
                || !name.startsWith(PREFIX)
                || !name.matches("[A-Za-z0-9._-]+")) {
            name = PREFIX + safePart(build.mc()) + "-" + safePart(build.loader())
                    + "-" + safePart(build.version()) + ".jar";
        }
        return name;
    }

    private static String safePart(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    static void cleanupOldJars(Path mods, String keepName) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, PREFIX + "*.jar")) {
            for (Path jar : stream) {
                if (jar.getFileName().toString().equals(keepName)) {
                    continue;
                }
                Path backup = jar.resolveSibling(jar.getFileName() + ".bak");
                Files.deleteIfExists(backup);
                Files.move(jar, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
