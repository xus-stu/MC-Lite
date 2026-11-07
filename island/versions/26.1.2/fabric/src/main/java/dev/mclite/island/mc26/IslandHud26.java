package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 26.x 灵动岛：屏幕顶部居中的小号深色半透明胶囊。
 *
 * <p>宽度按两行文本自适应，限制在 160..360；有歌词时 34 高（两行），
 * 没有歌词时 26 高（仍是两行：标题 + 歌手/提示）。文本、宽度、截断结果
 * 全部缓存，渲染帧内不做字符串拼接。</p>
 */
final class IslandHud26 {

    private static final String HINT = "M 打开播放器";
    private static final String IDLE_TITLE = "MCLite Island";
    private static final int MIN_WIDTH = 160;
    private static final int MAX_WIDTH = 360;
    private static final int PAD_X = 12;
    private static final int HEIGHT_ONE = 26;
    private static final int HEIGHT_LYRIC = 34;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int TEXT_PAUSED = 0xFFB4C0CB;
    private static final int LYRIC = 0xFF8FD3FF;
    private static final int DIM = 0xFF93A0AD;
    private static final int TRACK_BG = 0x28FFFFFF;
    private static final int TRACK_FG = 0xB06CC6FF;

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
        int x = centerX - layout.width / 2;
        int y = centerY - layout.height / 2;
        draw(extractor, font, layout, snapshot, x, y, 1f);
    }

    private static void paintPill(java.awt.Graphics2D g, int w, int h) {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int radius = h;
        g.setColor(new java.awt.Color(0, 0, 0, 70));
        g.fillRoundRect(0, 2, w, h, radius, radius);
        java.awt.GradientPaint gradient = new java.awt.GradientPaint(
                0, 0, new java.awt.Color(22, 28, 36, 232),
                0, h, new java.awt.Color(12, 16, 21, 238));
        g.setPaint(gradient);
        g.fillRoundRect(0, 0, w, h, radius, radius);
        g.setColor(new java.awt.Color(255, 255, 255, 34));
        g.setStroke(new java.awt.BasicStroke(1f));
        g.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
    }

    private static void draw(GuiGraphicsExtractor extractor, Font font, Layout layout,
                             IslandSnapshot snapshot, int x, int y, float scale) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int w = layout.width;
        int h = layout.height;

        // Java2D 烘焙的胶囊底图：真正的圆角(radius = h/2) + 渐变 + 细描边 + 阴影
        UISurface26.Surface backdrop = UISurface26.get("hud", w, h, IslandHud26::paintPill);
        UISurface26.draw(extractor, backdrop, 0, 0);

        int y1 = h == HEIGHT_LYRIC ? 6 : 3;
        int y2 = h == HEIGHT_LYRIC ? 18 : 13;
        int color1 = snapshot.paused() ? TEXT_PAUSED : TEXT;
        int color2 = layout.hasLyric ? LYRIC : DIM;
        extractor.text(font, layout.shown1, (w - layout.shown1Width) / 2, y1, color1, false);
        extractor.text(font, layout.shown2, (w - layout.shown2Width) / 2, y2, color2, false);

        long duration = snapshot.durationMs();
        if (snapshot.hasTrack() && duration > 0L) {
            int barY = h - 4;
            int left = 14;
            int right = w - 14;
            extractor.fill(left, barY, right, barY + 2, TRACK_BG);
            long position = Math.max(0L, snapshot.positionMs());
            int filled = left + (int) Math.min(right - left,
                    (right - left) * (double) position / (double) duration);
            if (filled > left) {
                extractor.fill(left, barY, filled, barY + 2, TRACK_FG);
            }
        }
        pose.popMatrix();
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
                height = HEIGHT_ONE;
            }
        } else if (showContext && hasContext) {
            line1 = cached.srcContextTitle.isBlank() ? IDLE_TITLE : cached.srcContextTitle;
            line2 = cached.srcContextSubtitle.isBlank() ? HINT : cached.srcContextSubtitle;
            lyricLine = false;
            height = HEIGHT_ONE;
        } else {
            line1 = IDLE_TITLE;
            line2 = HINT;
            lyricLine = false;
            height = HEIGHT_ONE;
        }

        int full1 = font.width(line1);
        int full2 = font.width(line2);
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.max(full1, full2) + PAD_X * 2));
        int available = width - PAD_X * 2;
        cached.line1 = line1;
        cached.line2 = line2;
        cached.shown1 = UiText26.fit(font, line1, available);
        cached.shown2 = UiText26.fit(font, line2, available);
        cached.shown1Width = font.width(cached.shown1);
        cached.shown2Width = font.width(cached.shown2);
        cached.width = width;
        cached.height = height;
        cached.hasLyric = lyricLine;
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
        int width = MIN_WIDTH;
        int height = HEIGHT_ONE;
    }
}
