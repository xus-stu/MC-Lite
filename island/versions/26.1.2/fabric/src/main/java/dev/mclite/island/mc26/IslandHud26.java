package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 26.x 灵动岛：参考 iPhone 灵动岛 / MacBook 刘海的极简胶囊。
 *
 * <p>只有左侧三根跳动电平和一行文字（有歌词就显示歌词，没有就显示标题），
 * 不放大封面、不画进度条。宽度 132–300、高度 26，宽度量化到 8px 复用底图；
 * 底图按 GUI Scale 烘焙，圆角不会在 3x 下变成阶梯。</p>
 */
final class IslandHud26 {

    private static final String IDLE_TITLE = "MCLite Island";
    private static final String HINT = "M 打开播放器";

    private static final int MIN_WIDTH = 118;
    private static final int MAX_WIDTH = 262;
    private static final int HEIGHT = 22;
    private static final int TEXT_X = 24;
    private static final int PAD_RIGHT = 10;

    private static final int TEXT = 0xFFF3F5F8;
    private static final int TEXT_DIM = 0xFFAAB3BE;
    private static final int ACCENT = 0xFFF0A3C2;
    private static final int EQ_DIM = 0x66F0A3C2;

    private static final int EQ_BARS = 3;
    private static final int EQ_BAR_W = 2;
    private static final int EQ_GAP = 2;
    private static final int EQ_MAX_H = 9;

    private static final UISurface26.Painter PAINT =
            (g, w, h) -> HudPaint26.pill(g, w, h);

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
        Layout layout = ensureLayout(client.font, snapshot, config);
        int screenWidth = client.getWindow().getGuiScaledWidth();
        float scale = clampScale(config.islandScale);
        int visualWidth = Math.round(layout.width * scale);
        int x = Math.round((screenWidth - layout.width * scale) / 2f) + config.islandX;
        x = Math.max(2, Math.min(Math.max(2, screenWidth - visualWidth - 2), x));
        int y = 6 + Math.max(0, config.islandY);
        draw(extractor, client.font, layout, snapshot, x, y, scale);
    }

    static void renderPreview(GuiGraphicsExtractor extractor, IslandConfig config,
                              PlaybackService service, int centerX, int centerY) {
        if (config == null || service == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        IslandSnapshot snapshot = service.snapshot();
        Layout layout = ensureLayout(client.font, snapshot, config);
        draw(extractor, client.font, layout, snapshot,
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
        UISurface26.Surface backdrop = UISurface26.get("hud/" + h, w, h,
                PAINT, UiScale26.current());
        UISurface26.draw(extractor, backdrop, 0, 0);

        drawEqualizer(extractor, snapshot, h);
        int color = snapshot.hasTrack() && (snapshot.playing() || snapshot.paused())
                ? (snapshot.paused() ? TEXT_DIM : TEXT) : TEXT_DIM;
        int textY = (h - 8) / 2;
        extractor.text(font, layout.shown, TEXT_X, textY, color, true);
        pose.popMatrix();
    }

    private static void drawEqualizer(GuiGraphicsExtractor extractor, IslandSnapshot snapshot,
                                      int h) {
        int total = EQ_BARS * EQ_BAR_W + (EQ_BARS - 1) * EQ_GAP;
        int startX = (TEXT_X - total) / 2;
        int baseline = h / 2 + 4;
        boolean animate = snapshot.playing() && !snapshot.paused();
        float phase = animate ? (System.currentTimeMillis() % 1600L) / 1600f : 0f;
        int color = animate ? ACCENT : EQ_DIM;
        for (int i = 0; i < EQ_BARS; i++) {
            float wave = (float) Math.sin((phase + i * 0.27f) * Math.PI * 2.0);
            int barHeight = animate
                    ? 3 + Math.round((EQ_MAX_H - 3) * (0.5f + 0.5f * wave))
                    : 3 + i;
            int barX = startX + i * (EQ_BAR_W + EQ_GAP);
            extractor.fill(barX, baseline - barHeight, barX + EQ_BAR_W, baseline, color);
        }
    }

    private static Layout ensureLayout(Font font, IslandSnapshot snapshot, IslandConfig config) {
        Layout cached = LAYOUT;
        boolean hasTrack = snapshot.hasTrack();
        if (cached.valid
                && cached.sourceFont == font
                && cached.hasTrack == hasTrack
                && cached.srcTitle.equals(safe(snapshot.title()))
                && cached.srcLyric.equals(safe(snapshot.lyric()))
                && cached.srcContextTitle.equals(safe(snapshot.contextTitle()))
                && cached.showContext == config.showContext) {
            return cached;
        }
        cached.valid = true;
        cached.sourceFont = font;
        cached.hasTrack = hasTrack;
        cached.showContext = config.showContext;
        cached.srcTitle = safe(snapshot.title());
        cached.srcLyric = safe(snapshot.lyric());
        cached.srcContextTitle = safe(snapshot.contextTitle());

        String text;
        if (hasTrack) {
            text = cached.srcLyric.isBlank() ? cached.srcTitle : cached.srcLyric;
            if (text.isBlank()) {
                text = HINT;
            }
        } else if (config.showContext && !cached.srcContextTitle.isBlank()) {
            text = cached.srcContextTitle;
        } else {
            text = IDLE_TITLE;
        }

        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH,
                font.width(text) + TEXT_X + PAD_RIGHT));
        width = Math.min(MAX_WIDTH, ((width + 7) / 8) * 8);
        cached.width = width;
        cached.height = HEIGHT;
        cached.shown = UiText26.fit(font, text, width - TEXT_X - PAD_RIGHT);
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
        boolean showContext;
        String srcTitle = "";
        String srcLyric = "";
        String srcContextTitle = "";
        String shown = "";
        int width = MIN_WIDTH;
        int height = HEIGHT;
    }
}
