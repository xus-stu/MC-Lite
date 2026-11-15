package mclite.music;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 封面缩略图：异步下载、磁盘缓存、圆角绘制。 */
final class Art {

    private static final Map<String, Image> MEMORY = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > 128;
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "mclite-art");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Art() {
    }

    static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String value = url.trim();
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        return value;
    }

    static JComponent thumb(Path cacheDir, String url, int size) {
        return new Thumb(cacheDir, normalize(url), size);
    }

    private static Image load(Path cacheDir, String url, int size) {
        String key = url + "@" + size;
        synchronized (MEMORY) {
            Image cached = MEMORY.get(key);
            if (cached != null) {
                return cached;
            }
        }
        try {
            byte[] bytes = null;
            Path file = cacheDir.resolve("art").resolve(hash(url) + ".img");
            if (Files.isRegularFile(file)) {
                bytes = Files.readAllBytes(file);
            } else {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://www.bilibili.com/")
                        .GET().build();
                HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    bytes = response.body();
                    Files.createDirectories(file.getParent());
                    Files.write(file, bytes);
                }
            }
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            BufferedImage source;
            try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
                source = ImageIO.read(in);
            }
            if (source == null) {
                return null;
            }
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.max((double) size / source.getWidth(), (double) size / source.getHeight());
            int w = (int) Math.round(source.getWidth() * scale);
            int h = (int) Math.round(source.getHeight() * scale);
            g.drawImage(source, (size - w) / 2, (size - h) / 2, w, h, null);
            g.dispose();
            synchronized (MEMORY) {
                MEMORY.put(key, scaled);
            }
            return scaled;
        } catch (Exception ignored) {
            return null;
        } finally {
            // 忽略失败：占位图继续显示。
        }
    }

    private static String hash(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static final class Thumb extends JComponent {
        private final int size;
        private Image image;

        Thumb(Path cacheDir, String url, int size) {
            this.size = size;
            java.awt.Dimension box = new java.awt.Dimension(size, size);
            setPreferredSize(box);
            setMinimumSize(box);
            setMaximumSize(box);
            if (url.isBlank()) {
                return;
            }
            POOL.execute(() -> {
                Image loaded = load(cacheDir, url, size);
                if (loaded != null) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        image = loaded;
                        repaint();
                    });
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            if (image != null) {
                g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            } else {
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            g2.dispose();
        }
    }
}
