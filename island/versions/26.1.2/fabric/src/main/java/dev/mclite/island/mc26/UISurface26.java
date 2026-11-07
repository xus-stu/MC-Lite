package dev.mclite.island.mc26;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 把 {@link UiPaint26} 画好的静态界面烘焙成一张 DynamicTexture。
 *
 * <p>界面每帧只做一次 {@code blit}，不再逐控件画圆角、描边和阴影。
 * 只有窗口尺寸变化（< 1 次/会话的常规情况）才会重新烘焙；纹理最多缓存
 * {@link #MAX_SURFACES} 张，旧的按 LRU 释放。</p>
 *
 * <p>Java2D 绘制失败时返回 {@code null}，调用方有一个 sprite 后备，
 * 不会因为 AWT 不可用把界面搞崩。</p>
 */
final class UISurface26 {

    /** 底图四周留给柔和投影的透明边。 */
    static final int PAD = 10;

    /** 缓存上限：5 个常规页面 + 少量窗口尺寸变体。 */
    private static final int MAX_SURFACES = 24;

    interface Painter {
        void paint(Graphics2D g, int width, int height);
    }

    static final class Surface {
        final int width;
        final int height;
        final int pad;
        final Identifier id;
        final DynamicTexture texture;

        private Surface(int width, int height, int pad, Identifier id, DynamicTexture texture) {
            this.width = width;
            this.height = height;
            this.pad = pad;
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

    private static final Map<String, Surface> CACHE =
            new LinkedHashMap<>(16, 0.75f, true);
    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static boolean warned;

    private UISurface26() {
    }

    /**
     * 取得（必要时烘焙）一张 pageW x pageH 的底图。返回的 Surface 在绘制时
     * 会自动向左上偏移 {@link #PAD}，以便把柔和投影一起画出来。
     */
    static Surface get(String key, int pageW, int pageH, Painter painter) {
        return get(key, pageW, pageH, painter, 1);
    }

    /**
     * @param renderScale 底图按几倍分辨率烘焙。小控件（胶囊/圆形按钮）传 GUI Scale，
     *                    让圆角在 3x GUI 下也是 1:1 像素；大页面仍用 1 省显存。
     */
    static Surface get(String key, int pageW, int pageH, Painter painter, int renderScale) {
        if (pageW <= 0 || pageH <= 0) {
            return null;
        }
        int scale = Math.max(1, Math.min(3, renderScale));
        String cacheKey = key + "@" + pageW + "x" + pageH + "s" + scale;
        Surface existing = CACHE.get(cacheKey);
        if (existing != null) {
            if (!existing.view().isClosed()) {
                return existing;
            }
            CACHE.remove(cacheKey);
        }
        Surface baked = bake(cacheKey, pageW, pageH, painter, scale);
        if (baked != null) {
            CACHE.put(cacheKey, baked);
            trim();
        }
        return baked;
    }

    static void draw(GuiGraphicsExtractor extractor, Surface surface, int pageX, int pageY) {
        if (surface == null || surface.view().isClosed()) {
            return;
        }
        int x0 = pageX - surface.pad;
        int y0 = pageY - surface.pad;
        // 26.1.2 的 blit(view, sampler, x0, y0, x1, y1, u0, u1, v0, v1)
        // 是“角点”而不是 x,y,w,h（已按反编译源码核对）。
        extractor.blit(surface.view(), surface.sampler(),
                x0, y0, x0 + surface.width, y0 + surface.height,
                0f, 1f, 0f, 1f);
    }

    private static Surface bake(String key, int pageW, int pageH, Painter painter,
                                int renderScale) {
        try {
            int surfaceW = (pageW + PAD * 2) * renderScale;
            int surfaceH = (pageH + PAD * 2) * renderScale;
            BufferedImage image = new BufferedImage(surfaceW, surfaceH,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.scale(renderScale, renderScale);
                g.translate(PAD, PAD);
                painter.paint(g, pageW, pageH);
            } finally {
                g.dispose();
            }

            int[] pixels = image.getRGB(0, 0, surfaceW, surfaceH, null, 0, surfaceW);
            NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA,
                    surfaceW, surfaceH, false);
            for (int y = 0; y < surfaceH; y++) {
                int row = y * surfaceW;
                for (int x = 0; x < surfaceW; x++) {
                    // setPixel 内部做 ARGB -> ABGR 转换，和 Java2D 的 getRGB 正好对接
                    nativeImage.setPixel(x, y, pixels[row + x]);
                }
            }

            Identifier id = Identifier.fromNamespaceAndPath("mclite-island",
                    "ui/bake/" + SERIAL.incrementAndGet());
            DynamicTexture texture = new DynamicTexture(
                    () -> "mclite-island ui " + key, nativeImage);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return new Surface(pageW + PAD * 2, pageH + PAD * 2, PAD, id, texture);
        } catch (Throwable failure) {
            if (!warned) {
                warned = true;
                System.err.println("[mclite-island] Java2D UI bake unavailable: " + failure);
            }
            return null;
        }
    }

    private static void trim() {
        while (CACHE.size() > MAX_SURFACES) {
            var iterator = CACHE.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            Surface oldest = iterator.next().getValue();
            iterator.remove();
            try {
                Minecraft.getInstance().getTextureManager().release(oldest.id);
            } catch (Throwable ignored) {
                // 释放失败不影响新纹理
            }
        }
    }
}
