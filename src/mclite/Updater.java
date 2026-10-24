package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import javax.swing.SwingUtilities;

public final class Updater {
    private static final String REPO = "xus-stu/MC-Lite";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    private static volatile String ignoredVersion;

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
        Path staged;
        Path root;
        try {
            staged = Path.of(jar).toAbsolutePath().normalize();
            root = config.appHome().resolve("updates").toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            clean(pending, null);
            return false;
        }
        if (!staged.startsWith(root) || !Files.isRegularFile(staged)) {
            clean(pending, jar);
            return false;
        }
        try {
            Path launchJar = DesktopShortcut.updateIfInstalled(staged, version);
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
            command.add(launchJar.toString());
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

    public static void startBackground(Config config, java.awt.Component parent,
                                      TaskCenter taskCenter, Runnable refreshTasks) {
        if (!config.autoUpdate() && "region".equals(channel())) {
            return;
        }
        Thread thread = new Thread(() -> check(config, parent, taskCenter, refreshTasks), "mclite-update");
        thread.setDaemon(true);
        thread.start();
        java.util.Timer timer = new java.util.Timer("mclite-update-check", true);
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                check(config, parent, taskCenter, refreshTasks, false);
            }
        }, 30L * 60 * 1000, 30L * 60 * 1000);
    }

    public static void checkNow(Config config, java.awt.Component parent,
                                TaskCenter taskCenter, Runnable refreshTasks) {
        ignoredVersion = null;
        Thread thread = new Thread(() -> check(config, parent, taskCenter, refreshTasks, true),
                "mclite-update-now");
        thread.setDaemon(true);
        thread.start();
    }

    private static void check(Config config, java.awt.Component parent,
                              TaskCenter taskCenter, Runnable refreshTasks) {
        check(config, parent, taskCenter, refreshTasks, false);
    }

    private static void check(Config config, java.awt.Component parent,
                              TaskCenter taskCenter, Runnable refreshTasks, boolean manual) {
        try {
            Log.info("检查启动器更新…");
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            String channel = channel();
            boolean patch = "normal".equals(channel) && config.removeRegionLimits();
            String version = latestFromHtml(client);
            if (version == null || version.isBlank()) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "MCLite/" + Version.VALUE)
                        .GET().build();
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    Map<String, Object> release = Json.obj(Json.parse(response.body()));
                    String tag = Json.str(release, "tag_name", "").trim();
                    version = tag.startsWith("v") ? tag.substring(1) : tag;
                }
            }
            if (version == null || version.isBlank()
                    || compare(version, Version.VALUE) <= 0) {
                return;
            }
            String wanted = "MCLite_v" + version
                    + (("region".equals(channel) || patch) ? "-region.jar" : ".jar");
            String url = "https://github.com/" + REPO + "/releases/download/v"
                    + version + "/" + wanted;
            Log.info("发现启动器更新 V" + version + "（" + channel + "）");
            String downloadVersion = version;
            java.util.List<String> downloadUrls = updateUrls(url);
            if (!config.autoUpdate() && "normal".equals(channel)) {
                if (!manual && version.equals(ignoredVersion)) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    UpdateDialog dialog = new UpdateDialog(parent, Version.VALUE, downloadVersion);
                    dialog.setVisible(true);
                    if (!dialog.confirmed()) {
                        ignoredVersion = downloadVersion;
                        return;
                    }
                    if (dialog.confirmed()) {
                        Thread downloader = new Thread(() -> downloadUpdate(
                                config, downloadVersion, channel, downloadUrls, null,
                                taskCenter, refreshTasks),
                                "mclite-update-download");
                        downloader.setDaemon(true);
                        downloader.start();
                    }
                });
                return;
            }
            downloadUpdate(config, downloadVersion, channel, downloadUrls, null,
                    taskCenter, refreshTasks);
        } catch (Exception ex) {
            Log.warn("检查启动器更新失败: " + ex.getMessage());
        }
    }

    private static String latestFromHtml(HttpClient client) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://github.com/" + REPO + "/releases/latest"))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "MCLite/" + Version.VALUE)
                    .GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            String uri = response.uri().toString();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("/releases/tag/v?([0-9][0-9A-Za-z._-]*)").matcher(uri);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "更新源" : host;
        } catch (Exception ex) {
            return "更新源";
        }
    }

    private static java.util.List<String> updateUrls(String direct) {
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        urls.add(direct);
        for (String prefix : new String[]{
                "https://ghproxy.net/",
                "https://gh-proxy.com/",
                "https://ghfast.top/",
                "https://github.moeyy.xyz/"}) {
            urls.add(prefix + direct);
        }
        return new java.util.ArrayList<>(urls);
    }

    private static void downloadUpdate(Config config, String version, String channel,
                                       java.util.List<String> urls, String digest,
                                       TaskCenter taskCenter, Runnable refreshTasks) {
        DownloadTask task = taskCenter.start("启动器更新 · V" + version, "GitHub Releases");
        if (refreshTasks != null) {
            SwingUtilities.invokeLater(refreshTasks);
        }
        TaskProgress progress = new TaskProgress();
        progress.stage("连接更新源…");
        long[] lastUi = {0L};
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpResponse<InputStream> response = null;
            for (String url : urls) {
                try {
                    task.stage("连接更新源 · " + hostOf(url));
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofMinutes(2))
                            .header("User-Agent", "MCLite/" + Version.VALUE)
                            .GET().build();
                    HttpResponse<InputStream> candidate = client.send(request,
                            HttpResponse.BodyHandlers.ofInputStream());
                    if (candidate.statusCode() == 200) {
                        response = candidate;
                        break;
                    }
                } catch (Exception ex) {
                    Log.warn("更新源失败 " + hostOf(url) + ": " + ex.getMessage());
                }
            }
            if (response == null) {
                throw new IOException("所有更新源均不可用");
            }
            long length = response.headers().firstValueAsLong("content-length").orElse(-1);
            progress.total(length);
            progress.stage("下载更新文件…");
            Path folder = config.appHome().resolve("updates/v" + version);
            Files.createDirectories(folder);
            boolean patch = "normal".equals(channel) && config.removeRegionLimits();
            String wanted = "MCLite_v" + version
                    + (("region".equals(channel) || patch) ? "-region.jar" : ".jar");
            Path target = folder.resolve("MCLite_v" + version
                    + ("region".equals(channel) ? "-region.jar" : ".jar"));
            Path source = folder.resolve(wanted + ".part");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(source)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    out.write(buffer, 0, read);
                    sha.update(buffer, 0, read);
                    progress.add(read);
                    long now = System.currentTimeMillis();
                    if (now - lastUi[0] >= 50) {
                        lastUi[0] = now;
                        taskCenter.update(task, progress);
                        if (refreshTasks != null) {
                            SwingUtilities.invokeLater(refreshTasks);
                        }
                    }
                }
            }
            if (digest != null && digest.startsWith("sha256:")) {
                String actual = java.util.HexFormat.of().formatHex(sha.digest());
                if (!digest.substring("sha256:".length()).equalsIgnoreCase(actual)) {
                    Files.deleteIfExists(source);
                    throw new IOException("更新文件哈希不匹配");
                }
            }
            progress.stage("准备更新…");
            taskCenter.update(task, progress);
            if (refreshTasks != null) {
                SwingUtilities.invokeLater(refreshTasks);
            }
            if (patch) {
                task.stage("移除地区限制…");
                RegionPatcher.makeUnrestricted(source, target);
                Files.deleteIfExists(source);
            } else {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Properties pending = new Properties();
            pending.setProperty("version", version);
            pending.setProperty("channel", channel);
            pending.setProperty("jar", target.toString());
            Path marker = config.appHome().resolve("updates/pending.properties");
            try (var out = Files.newOutputStream(marker)) {
                pending.store(out, null);
            }
            task.stage("下载完成，下次启动生效");
            taskCenter.finish(task);
            if (refreshTasks != null) {
                SwingUtilities.invokeLater(refreshTasks);
            }
            Log.info("已下载启动器更新 v" + version + "，下次启动生效");
        } catch (Exception ex) {
            taskCenter.fail(task, ex.getMessage());
            if (refreshTasks != null) {
                SwingUtilities.invokeLater(refreshTasks);
            }
            Log.warn("下载启动器更新失败: " + ex.getMessage());
        }
    }

    public static void cleanOldVersionsLater() {
        if (!isWindows()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2500);
                Path source = Path.of(Updater.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
                if (!Files.isRegularFile(source)) {
                    return;
                }
                Path dir = source.getParent();
                if (dir == null || !Files.isRegularFile(dir.resolve("Uninstall.exe"))) {
                    return;
                }
                Path current = source;
                try (var files = Files.list(dir)) {
                    files.filter(path -> path.getFileName().toString()
                                    .matches("MCLite_v[0-9].*\\.jar"))
                            .filter(path -> !path.getFileName().toString()
                                    .equals("MCLite_v" + Version.VALUE + ".jar"))
                            .forEach(path -> {
                                try {
                                    Files.copy(current, path,
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                } catch (IOException ignored) {
                                }
                            });
                }
            } catch (Exception ignored) {
            }
        }, "mclite-clean-old-version");
        thread.setDaemon(true);
        thread.start();
    }

    private static void clean(Path marker, String jar) {
        try {
            Files.deleteIfExists(marker);
            if (jar != null && !jar.isBlank()) {
                try {
                    Files.deleteIfExists(Path.of(jar));
                } catch (RuntimeException ignored) {
                }
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
