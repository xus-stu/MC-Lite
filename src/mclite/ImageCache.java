package mclite;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageCache {
    private static final int MAX_ENTRIES = 80;
    private static final ConcurrentHashMap<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentLinkedDeque<String> ORDER =
            new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "mclite-icons");
        thread.setDaemon(true);
        return thread;
    });

    private ImageCache() {
    }

    public static Image get(String url) {
        return url == null ? null : CACHE.get(url);
    }

    public static void load(String url, Runnable onLoaded) {
        if (url == null || url.isBlank()) {
            return;
        }
        Image cached = CACHE.get(url);
        if (cached != null) {
            onLoaded.run();
            return;
        }
        if (!LOADING.add(url)) {
            return;
        }
        POOL.submit(() -> {
            try {
                byte[] bytes = new Net(ProgressSink.NOOP, new CancellationToken())
                        .getUnverifiedBytes(url, "项目图标");
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) {
                    image = decodeWithSystemTool(bytes);
                }
                if (image != null) {
                    CACHE.put(url, image);
                    ORDER.addLast(url);
                    while (CACHE.size() > MAX_ENTRIES) {
                        String oldest = ORDER.pollFirst();
                        if (oldest == null) {
                            break;
                        }
                        CACHE.remove(oldest);
                    }
                    SwingUtilities.invokeLater(onLoaded);
                }
            } catch (Throwable ex) {
                Log.warn("图标加载失败: " + ex);
            } finally {
                LOADING.remove(url);
            }
        });
    }

    private static BufferedImage decodeWithSystemTool(byte[] bytes) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return null;
        }
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("mclite-icon-", ".webp");
            output = Files.createTempFile("mclite-icon-", ".png");
            Files.write(input, bytes);
            Process process = new ProcessBuilder("/usr/bin/sips", "-s", "format", "png",
                    input.toString(), "--out", output.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    || process.exitValue() != 0) {
                process.destroyForcibly();
                return null;
            }
            return ImageIO.read(output.toFile());
        } catch (Exception ex) {
            return null;
        } finally {
            try {
                if (input != null) {
                    Files.deleteIfExists(input);
                }
                if (output != null) {
                    Files.deleteIfExists(output);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
