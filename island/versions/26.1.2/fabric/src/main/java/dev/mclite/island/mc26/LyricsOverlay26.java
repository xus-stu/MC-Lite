package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 全屏 3D 歌词：参考 MuoniumPlayer 的「文字 PV」思路，在 HUD 层用大字 + 多层
 * 偏移发光做出纵深，而不是继续往世界渲染事件里塞（26.x 的 submit 阶段不适合）。
 *
 * <p>当前行居中偏下，下一行缩小；颜色/缩放/行数沿用 3D 歌词配置。</p>
 */
final class LyricsOverlay26 {

    private LyricsOverlay26() {
    }

    static void render(GuiGraphicsExtractor extractor, IslandConfig config, PlaybackService service) {
        if (config == null || service == null || !config.threeDLyrics) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return;
        }
        IslandSnapshot snapshot = service.snapshot();
        if (!snapshot.hasTrack()) {
            return;
        }
        String text = snapshot.lyric();
        if (text == null || text.isBlank()) {
            return;
        }
        Font font = client.font;
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        float scale = Math.max(1.2f, Math.min(3.6f, 2.0f * config.lyricsScale));
        int textWidth = font.width(text);
        float maxWidth = width * 0.86f;
        if (textWidth * scale > maxWidth) {
            scale = Math.max(0.9f, maxWidth / Math.max(1, textWidth));
        }
        float x = width / 2f - textWidth * scale / 2f;
        float y = height * 0.58f + config.lyricsVerticalOffset * 18f;

        var pose = extractor.pose();
        // 多层偏移的暗色/主题色副本，做出“厚度”
        drawLayer(extractor, font, text, x, y + 3f * scale, scale, 0x66000000);
        drawLayer(extractor, font, text, x - 1.5f * scale, y - 1.5f * scale, scale,
                withAlpha(config.lyricsColor, 0x55));
        drawLayer(extractor, font, text, x, y, scale, config.lyricsColor);

        String next = snapshot.nextLyric();
        if (config.lyricsMaxLines > 1 && next != null && !next.isBlank()) {
            float nextScale = Math.max(0.8f, scale * 0.48f);
            int nextWidth = font.width(next);
            pose.pushMatrix();
            pose.translate(width / 2f - nextWidth * nextScale / 2f,
                    y + font.lineHeight * scale * 1.7f);
            pose.scale(nextScale, nextScale);
            extractor.text(font, next, 0, 0, withAlpha(config.lyricsColor, 0xAA), true);
            pose.popMatrix();
        }
    }

    private static void drawLayer(GuiGraphicsExtractor extractor, Font font, String text,
                                  float x, float y, float scale, int color) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        extractor.text(font, text, 0, 0, color, true);
        pose.popMatrix();
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
