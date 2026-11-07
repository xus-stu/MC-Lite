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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
 * 播放器的整张「封面模糊背景 + 半透明卡片」底图。
 *
 * <p>每首歌只烘焙一次：后台线程下载封面、缩成 48 像素的小图再放大得到模糊背景，
 * 交给 {@link UiPaint26#playerBackdrop} 画完卡片后，渲染线程只做一次
 * NativeImage 转换和一次 blit。切歌不会每帧重画，播放过程中也没有额外开销。</p>
 */
final class PlayerBackdrop26 {

    static final int WIDTH = UiPaint26.PLAYER_W;
    static final int HEIGHT = UiPaint26.PLAYER_H;
    static final int PAD = 14;
    static final int ACCENT = 0xFFFF7FB8;

    private static final int MAX_ENTRIES = 3;
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(8, 0.75f, true);
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mclite-island-player-backdrop");
        thread.setDaemon(true);
        return thread;
    });

    private PlayerBackdrop26() {
    }

    static final class Entry {
        final Identifier id;
        final DynamicTexture texture;
        volatile long lastAccessMs = System.currentTimeMillis();

        private Entry(Identifier id, DynamicTexture texture) {
            this.id = id;
            this.texture = texture;
        }

        GpuTextureView view() {
            return texture.getTextureView();
        }

        GpuSampler sampler() {
            return texture.getSampler();
        }
    }

    /** 拿不到就先用默认底图，不要阻塞渲染线程。 */
    static Entry get(String coverUrl) {
        if (coverUrl == null || coverUrl.isBlank()) {
            return getDefault();
        }
        String key = "cover|" + coverUrl;
        Entry existing = CACHE.get(key);
        if (existing != null && !existing.view().isClosed()) {
            existing.lastAccessMs = System.currentTimeMillis();
            return existing;
        }
        if (PENDING.add(key)) {
            int scale = UiScale26.current();
            POOL.execute(() -> {
                try {
                    String source = BiliImageRef.sized(coverUrl, 400, 400, true);
                    BufferedImage cover = BiliImage26.load(source, 400, 400, false, 400);
                    submit(key, cover, scale);
                } catch (Throwable failure) {
                    PENDING.remove(key);
                    System.err.println("[mclite-island] 播放器背景下载失败: " + failure);
                }
            });
        }
        return null;
    }

    private static Entry getDefault() {
        String key = "default";
        Entry existing = CACHE.get(key);
        if (existing != null && !existing.view().isClosed()) {
            existing.lastAccessMs = System.currentTimeMillis();
            return existing;
        }
        if (PENDING.add(key)) {
            int scale = UiScale26.current();
            POOL.execute(() -> submit(key, null, scale));
        }
        return null;
    }

    private static void submit(String key, BufferedImage cover, int scale) {
        try {
            BufferedImage baked = bake(cover, scale);
            ByteArrayOutputStream png = new ByteArrayOutputStream(1 << 16);
            ImageIO.write(baked, "png", png);
            // stb 解码在后台线程完成；渲染线程只做 DynamicTexture 注册
            NativeImage image = NativeImage.read(png.toByteArray());
            Minecraft.getInstance().execute(() -> registerNative(key, image));
        } catch (Throwable failure) {
            PENDING.remove(key);
            System.err.println("[mclite-island] 播放器背景生成失败: " + failure);
        }
    }

    private static BufferedImage bake(BufferedImage cover, int scale) {
        int surfaceW = (WIDTH + PAD * 2) * scale;
        int surfaceH = (HEIGHT + PAD * 2) * scale;
        BufferedImage image = new BufferedImage(surfaceW, surfaceH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(scale, scale);
            g.translate(PAD, PAD);
            UiPaint26.playerBackdrop(g, WIDTH, HEIGHT, cover, ACCENT);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void registerNative(String key, NativeImage image) {
        PENDING.remove(key);
        try {
            Identifier id = Identifier.fromNamespaceAndPath("mclite-island",
                    "player/backdrop/" + SERIAL.incrementAndGet());
            DynamicTexture texture = new DynamicTexture(
                    () -> "mclite-island player backdrop " + key, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            CACHE.put(key, new Entry(id, texture));
            trim();
        } catch (Throwable failure) {
            try {
                image.close();
            } catch (Throwable ignored) {
                // 关闭失败就算了
            }
            System.err.println("[mclite-island] 播放器背景注册失败: " + failure);
        }
    }

    private static void trim() {
        long now = System.currentTimeMillis();
        while (CACHE.size() > MAX_ENTRIES) {
            var iterator = CACHE.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            Map.Entry<String, Entry> oldest = iterator.next();
            if ("default".equals(oldest.getKey())
                    || now - oldest.getValue().lastAccessMs < 5_000L) {
                return;
            }
            iterator.remove();
            try {
                Minecraft.getInstance().getTextureManager().release(oldest.getValue().id);
            } catch (Throwable ignored) {
                // 释放失败不影响新纹理
            }
        }
    }

    static void draw(GuiGraphicsExtractor extractor, Entry entry, int x, int y) {
        if (entry == null || entry.view().isClosed()) {
            return;
        }
        int x0 = x - PAD;
        int y0 = y - PAD;
        extractor.blit(entry.view(), entry.sampler(), x0, y0,
                x0 + WIDTH + PAD * 2, y0 + HEIGHT + PAD * 2, 0f, 1f, 0f, 1f);
    }
}
