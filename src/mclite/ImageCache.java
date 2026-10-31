package mclite;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageCache {
    private static final int MAX_ENTRIES = 40;
    private static final ConcurrentHashMap<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentLinkedDeque<String> ORDER =
            new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<String, List<Runnable>> WAITERS = new ConcurrentHashMap<>();
    private static volatile Path cacheDir;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "mclite-icons");
        thread.setDaemon(true);
        return thread;
    });

    private ImageCache() {
    }

    public static void configure(Path dir) {
        cacheDir = dir;
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
            WAITERS.computeIfAbsent(url, key -> new CopyOnWriteArrayList<>()).add(onLoaded);
            return;
        }
        POOL.submit(() -> {
            BufferedImage image = null;
            try {
                Path disk = diskFile(url);
                if (disk != null && Files.isRegularFile(disk)) {
                    try {
                        image = ImageIO.read(disk.toFile());
                    } catch (Exception ignored) {
                        Files.deleteIfExists(disk);
                    }
                }
                if (image == null) {
                    Net net = new Net(ProgressSink.NOOP, new CancellationToken());
                    try {
                        byte[] converted = net.getUnverifiedBytes(pngProxy(url), "项目图标");
                        image = ImageIO.read(new ByteArrayInputStream(converted));
                    } catch (Throwable ex) {
                        Log.warn("图标代理失败，回退原图: " + ex.getMessage());
                    }
                    if (image == null) {
                        byte[] bytes = net.getUnverifiedBytes(url, "项目图标");
                        image = ImageIO.read(new ByteArrayInputStream(bytes));
                        if (image == null) {
                            image = decodeWithSystemTool(bytes);
                        }
                    }
                    if (image != null) {
                        writeDisk(disk, image);
                    }
                }
                if (image != null) {
                    put(url, image);
                    SwingUtilities.invokeLater(onLoaded);
                }
            } catch (Throwable ex) {
                Log.warn("图标加载失败: " + ex);
            } finally {
                LOADING.remove(url);
                List<Runnable> waiters = WAITERS.remove(url);
                if (waiters != null && image != null) {
                    SwingUtilities.invokeLater(() -> waiters.forEach(Runnable::run));
                }
            }
        });
    }

    private static void put(String url, Image image) {
        CACHE.put(url, image);
        ORDER.addLast(url);
        while (CACHE.size() > MAX_ENTRIES) {
            String oldest = ORDER.pollFirst();
            if (oldest == null) {
                break;
            }
            CACHE.remove(oldest);
        }
    }

    private static String pngProxy(String url) {
        return "https://wsrv.nl/?url="
                + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8)
                + "&output=png";
    }

    private static Path diskFile(String url) {
        Path dir = cacheDir;
        if (dir == null) {
            return null;
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return dir.resolve(java.util.HexFormat.of().formatHex(hash) + ".png");
        } catch (Exception ex) {
            return null;
        }
    }

    private static void writeDisk(Path file, BufferedImage image) {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "icon.", ".tmp");
            if (ImageIO.write(image, "png", temp.toFile())) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(temp);
            }
        } catch (Exception ex) {
            Log.warn("图标缓存写入失败: " + ex.getMessage());
        }
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
