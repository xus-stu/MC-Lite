package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 26.x 灵动岛：顶部居中的迷你播放器胶囊。
 *
 * <p>有歌曲时是「封面块 + 标题 + 歌词 + 进度线」的横向小卡片，左边封面块里
 * 的音量条随播放跳动；没有歌曲时退化成居中的小胶囊。所有静态底图由
 * {@link HudPaint26} 用 Java2D 烘焙，每帧只做 1 次 blit + 少量文本/矩形，
 * 宽度会量化到 8px 一档以复用纹理。</p>
 */
final class IslandHud26 {

    private static final String HINT = "M 打开播放器";
    private static final String IDLE_TITLE = "MCLite Island";

    private static final int MIN_WIDTH = 200;
    private static final int MAX_WIDTH = 420;
    private static final int MIN_IDLE_WIDTH = 176;
    private static final int HEIGHT_IDLE = 36;
    private static final int HEIGHT_PLAYING = 42;
    private static final int HEIGHT_LYRIC = 48;

    private static final int TEXT = 0xFFF2F5F8;
    private static final int TEXT_PAUSED = 0xFFB0BCC8;
    private static final int LYRIC = 0xFF8FD3FF;
    private static final int DIM = 0xFF93A0AD;
    private static final int ACCENT = 0xFF7FC8FF;
    private static final int TRACK_BG = 0x30FFFFFF;
    private static final int EQ_DIM = 0x66CFE9FF;

    private static final UISurface26.Painter PAINT_PLAYER =
            (g, w, h) -> HudPaint26.pill(g, w, h, true);
    private static final UISurface26.Painter PAINT_IDLE =
            (g, w, h) -> HudPaint26.pill(g, w, h, false);

    private static final int EQ_BARS = 3;
    private static final int EQ_BAR_W = 2;
    private static final int EQ_GAP = 2;
    private static final int EQ_MAX_H = 13;

    private IslandHud26() {
    }

    static void render(GuiGraphicsExtractor extractor, IslandConfig config, PlaybackService service) {
        if (config == null || service == null || !config.showIsland) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return;
        }
        IslandSnapshot snapshot = service.snapshot();
        Font font = client.font;
        Layout layout = ensureLayout(font, snapshot, config);

        int screenWidth = client.getWindow().getGuiScaledWidth();
        float scale = clampScale(config.islandScale);
        int visualWidth = Math.round(layout.width * scale);
        int x = Math.round((screenWidth - layout.width * scale) / 2f) + config.islandX;
        x = Math.max(2, Math.min(Math.max(2, screenWidth - visualWidth - 2), x));
        int y = 6 + Math.max(0, config.islandY);
        draw(extractor, font, layout, snapshot, x, y, scale);
    }

    /** 设置页顶部的实时预览：不受 showIsland 影响，固定 1.0 缩放。 */
    static void renderPreview(GuiGraphicsExtractor extractor, IslandConfig config,
                              PlaybackService service, int centerX, int centerY) {
        if (config == null || service == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        IslandSnapshot snapshot = service.snapshot();
        Font font = client.font;
        Layout layout = ensureLayout(font, snapshot, config);
        draw(extractor, font, layout, snapshot,
                centerX - layout.width / 2, centerY - layout.height / 2, 1f);
    }

    private static void draw(GuiGraphicsExtractor extractor, Font font, Layout layout,
                             IslandSnapshot snapshot, int x, int y, float scale) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int w = layout.width;
        int h = layout.height;
        boolean art = layout.hasTrack;

        UISurface26.Surface backdrop = UISurface26.get(
                (art ? "hud/player/" : "hud/idle/") + h, w, h,
                art ? PAINT_PLAYER : PAINT_IDLE);
        UISurface26.draw(extractor, backdrop, 0, 0);

        if (!art) {
            int color1 = snapshot.paused() ? TEXT_PAUSED : TEXT;
            extractor.text(font, layout.shown1, (w - layout.shown1Width) / 2, 8, color1, false);
            extractor.text(font, layout.shown2, (w - layout.shown2Width) / 2, 20, DIM, false);
            pose.popMatrix();
            return;
        }

        int artY = (h - HudPaint26.ART_SIZE) / 2;
        drawEqualizer(extractor, snapshot, artY);

        int color1 = snapshot.paused() ? TEXT_PAUSED : TEXT;
        int color2 = layout.hasLyric ? LYRIC : DIM;
        extractor.text(font, layout.shown1, HudPaint26.TEXT_X, layout.line1Y, color1, false);
        extractor.text(font, layout.shown2, HudPaint26.TEXT_X, layout.line2Y, color2, false);

        long duration = snapshot.durationMs();
        if (duration > 0L) {
            drawProgress(extractor, snapshot, w, h, duration);
        }
        pose.popMatrix();
    }

    /** 封面块里 3 根跳动的电平条；暂停时保持低平的静态条。 */
    private static void drawEqualizer(GuiGraphicsExtractor extractor, IslandSnapshot snapshot,
                                      int artY) {
        int centerX = HudPaint26.ART_X + HudPaint26.ART_SIZE / 2;
        int total = EQ_BARS * EQ_BAR_W + (EQ_BARS - 1) * EQ_GAP;
        int startX = centerX - total / 2;
        int baseline = artY + HudPaint26.ART_SIZE - 8;
        boolean animate = snapshot.playing() && !snapshot.paused();
        float phase = animate ? (System.currentTimeMillis() % 1600L) / 1600f : 0f;
        int color = animate ? ACCENT : EQ_DIM;
        for (int i = 0; i < EQ_BARS; i++) {
            float wave = (float) Math.sin((phase + i * 0.27f) * Math.PI * 2.0);
            int barHeight = animate
                    ? 4 + Math.round((EQ_MAX_H - 4) * (0.5f + 0.5f * wave))
                    : 4 + i;
            int barX = startX + i * (EQ_BAR_W + EQ_GAP);
            extractor.fill(barX, baseline - barHeight, barX + EQ_BAR_W, baseline, color);
        }
    }

    private static void drawProgress(GuiGraphicsExtractor extractor, IslandSnapshot snapshot,
                                     int w, int h, long duration) {
        int left = HudPaint26.TEXT_X;
        int right = w - HudPaint26.PAD_RIGHT;
        int barY = h - 7;
        extractor.fill(left, barY, right, barY + 2, TRACK_BG);
        long position = Math.max(0L, snapshot.positionMs());
        int filled = left + (int) Math.min(right - left,
                (right - left) * (double) position / (double) duration);
        if (filled > left) {
            extractor.fill(left, barY, filled, barY + 2, ACCENT);
            int head = Math.min(right, filled + 1);
            extractor.fill(head - 1, barY - 1, head + 2, barY + 3, 0xFFE4F4FF);
        }
    }

    private static Layout ensureLayout(Font font, IslandSnapshot snapshot, IslandConfig config) {
        Layout cached = LAYOUT;
        boolean hasTrack = snapshot.hasTrack();
        boolean hasContext = snapshot.hasContext();
        boolean showContext = config.showContext;
        if (cached.valid
                && cached.sourceFont == font
                && cached.hasTrack == hasTrack
                && cached.hasContext == hasContext
                && cached.showContext == showContext
                && cached.srcTitle.equals(safe(snapshot.title()))
                && cached.srcArtist.equals(safe(snapshot.artist()))
                && cached.srcLyric.equals(safe(snapshot.lyric()))
                && cached.srcContextTitle.equals(safe(snapshot.contextTitle()))
                && cached.srcContextSubtitle.equals(safe(snapshot.contextSubtitle()))) {
            return cached;
        }

        cached.valid = true;
        cached.sourceFont = font;
        cached.hasTrack = hasTrack;
        cached.hasContext = hasContext;
        cached.showContext = showContext;
        cached.srcTitle = safe(snapshot.title());
        cached.srcArtist = safe(snapshot.artist());
        cached.srcLyric = safe(snapshot.lyric());
        cached.srcContextTitle = safe(snapshot.contextTitle());
        cached.srcContextSubtitle = safe(snapshot.contextSubtitle());

        String line1;
        String line2;
        int height;
        boolean lyricLine;
        if (hasTrack) {
            line1 = cached.srcTitle.isBlank() ? IDLE_TITLE : cached.srcTitle;
            if (!cached.srcLyric.isBlank()) {
                line2 = cached.srcLyric;
                lyricLine = true;
                height = HEIGHT_LYRIC;
            } else {
                line2 = cached.srcArtist.isBlank() ? HINT : cached.srcArtist;
                lyricLine = false;
                height = HEIGHT_PLAYING;
            }
        } else if (showContext && hasContext) {
            line1 = cached.srcContextTitle.isBlank() ? IDLE_TITLE : cached.srcContextTitle;
            line2 = cached.srcContextSubtitle.isBlank() ? HINT : cached.srcContextSubtitle;
            lyricLine = false;
            height = HEIGHT_IDLE;
        } else {
            line1 = IDLE_TITLE;
            line2 = HINT;
            lyricLine = false;
            height = HEIGHT_IDLE;
        }

        int width;
        if (hasTrack) {
            int textWidth = Math.max(font.width(line1), font.width(line2));
            width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH,
                    textWidth + HudPaint26.TEXT_X + HudPaint26.PAD_RIGHT));
        } else {
            int textWidth = Math.max(font.width(line1), font.width(line2));
            width = Math.max(MIN_IDLE_WIDTH, Math.min(320, textWidth + 28));
        }
        // 量化到 8px：歌词逐字变化时不会每次都重新烘焙一张纹理
        width = Math.min(MAX_WIDTH, ((width + 7) / 8) * 8);
        int available = width - HudPaint26.TEXT_X - HudPaint26.PAD_RIGHT;

        cached.line1 = line1;
        cached.line2 = line2;
        cached.shown1 = UiText26.fit(font, line1, available);
        cached.shown2 = UiText26.fit(font, line2, available);
        cached.shown1Width = font.width(cached.shown1);
        cached.shown2Width = font.width(cached.shown2);
        cached.width = width;
        cached.height = height;
        cached.hasLyric = lyricLine;
        cached.line1Y = hasTrack ? 9 : 8;
        cached.line2Y = hasTrack ? (lyricLine ? 24 : 22) : 20;
        return cached;
    }

    private static float clampScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            return 1f;
        }
        return Math.max(0.5f, Math.min(3f, scale));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final Layout LAYOUT = new Layout();

    private static final class Layout {
        boolean valid;
        Font sourceFont;
        boolean hasTrack;
        boolean hasContext;
        boolean showContext;
        boolean hasLyric;
        String srcTitle = "";
        String srcArtist = "";
        String srcLyric = "";
        String srcContextTitle = "";
        String srcContextSubtitle = "";
        String line1 = "";
        String line2 = "";
        String shown1 = "";
        String shown2 = "";
        int shown1Width;
        int shown2Width;
        int width = MIN_IDLE_WIDTH;
        int height = HEIGHT_IDLE;
        int line1Y = 8;
        int line2Y = 20;
    }
}
