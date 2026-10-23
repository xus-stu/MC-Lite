package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class LoaderInstaller {
    private final Config config;
    private final GamePaths paths;
    private final ProgressSink progress;
    private final CancellationToken token;
    private final Net net;

    public LoaderInstaller(Config config, ProgressSink progress, CancellationToken token) {
        this.config = config;
        this.paths = new GamePaths(config.gameDir());
        this.progress = progress;
        this.token = token;
        this.net = new Net(progress, token);
    }

    public String install(String loaderType, String minecraftVersion, String loaderVersion,
                          JavaManager.JavaInfo java) throws IOException {
        if (RegionGate.restricted() && !Files.isRegularFile(paths.versionJson(minecraftVersion))) {
            RegionGate.requireGameDownloadAllowed();
        }
        if ("fabric".equals(loaderType)) {
            return installFromMeta("fabric",
                    "https://meta.fabricmc.net/v2/versions/loader/" + minecraftVersion + "/"
                            + loaderVersion + "/profile/json", minecraftVersion);
        }
        if ("quilt".equals(loaderType)) {
            return installFromMeta("quilt",
                    "https://meta.quiltmc.org/v3/versions/loader/" + minecraftVersion + "/"
                            + loaderVersion + "/profile/json", minecraftVersion);
        }
        if ("forge".equals(loaderType)) {
            return installForge(minecraftVersion, loaderVersion, java, false);
        }
        if ("neoforge".equals(loaderType)) {
            return installForge(minecraftVersion, loaderVersion, java, true);
        }
        throw new IOException("不支持的模组加载器: " + loaderType);
    }

    public String latestVersion(String type) throws IOException {
        return latestVersion(type, null);
    }

    public String latestVersion(String type, String minecraftVersion) throws IOException {
        if ("forge".equals(type)) {
            return latestForge(minecraftVersion, false);
        }
        if ("neoforge".equals(type)) {
            return latestForge(minecraftVersion, true);
        }
        String url = "fabric".equals(type)
                ? "https://meta.fabricmc.net/v2/versions/loader"
                : "quilt".equals(type)
                ? "https://meta.quiltmc.org/v3/versions/loader" : null;
        if (url == null) {
            throw new IOException("不支持的加载器: " + type);
        }
        byte[] bytes = net.getUnverifiedBytes(url, type + " 加载器版本列表");
        String fallback = null;
        for (Object raw : Json.arr(Json.parse(new String(bytes, StandardCharsets.UTF_8)))) {
            Map<String, Object> item = Json.obj(raw);
            String version = Json.str(item, "version");
            if (version == null) {
                continue;
            }
            if (fallback == null) {
                fallback = version;
            }
            if (Json.bool(item, "stable", false)) {
                return version;
            }
        }
        if (fallback == null) {
            throw new IOException("没有找到可用的 " + type + " 加载器版本");
        }
        return fallback;
    }

    private String latestForge(String minecraftVersion, boolean neoForge) throws IOException {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IOException("缺少 Minecraft 版本");
        }
        String url = neoForge
                ? "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
                : "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
        String prefix = neoForge ? neoPrefix(minecraftVersion) : minecraftVersion + "-";
        byte[] bytes = net.getUnverifiedBytes(url,
                (neoForge ? "NeoForge" : "Forge") + " 版本列表");
        String text = new String(bytes, StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<version>([^<]+)</version>").matcher(text);
        String best = null;
        while (matcher.find()) {
            String full = matcher.group(1).trim();
            if (!full.startsWith(prefix)) {
                continue;
            }
            String candidate = full.substring(prefix.length());
            if (candidate.isBlank()) {
                continue;
            }
            if (best == null || compareVersion(candidate, best) > 0) {
                best = candidate;
            }
        }
        if (best == null) {
            throw new IOException("没有找到 " + minecraftVersion + " 的最新 "
                    + (neoForge ? "NeoForge" : "Forge") + " 版本");
        }
        return best;
    }

    private static String neoPrefix(String minecraftVersion) {
        if ("1.20.1".equals(minecraftVersion)) {
            return "47.1.";
        }
        if (minecraftVersion.startsWith("1.20.")) {
            return "20." + minecraftVersion.substring("1.20.".length()) + ".";
        }
        if (minecraftVersion.startsWith("1.21")) {
            String[] parts = minecraftVersion.split("\\.");
            String minor = parts.length > 1 ? parts[1] : "0";
            String patch = parts.length > 2 ? parts[2] : "0";
            return minor + "." + patch + ".";
        }
        return minecraftVersion.replaceFirst("^1\\.", "") + ".";
    }

    private static int compareVersion(String a, String b) {
        String[] left = a.split("[.-]");
        String[] right = b.split("[.-]");
        int count = Math.max(left.length, right.length);
        for (int i = 0; i < count; i++) {
            int l = i < left.length ? parseInt(left[i]) : 0;
            int r = i < right.length ? parseInt(right[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return a.compareTo(b);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String installFromMeta(String type, String url, String minecraftVersion)
            throws IOException {
        progress.stage("获取 " + type + " 加载器配置…");
        byte[] bytes = net.getUnverifiedBytes(url, type + " 加载器配置");
        Map<String, Object> profile = Json.obj(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
        String id = Json.str(profile, "id");
        if (id == null || !id.matches("[A-Za-z0-9._ -]{1,80}")) {
            throw new IOException("加载器配置中的 id 非法");
        }
        profile.put("id", id);
        profile.put("type", "release");
        Path versionDir = paths.versionsDir().resolve(id);
        Files.createDirectories(versionDir);
        Files.writeString(versionDir.resolve(id + ".json"),
                prettyJson(profile), StandardCharsets.UTF_8);

        int count = 0;
        for (Object raw : Json.arr(profile.get("libraries"))) {
            token.check();
            Map<String, Object> library = Json.obj(raw);
            String name = Json.str(library, "name");
            String sha1 = Json.str(library, "sha1");
            String baseUrl = Json.str(library, "url", "https://libraries.minecraft.net/");
            long size = Json.lng(library, "size", -1);
            if (name == null) {
                continue;
            }
            String relative = mavenPath(name);
            if (sha1 == null || !sha1.matches("[0-9a-fA-F]{40}")) {
                progress.stage("获取加载器校验值 " + name + "…");
                byte[] sidecar = net.getUnverifiedBytes(baseUrl + relative + ".sha1",
                        "加载器库校验值");
                sha1 = new String(sidecar, StandardCharsets.UTF_8).trim().split("\\s+")[0];
            }
            if (sha1 == null || !sha1.matches("[0-9a-fA-F]{40}")) {
                throw new IOException("加载器库缺少 SHA-1: " + name);
            }
            Path target = GamePaths.resolveUnder(paths.librariesDir(), relative);
            net.download(baseUrl + relative, target, sha1, size, "加载器库 " + name, true);
            count++;
        }
        Log.info("安装 " + type + " 加载器 " + id + "，库文件 " + count + " 个");
        return id;
    }

    private String installForge(String minecraftVersion, String loaderVersion,
                                JavaManager.JavaInfo java, boolean neoForge) throws IOException {
        String url;
        if (neoForge) {
            url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                    + loaderVersion + "/neoforge-" + loaderVersion + "-installer.jar";
        } else {
            String full = minecraftVersion + "-" + loaderVersion;
            url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + full
                    + "/forge-" + full + "-installer.jar";
        }
        progress.stage("获取 " + (neoForge ? "NeoForge" : "Forge") + " 安装器…");
        byte[] shaBytes = net.getUnverifiedBytes(url + ".sha1", "加载器校验值");
        String sha1 = new String(shaBytes, StandardCharsets.UTF_8).trim().split("\\s+")[0];
        if (!sha1.matches("[0-9a-fA-F]{40}")) {
            throw new IOException("官方加载器安装器没有有效 SHA-1");
        }
        Path temp = config.cacheDir().resolve("loader-installer.jar");
        net.download(url, temp, sha1, -1, "加载器安装器", true);

        progress.stage("运行官方加载器安装器…");
        Files.createDirectories(config.logsDir());
        Path log = config.logsDir().resolve("loader-install.log");
        ProcessBuilder builder = new ProcessBuilder(java.path().toString(), "-jar",
                temp.toString(), "--installClient", paths.gameDir().toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        Process process = builder.start();
        try {
            if (!process.waitFor(8, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("加载器安装超时，详见 " + log);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new CancellationToken.CancelledException();
        }
        if (process.exitValue() != 0) {
            throw new IOException("加载器安装器退出码 " + process.exitValue() + "，详见 " + log);
        }
        String prefix = neoForge ? "neoforge-" : minecraftVersion + "-forge";
        try (var dirs = Files.newDirectoryStream(paths.versionsDir())) {
            String found = null;
            for (Path dir : dirs) {
                String id = dir.getFileName().toString();
                if (id.startsWith(prefix) && Files.isRegularFile(dir.resolve(id + ".json"))) {
                    found = id;
                }
            }
            if (found != null) {
                return found;
            }
        }
        throw new IOException("安装器结束，但没有找到生成的版本 JSON");
    }

    private static String mavenPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("非法 Maven 坐标: " + name);
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version
                + classifier + ".jar";
    }

    private static String prettyJson(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{\n");
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index++ > 0) {
                    out.append(",\n");
                }
                out.append("  \"").append(escape(String.valueOf(entry.getKey()))).append("\": ");
                out.append(prettyJson(entry.getValue()));
            }
            return out.append("\n}").toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(",\n");
                }
                out.append("  ").append(prettyJson(list.get(i)));
            }
            return out.append("\n]").toString();
        }
        if (value instanceof String text) {
            return "\"" + escape(text) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "null";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
