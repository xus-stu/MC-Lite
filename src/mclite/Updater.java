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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipException;
import javax.swing.SwingUtilities;

public final class Updater {
    private static final String REPO = "xus-stu/MC-Lite";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long MAX_UPDATE_BYTES = 128L * 1024 * 1024;

    private static volatile String ignoredVersion;
    private static volatile Path parentLaunch;
    private static volatile boolean parentCaptured;

    private record Release(String version, Map<String, String> digests) {
        String digest(String asset) {
            return digests.get(asset);
        }
    }

    private Updater() {
    }

    public static void captureParent() {
        if (parentCaptured) {
            return;
        }
        parentCaptured = true;
        parentLaunch = readParentLaunchJar();
        if (parentLaunch != null) {
            Log.info("检测到父启动器: " + parentLaunch);
        }
    }

    public static boolean applyPending(String[] args, Config config) {
        if (contains(args, "--self-test") || contains(args, "--screenshot")) {
            return false;
        }
        captureParent();
        Path root = config.appHome().resolve("updates").toAbsolutePath().normalize();
        Path pending = root.resolve("pending.properties");
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
        Path staged = safeStagedPath(jar, root);
        boolean runningStaged = staged != null && isRunningFrom(staged);
        String currentChannel = channel();
        if (!isSafeVersion(version) || staged == null || !channel.equals(currentChannel)
                || compare(version, Version.VALUE) <= 0) {
            if (runningStaged) {
                // 1.1.33 及更早的旧版更新器只会从缓存启动，不会替换安装位置。
                // 这里通过父进程命令行找到旧 jar，把当前版本补装回去。
                if (isSafeVersion(version) && compare(version, Version.VALUE) == 0
                        && migrateFromParent(config, staged, version)) {
                    return false;
                }
                // 安装目录不可写时会持续从缓存启动，此时不能删掉正在使用的 jar。
                return false;
            }
            clean(pending, staged, root);
            return false;
        }
        if (!Files.isRegularFile(staged)) {
            clean(pending, staged, root);
            return false;
        }
        Path original = staged;
        Path installJar = staged;
        boolean unrestricted;
        try {
            if ("normal".equals(installChannel(config, channel))) {
                // 下载后用户可能切换了“移除地区限制”，这里按当前设置补一次转换。
                if (!isUnrestricted(installJar)) {
                    Path patched = installJar.resolveSibling(
                            "MCLite_v" + version + "-patched.jar");
                    RegionPatcher.makeUnrestricted(installJar, patched);
                    installJar = patched;
                }
            }
            unrestricted = isUnrestricted(installJar);
            verifyJar(installJar, version, unrestricted);
        } catch (Exception ex) {
            Log.warn("启动器更新包校验失败，已丢弃: " + ex.getMessage());
            clean(pending, installJar, root);
            if (!samePath(installJar, original)) {
                try {
                    Files.deleteIfExists(original);
                } catch (IOException ignored) {
                }
            }
            return false;
        }
        UpdateInstaller.Installed installed;
        try {
            installed = UpdateInstaller.install(installJar, version,
                    unrestricted ? "normal" : "region");
        } catch (Exception ex) {
            Log.warn("安装启动器更新失败，继续从缓存启动: " + ex.getMessage());
            installJar = original;
            installed = UpdateInstaller.Installed.staged(original);
        }
        boolean launched = launch(installed, args, config);
        if (launched && !samePath(installed.launch(), installJar)) {
            clean(pending, installJar, root);
            if (!samePath(installJar, original)) {
                try {
                    Files.deleteIfExists(original);
                } catch (IOException ignored) {
                }
            }
        }
        return launched;
    }

    private static boolean launch(UpdateInstaller.Installed installed, String[] args,
                                  Config config) {
        try {
            if (installed.appMode() && isMac() && installed.appBundle() != null
                    && defaultAppHome(config.appHome())) {
                // 从应用包重启，Dock 图标、应用名和 Java 探测都与双击一致。
                List<String> command = new ArrayList<>();
                command.add("open");
                command.add("-n");
                command.add(installed.appBundle().toString());
                if (args.length > 0) {
                    command.add("--args");
                    command.addAll(List.of(args));
                }
                Process process = new ProcessBuilder(command).inheritIO().start();
                boolean exited = process.waitFor(15, TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly();
                    return true;
                }
                if (process.exitValue() == 0) {
                    return true;
                }
                Log.warn("open 重启应用包失败（退出码 " + process.exitValue() + "），改用 java 启动");
            }
            Path java = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java");
            if (!Files.isRegularFile(java)) {
                Log.warn("找不到当前 Java 运行时，无法重启启动器: " + java);
                return false;
            }
            List<String> command = new ArrayList<>();
            command.add(java.toString());
            command.add("-Xms32m");
            command.add("-Xmx256m");
            command.add("-Dsun.java2d.opengl=false");
            command.add("-Dsun.java2d.metal=false");
            command.add("-Dmclite.home=" + config.appHome());
            command.add("-XX:+UseSerialGC");
            command.add("-XX:MaxMetaspaceSize=96m");
            command.add("-XX:ReservedCodeCacheSize=48m");
            command.add("-XX:-UsePerfData");
            command.add("-XX:TieredStopAtLevel=1");
            command.add("-jar");
            command.add(installed.launch().toString());
            for (String arg : args) {
                command.add(arg);
            }
            new ProcessBuilder(command).inheritIO().start();
            return true;
        } catch (Exception ex) {
            Log.warn("重启启动器失败: " + ex.getMessage());
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
            Release release = latestFromApi(client);
            String version = release == null ? null : release.version();
            if (version == null || version.isBlank()) {
                version = latestFromHtml(client);
            }
            if (!isSafeVersion(version) || compare(version, Version.VALUE) <= 0) {
                return;
            }
            String asset = "MCLite_v" + version + "-region.jar";
            String url = "https://github.com/" + REPO + "/releases/download/v"
                    + version + "/" + asset;
            String digest = release == null ? null : release.digest(asset);
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
                    Thread downloader = new Thread(() -> downloadUpdate(
                            config, downloadVersion, channel, downloadUrls, digest,
                            taskCenter, refreshTasks),
                            "mclite-update-download");
                    downloader.setDaemon(true);
                    downloader.start();
                });
                return;
            }
            downloadUpdate(config, downloadVersion, channel, downloadUrls, digest,
                    taskCenter, refreshTasks);
        } catch (Exception ex) {
            Log.warn("检查启动器更新失败: " + ex.getMessage());
        }
    }

    private static Release latestFromApi(HttpClient client) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MCLite/" + Version.VALUE)
                    .GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            Map<String, Object> release = Json.obj(Json.parse(response.body()));
            String tag = Json.str(release, "tag_name", "").trim();
            String version = tag.startsWith("v") ? tag.substring(1) : tag;
            if (!isSafeVersion(version)) {
                return null;
            }
            Map<String, String> digests = new HashMap<>();
            for (Object item : Json.arr(release.get("assets"))) {
                Map<String, Object> asset = Json.obj(item);
                String name = Json.str(asset, "name", "").trim();
                String digest = Json.str(asset, "digest", "").trim();
                if (!name.isEmpty() && !digest.isEmpty()) {
                    digests.put(name, digest);
                }
            }
            return new Release(version, digests);
        } catch (Exception ex) {
            Log.warn("查询 GitHub Release 失败: " + ex.getMessage());
            return null;
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
                String version = matcher.group(1);
                return isSafeVersion(version) ? version : null;
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
            if (length > MAX_UPDATE_BYTES) {
                throw new IOException("更新文件异常过大: " + length + " 字节");
            }
            progress.total(length);
            progress.stage("下载更新文件…");
            Path folder = config.appHome().resolve("updates/v" + version);
            Files.createDirectories(folder);
            boolean patch = "normal".equals(channel) && config.removeRegionLimits();
            String asset = "MCLite_v" + version + "-region.jar";
            Path source = folder.resolve(asset + ".part");
            Path target = folder.resolve(patch ? "MCLite_v" + version + ".jar" : asset);
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
                    if (progress.done() > MAX_UPDATE_BYTES) {
                        throw new IOException("更新文件异常过大");
                    }
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
                    throw new IOException("更新文件哈希不匹配，已拒绝安装");
                }
            }
            progress.stage("校验更新文件…");
            taskCenter.update(task, progress);
            verifyJar(source, version, false);
            if (patch) {
                task.stage("移除地区限制…");
                progress.stage("移除地区限制…");
                taskCenter.update(task, progress);
                RegionPatcher.makeUnrestricted(source, target);
                Files.deleteIfExists(source);
                verifyJar(target, version, true);
            } else {
                moveReplace(source, target);
                verifyJar(target, version, false);
            }
            progress.stage("准备更新…");
            taskCenter.update(task, progress);
            if (refreshTasks != null) {
                SwingUtilities.invokeLater(refreshTasks);
            }
            Properties pending = new Properties();
            pending.setProperty("version", version);
            pending.setProperty("channel", channel);
            pending.setProperty("jar", target.toString());
            Path marker = config.appHome().resolve("updates/pending.properties");
            Files.createDirectories(marker.getParent());
            Path temp = Files.createTempFile(marker.getParent(), "pending.", ".tmp");
            try (var out = Files.newOutputStream(temp)) {
                pending.store(out, null);
            }
            moveReplace(temp, marker);
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

    private static boolean migrateFromParent(Config config, Path staged, String version) {
        Path parent = parentLaunch;
        if (parent == null) {
            Log.warn("未能读取旧版安装位置，更新继续从缓存运行");
            return false;
        }
        if (samePath(parent, staged) || !Files.isRegularFile(parent)) {
            Log.warn("旧版安装位置无效: " + parent);
            return false;
        }
        String name = parent.getFileName().toString();
        if (!name.startsWith("MCLite_v") || !name.endsWith(".jar")) {
            Log.warn("父进程不是启动器 jar: " + parent);
            return false;
        }
        Path updates = config.appHome().resolve("updates").toAbsolutePath().normalize();
        if (parent.startsWith(updates)) {
            Log.warn("父进程位于更新缓存目录，跳过迁移: " + parent);
            return false;
        }
        String oldVersion = jarVersion(parent);
        if (oldVersion == null || compare(oldVersion, version) >= 0) {
            Log.warn("旧版安装版本无效: " + oldVersion + " / " + parent);
            return false;
        }
        try {
            boolean unrestricted = isUnrestricted(staged);
            verifyJar(staged, version, unrestricted);
            UpdateInstaller.Installed installed = UpdateInstaller.installFrom(
                    parent, staged, version, unrestricted ? "normal" : "region");
            if (samePath(installed.launch(), staged)) {
                return false;
            }
            Log.info("检测到旧版更新缓存，已安装到 " + installed.launch());
            try {
                Files.deleteIfExists(updates.resolve("pending.properties"));
            } catch (IOException ignored) {
            }
            return true;
        } catch (Exception ex) {
            Log.warn("迁移旧版更新失败: " + ex.getMessage());
            return false;
        }
    }

    private static Path readParentLaunchJar() {
        try {
            var parent = ProcessHandle.current().parent();
            if (parent.isEmpty()) {
                return null;
            }
            var info = parent.get().info();
            String jar = null;
            String home = null;
            String[] arguments = info.arguments().orElse(null);
            if (arguments != null) {
                for (String argument : arguments) {
                    if (argument.startsWith("-Dmclite.home=")) {
                        home = argument.substring("-Dmclite.home=".length());
                    }
                }
                for (int i = 0; i + 1 < arguments.length; i++) {
                    if ("-jar".equals(arguments[i])) {
                        jar = arguments[i + 1];
                        break;
                    }
                }
            }
            if (jar == null) {
                String command = info.commandLine().orElse("");
                int index = command.indexOf("-jar ");
                if (index >= 0) {
                    String rest = command.substring(index + 5).trim();
                    if (rest.startsWith("\"")) {
                        int end = rest.indexOf('"', 1);
                        jar = end > 0 ? rest.substring(1, end) : null;
                    } else {
                        jar = rest.split("\\s+", 2)[0];
                    }
                }
                if (home == null) {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("-Dmclite\\.home=(\\S+)").matcher(command);
                    if (matcher.find()) {
                        home = matcher.group(1);
                    }
                }
            }
            if (jar != null && home != null && !home.isBlank()
                    && System.getProperty("mclite.home") == null) {
                // 旧版更新器重新启动时不会传递 -Dmclite.home，这里从父进程补回来。
                System.setProperty("mclite.home", home);
            }
            if (jar == null || jar.isBlank()) {
                return null;
            }
            jar = jar.trim();
            if (jar.startsWith("\"") && jar.endsWith("\"") && jar.length() > 1) {
                jar = jar.substring(1, jar.length() - 1);
            }
            Path path = Path.of(jar);
            if (!path.isAbsolute()) {
                path = Path.of(System.getProperty("user.dir", ".")).resolve(path);
            }
            return path.toAbsolutePath().normalize();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String jarVersion(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = file.getJarEntry("mclite/version.properties");
            if (entry == null) {
                return null;
            }
            Properties properties = new Properties();
            try (InputStream in = file.getInputStream(entry)) {
                properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            return properties.getProperty("version", "").trim();
        } catch (IOException ex) {
            return null;
        }
    }

    static boolean isUnrestricted(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getJarEntry("mclite/unrestricted") != null
                    && file.getJarEntry("mclite/region.lock") == null;
        } catch (IOException ex) {
            return false;
        }
    }

    static void verifyJar(Path jar, String expectedVersion, boolean unrestricted)
            throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            String main = manifest == null ? ""
                    : manifest.getMainAttributes().getValue("Main-Class");
            if (main == null || main.isBlank()) {
                throw new IOException("更新包缺少 Main-Class");
            }
            boolean lock = file.getJarEntry("mclite/region.lock") != null;
            boolean marker = file.getJarEntry("mclite/unrestricted") != null;
            if (unrestricted) {
                if (lock || !marker) {
                    throw new IOException("普通版更新包没有正确移除地区限制");
                }
                if (!"Main".equals(main.trim())) {
                    throw new IOException("普通版更新包入口不正确: " + main);
                }
            } else {
                if (!lock || marker) {
                    throw new IOException("地区版更新包结构不正确");
                }
                if (!main.contains("RegionLauncherApp")) {
                    throw new IOException("地区版更新包入口不正确: " + main);
                }
            }
            JarEntry versionEntry = file.getJarEntry("mclite/version.properties");
            if (versionEntry == null) {
                throw new IOException("更新包缺少版本信息");
            }
            Properties properties = new Properties();
            try (InputStream in = file.getInputStream(versionEntry)) {
                properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            String actual = properties.getProperty("version", "").trim();
            if (!expectedVersion.equals(actual)) {
                throw new IOException("更新包版本不一致: 期望 " + expectedVersion
                        + "，实际 " + actual);
            }
        } catch (ZipException ex) {
            throw new IOException("更新包不是有效的 jar 文件", ex);
        }
    }

    public static void cleanOldVersionsLater(Config config) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2500);
                Path current = UpdateInstaller.currentJar();
                if (current == null) {
                    return;
                }
                cleanInstalledJars(current);
                cleanStagedUpdates(config, current);
            } catch (Exception ignored) {
            }
        }, "mclite-clean-old-version");
        thread.setDaemon(true);
        thread.start();
    }

    private static void cleanInstalledJars(Path current) {
        Path dir = current.getParent();
        if (dir == null) {
            return;
        }
        String keep = current.getFileName().toString();
        boolean region = "region".equals(channel());
        if (isWindows()) {
            if (Files.isRegularFile(dir.resolve("Uninstall.exe"))) {
                deleteOldJars(dir, keep, false, true);
            } else if (keep.startsWith("MCLite_v") && keep.endsWith(".jar")) {
                // 免安装版没有 Uninstall.exe，也要清理上一版 jar。
                deleteOldJars(dir, keep, region, false);
            }
            return;
        }
        if (!keep.startsWith("MCLite_v") || !keep.endsWith(".jar")) {
            return;
        }
        deleteOldJars(dir, keep, region, false);
    }

    private static void deleteOldJars(Path dir, String keep, boolean region, boolean all) {
        try (var files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                if (!name.startsWith("MCLite_v") || !name.endsWith(".jar")
                        || name.equals(keep)) {
                    return;
                }
                if (!all && (name.endsWith("-region.jar") != region)) {
                    return;
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void cleanStagedUpdates(Config config, Path current) {
        Path root = config.appHome().resolve("updates").toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return;
        }
        Path keep = pendingJar(root);
        if (keep == null) {
            try {
                Files.deleteIfExists(root.resolve("pending.properties"));
            } catch (IOException ignored) {
            }
        }
        try (var entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                try (var files = Files.list(entry)) {
                    for (Path file : files.toList()) {
                        if (samePath(file, keep) || samePath(file, current)) {
                            continue;
                        }
                        String name = file.getFileName().toString();
                        if ((name.endsWith(".jar") || name.endsWith(".part")
                                || name.endsWith(".tmp")) && !recent(file)) {
                            try {
                                Files.deleteIfExists(file);
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
                try (var left = Files.list(entry)) {
                    if (left.findAny().isEmpty()) {
                        Files.deleteIfExists(entry);
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        // 只清理十分钟前的临时文件，避免删掉正在进行中的下载。
        try (var files = Files.list(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return (name.endsWith(".part") || name.endsWith(".tmp"))
                                && !name.equals("pending.properties");
                    })
                    .filter(path -> !recent(path))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static boolean recent(Path file) {
        try {
            return System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis()
                    < 10L * 60 * 1000;
        } catch (IOException ex) {
            return true;
        }
    }

    private static Path pendingJar(Path root) {
        Path marker = root.resolve("pending.properties");
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        Properties properties = new Properties();
        try (var in = Files.newInputStream(marker)) {
            properties.load(in);
        } catch (IOException ex) {
            return null;
        }
        return safeStagedPath(properties.getProperty("jar", "").trim(), root);
    }

    private static Path safeStagedPath(String jar, Path root) {
        if (jar == null || jar.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(jar).toAbsolutePath().normalize();
            return path.startsWith(root) ? path : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void clean(Path marker, Path jar, Path root) {
        try {
            Files.deleteIfExists(marker);
        } catch (IOException ignored) {
        }
        if (jar == null || !jar.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(jar);
            Path parent = jar.getParent();
            if (parent != null && !parent.equals(root)) {
                try (var entries = Files.list(parent)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isRunningFrom(Path jar) {
        return samePath(UpdateInstaller.currentJar(), jar);
    }

    private static boolean samePath(Path left, Path right) {
        if (left == null || right == null) {
            return false;
        }
        Path a = left.toAbsolutePath().normalize();
        Path b = right.toAbsolutePath().normalize();
        if (a.equals(b)) {
            return true;
        }
        try {
            return Files.isSameFile(a, b);
        } catch (IOException ex) {
            return false;
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException
                 | UnsupportedOperationException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String installChannel(Config config, String channel) {
        if ("region".equals(channel)) {
            return "region";
        }
        return config.removeRegionLimits() ? "normal" : "region";
    }

    private static boolean defaultAppHome(Path appHome) {
        Path normal = Path.of(System.getProperty("user.home"), ".mclite")
                .toAbsolutePath().normalize();
        return appHome.toAbsolutePath().normalize().equals(normal);
    }

    private static boolean isSafeVersion(String version) {
        return version != null && version.matches("[0-9][0-9A-Za-z._-]{0,63}");
    }

    private static String channel() {
        return RegionGate.locked() ? "region" : "normal";
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

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
