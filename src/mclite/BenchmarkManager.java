package mclite;

import javax.swing.SwingUtilities;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public final class BenchmarkManager {
    private static final String ASSET = "MCLiteBenchmark.jar";

    private BenchmarkManager() {
    }

    public static Path module(Config config) {
        return config.appHome().resolve("modules").resolve(ASSET);
    }

    public static boolean installed(Config config) {
        return Files.isRegularFile(module(config));
    }

    public static void download(Config config, TaskCenter center, Runnable refresh,
                                Consumer<String> status) {
        DownloadTask task = center.start("下载跑分模块 · " + Version.VALUE,
                "GitHub Releases");
        if (refresh != null) {
            SwingUtilities.invokeLater(refresh);
        }
        new Thread(() -> {
            Path target = module(config);
            Path temp = target.resolveSibling(ASSET + ".part");
            TaskProgress progress = new TaskProgress();
            progress.stage("连接跑分模块下载源…");
            try {
                Files.createDirectories(target.getParent());
                String base = "https://github.com/xus-stu/MC-Lite/releases/download/v"
                        + Version.VALUE + "/" + ASSET;
                List<String> urls = new ArrayList<>();
                urls.add(base);
                for (String prefix : new String[]{
                        "https://ghproxy.net/",
                        "https://gh-proxy.com/",
                        "https://ghfast.top/",
                        "https://github.moeyy.xyz/"}) {
                    urls.add(prefix + base);
                }
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(12))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpResponse<InputStream> response = null;
                String used = "";
                for (String url : urls) {
                    try {
                        task.stage("连接下载源 · " + host(url));
                        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                                .timeout(Duration.ofMinutes(2))
                                .header("User-Agent", "MCLite/" + Version.VALUE)
                                .GET().build();
                        HttpResponse<InputStream> candidate = client.send(request,
                                HttpResponse.BodyHandlers.ofInputStream());
                        if (candidate.statusCode() == 200) {
                            response = candidate;
                            used = host(url);
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (response == null) {
                    throw new java.io.IOException("所有下载源均不可用");
                }
                String sourceHost = used;
                long length = response.headers().firstValueAsLong("content-length").orElse(-1);
                progress.total(length);
                task.stage("下载跑分模块 · " + sourceHost);
                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(temp)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        out.write(buffer, 0, read);
                        progress.add(read);
                        center.update(task, progress);
                        if (refresh != null) {
                            SwingUtilities.invokeLater(refresh);
                        }
                    }
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                center.finish(task);
                task.stage("跑分模块下载完成");
                SwingUtilities.invokeLater(() -> status.accept("跑分模块已下载 · " + sourceHost));
            } catch (Exception ex) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                }
                center.fail(task, ex.getMessage());
                SwingUtilities.invokeLater(() -> status.accept("下载失败：" + ex.getMessage()));
            }
        }, "mclite-benchmark-download").start();
    }

    public static void run(Config config, Consumer<String> status) {
        Path module = module(config);
        if (!Files.isRegularFile(module)) {
            status.accept("请先下载跑分模块");
            return;
        }
        try {
            Path java = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "javaw.exe" : "java");
            new ProcessBuilder(java.toString(), "-Xmx512m", "-jar", module.toString(),
                    "--bg=" + hex(Theme.BG), "--card=" + hex(Theme.CARD),
                    "--text=" + hex(Theme.TEXT), "--muted=" + hex(Theme.MUTED),
                    "--dim=" + hex(Theme.DIM), "--accent=" + hex(Theme.ACCENT)).start();
            status.accept("跑分模块已启动");
        } catch (Exception ex) {
            status.accept("启动失败：" + ex.getMessage());
        }
    }

    private static String hex(java.awt.Color color) {
        return String.format("%06X", color.getRGB() & 0xFFFFFF);
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "下载源" : host;
        } catch (Exception ex) {
            return "下载源";
        }
    }
}
