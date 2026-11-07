package dev.mclite.island.mc26;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.mclite.island.bili.BiliImageRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * B 站头像 / 视频封面的异步图片缓存。
 *
 * <p>下载和 ImageIO 解码都在 2 个后台线程完成；渲染线程只负责把已解码的
 * BufferedImage 转成 DynamicTexture 并缓存。纹理按 URL+尺寸 LRU，最多
 * {@value #MAX_ENTRIES} 张，屏幕关闭也不回收，翻收藏夹时不会重复下载。
 * 每帧每张图仍然只有一次 blit，不做任何逐帧缩放或离屏画布。</p>
 */
final class BiliImage26 {

    private static final int MAX_ENTRIES = 120;
    private static final int TEXTURE_SCALE = 2;
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String REFERER = "https://www.bilibili.com/";

    private static final Map<String, Entry> CACHE =
            new LinkedHashMap<>(32, 0.75f, true);
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "mclite-island-bili-image");
        thread.setDaemon(true);
        return thread;
    });

    private BiliImage26() {
    }

    static final class Entry {
        final Identifier id;
        final DynamicTexture texture;
        final int width;
        final int height;

        private Entry(Identifier id, DynamicTexture texture, int width, int height) {
            this.id = id;
            this.texture = texture;
            this.width = width;
            this.height = height;
        }

        GpuTextureView view() {
            return texture.getTextureView();
        }

        GpuSampler sampler() {
            return texture.getSampler();
        }
    }

    /**
     * 取一张已经下载好的图片；没准备好时返回 null，同时把 URL 扔进后台队列。
     * 必须在渲染线程调用。
     */
    static Entry get(String url, int width, int height, boolean circle) {
        if (url == null || url.isBlank() || width <= 0 || height <= 0) {
            return null;
        }
        String key = key(url, width, height, circle);
        Entry existing = CACHE.get(key);
        if (existing != null && !existing.view().isClosed()) {
            return existing;
        }
        if (!PENDING.add(key)) {
            return null;
        }
        String source = BiliImageRef.sized(url, width * TEXTURE_SCALE,
                height * TEXTURE_SCALE, true);
        POOL.execute(() -> {
            try {
                BufferedImage image = download(source, width * TEXTURE_SCALE,
                        height * TEXTURE_SCALE, circle, width);
                Minecraft.getInstance().execute(() -> register(key, image, width, height));
            } catch (Throwable failure) {
                PENDING.remove(key);
                if (FAILED.add(key)) {
                    System.err.println("[mclite-island] B 站图片加载失败 " + source
                            + ": " + failure);
                }
            }
        });
        return null;
    }

    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private static void register(String key, BufferedImage image, int width, int height) {
        PENDING.remove(key);
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    nativeImage.setPixel(x, y, pixels[row + x]);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("mclite-island",
                    "bili/image/" + SERIAL.incrementAndGet());
            DynamicTexture texture = new DynamicTexture(
                    () -> "mclite-island bili image " + key, nativeImage);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            CACHE.put(key, new Entry(id, texture, width, height));
            trim();
        } catch (Throwable failure) {
            FAILED.add(key);
            System.err.println("[mclite-island] B 站图片注册失败: " + failure);
        }
    }

    private static BufferedImage download(String url, int textureW, int textureH,
                                          boolean circle, int logicalW) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL()
                .openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(10_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Referer", REFERER);
        connection.setRequestProperty("Accept", "image/jpeg,image/png,image/*;q=0.8");
        try (InputStream input = connection.getInputStream()) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) {
                throw new IOException("不是可解码的图片");
            }
            BufferedImage target = new BufferedImage(textureW, textureH,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                Shape clip = circle
                        ? new Ellipse2D.Float(0, 0, textureW, textureH)
                        : new RoundRectangle2D.Float(0, 0, textureW, textureH,
                                Math.max(3, logicalW * 0.2f), Math.max(3, logicalW * 0.2f));
                g.setClip(clip);
                g.drawImage(source, 0, 0, textureW, textureH, null);
            } finally {
                g.dispose();
            }
            return target;
        } finally {
            connection.disconnect();
        }
    }

    private static void trim() {
        while (CACHE.size() > MAX_ENTRIES) {
            var iterator = CACHE.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            Entry oldest = iterator.next().getValue();
            iterator.remove();
            try {
                Minecraft.getInstance().getTextureManager().release(oldest.id);
            } catch (Throwable ignored) {
                // 释放失败不影响新纹理
            }
        }
    }

    static void draw(GuiGraphicsExtractor extractor, Entry entry, int x, int y,
                     int width, int height) {
        if (entry == null || entry.view().isClosed()) {
            return;
        }
        extractor.blit(entry.view(), entry.sampler(), x, y, x + width, y + height,
                0f, 1f, 0f, 1f);
    }

    private static String key(String url, int width, int height, boolean circle) {
        return url + "|" + width + "x" + height + (circle ? "|c" : "|r");
    }
}
