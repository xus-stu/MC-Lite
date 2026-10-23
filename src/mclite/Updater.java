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
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class Updater {
    private static final String REPO = "xus-stu/MC-Lite";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    private Updater() {
    }

    public static boolean applyPending(String[] args, Config config) {
        if (contains(args, "--self-test") || contains(args, "--screenshot")) {
            return false;
        }
        Path pending = config.appHome().resolve("updates/pending.properties");
        if (!Files.isRegularFile(pending)) {
            return false;
        }
        Properties properties = new Properties();
        try (var in = Files.newInputStream(pending)) {
            properties.load(in);
        } catch (IOException ex) {
            return false;
        }
        String version = properties.getProperty("version", "").trim();
        String channel = properties.getProperty("channel", "").trim();
        String jar = properties.getProperty("jar", "").trim();
        String currentChannel = channel();
        if (version.isBlank() || jar.isBlank() || !channel.equals(currentChannel)
                || compare(version, Version.VALUE) <= 0) {
            clean(pending, jar);
            return false;
        }
        Path staged = Path.of(jar).toAbsolutePath().normalize();
        Path root = config.appHome().resolve("updates").toAbsolutePath().normalize();
        if (!staged.startsWith(root) || !Files.isRegularFile(staged)) {
            clean(pending, jar);
            return false;
        }
        try {
            Path java = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java");
            if (!Files.isRegularFile(java)) {
                return false;
            }
            List<String> command = new ArrayList<>();
            command.add(java.toString());
            command.add("-Xms32m");
            command.add("-Xmx256m");
            command.add("-Dsun.java2d.opengl=false");
            command.add("-Dsun.java2d.metal=false");
            command.add("-XX:+UseSerialGC");
            command.add("-XX:MaxMetaspaceSize=96m");
            command.add("-XX:ReservedCodeCacheSize=48m");
            command.add("-XX:-UsePerfData");
            command.add("-XX:TieredStopAtLevel=1");
            command.add("-jar");
            command.add(staged.toString());
            for (String arg : args) {
                command.add(arg);
            }
            new ProcessBuilder(command).inheritIO().start();
            return true;
        } catch (Exception ex) {
            Log.warn("应用启动器更新失败: " + ex.getMessage());
            return false;
        }
    }

    public static void startBackground(Config config) {
        if (!config.autoUpdate()) {
            return;
        }
        Thread thread = new Thread(() -> check(config), "mclite-update");
        thread.setDaemon(true);
        thread.start();
    }

    private static void check(Config config) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MCLite/" + Version.VALUE)
                    .GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }
            Map<String, Object> release = Json.obj(Json.parse(response.body()));
            String tag = Json.str(release, "tag_name", "").trim();
            String version = tag.startsWith("v") ? tag.substring(1) : tag;
            if (version.isBlank() || compare(version, Version.VALUE) <= 0) {
                return;
            }
            String channel = channel();
            String wanted = "MCLite_v" + version + ("region".equals(channel) ? "-region.jar" : ".jar");
            String url = null;
            String digest = null;
            for (Object raw : Json.arr(release.get("assets"))) {
                Map<String, Object> asset = Json.obj(raw);
                if (wanted.equals(Json.str(asset, "name", ""))) {
                    url = Json.str(asset, "browser_download_url");
                    digest = Json.str(asset, "digest");
                    break;
                }
            }
            if (url == null) {
                return;
            }
            HttpRequest download = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "MCLite/" + Version.VALUE)
                    .GET().build();
            HttpResponse<byte[]> bytes = client.send(download,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (bytes.statusCode() != 200 || bytes.body().length < 1024) {
                return;
            }
            if (digest != null && digest.startsWith("sha256:")) {
                String actual = sha256(bytes.body());
                if (!digest.substring("sha256:".length()).equalsIgnoreCase(actual)) {
                    Log.warn("启动器更新哈希不匹配");
                    return;
                }
            }
            Path folder = config.appHome().resolve("updates/v" + version);
            Files.createDirectories(folder);
            Path target = folder.resolve(wanted);
            Path temp = folder.resolve(wanted + ".part");
            Files.write(temp, bytes.body());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            Properties pending = new Properties();
            pending.setProperty("version", version);
            pending.setProperty("channel", channel);
            pending.setProperty("jar", target.toString());
            Path marker = config.appHome().resolve("updates/pending.properties");
            try (var out = Files.newOutputStream(marker)) {
                pending.store(out, null);
            }
            Log.info("已下载启动器更新 v" + version + "，下次启动生效");
        } catch (Exception ex) {
            Log.warn("检查启动器更新失败: " + ex.getMessage());
        }
    }

    private static void clean(Path marker, String jar) {
        try {
            Files.deleteIfExists(marker);
            if (jar != null && !jar.isBlank()) {
                Files.deleteIfExists(Path.of(jar));
            }
        } catch (IOException ignored) {
        }
    }

    private static String channel() {
        return RegionGate.locked() ? "region" : "normal";
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static int compare(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        int count = Math.max(left.length, right.length);
        for (int i = 0; i < count; i++) {
            int l = i < left.length ? number(left[i]) : 0;
            int r = i < right.length ? number(right[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static boolean contains(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag) || arg.startsWith(flag + "=")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
