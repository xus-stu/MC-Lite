package mclite;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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

    public static void download(Config config, Consumer<String> status) {
        new Thread(() -> {
            Path target = module(config);
            Path temp = target.resolveSibling(ASSET + ".part");
            try {
                Files.createDirectories(target.getParent());
                String url = "https://github.com/xus-stu/MC-Lite/releases/download/v"
                        + Version.VALUE + "/" + ASSET;
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(3))
                        .header("User-Agent", "MCLite/" + Version.VALUE)
                        .GET().build();
                HttpResponse<Path> response = client.send(request,
                        HttpResponse.BodyHandlers.ofFile(temp));
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                SwingUtilities.invokeLater(() -> status.accept("跑分模块已下载"));
            } catch (Exception ex) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
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
            new ProcessBuilder(java.toString(), "-Xmx512m", "-jar", module.toString()).start();
            status.accept("跑分模块已启动");
        } catch (Exception ex) {
            status.accept("启动失败：" + ex.getMessage());
        }
    }
}
