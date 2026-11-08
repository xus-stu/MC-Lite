package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏 3D 歌词：参考 MuoniumPlayer 的 Tilt 舞台——把一行歌词拆成 1–3 段，
 * 交替斜切、错位、发光，并在下方投一层倒影，做出文字 PV 的纵深。
 */
final class LyricsOverlay26 {

    private static String lastText = "";
    private static long lastChangeNanos;

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
        if (!text.equals(lastText)) {
            lastText = text;
            lastChangeNanos = System.nanoTime();
        }
        float enter = Math.min(1f, (System.nanoTime() - lastChangeNanos) / 380_000_000f);
        enter = enter * enter * (3f - 2f * enter);

        Font font = client.font;
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        List<String> segments = split(text);
        float base = Math.max(1.25f, Math.min(3.4f, height / 250f * config.lyricsScale));
        float widest = 1f;
        for (String segment : segments) {
            widest = Math.max(widest, font.width(segment));
        }
        float maxWidth = width * 0.86f;
        if (widest * base > maxWidth) {
            base = Math.max(0.9f, maxWidth / widest);
        }

        int count = segments.size();
        float lineGap = base * 13.5f;
        float blockHeight = (count - 1) * lineGap;
        float centerX = width / 2f;
        float centerY = height * 0.46f + (1f - enter) * 22f;
        int accent = config.lyricsColor;

        for (int i = 0; i < count; i++) {
            String segment = segments.get(i);
            float scale = base * (i == count / 2 ? 1.06f : 0.94f);
            float x = centerX - font.width(segment) * scale / 2f;
            float y = centerY - blockHeight / 2f + i * lineGap;
            float shear = (i % 2 == 0) ? 0.16f : -0.13f;
            drawText(extractor, font, segment, x, y, scale, shear,
                    withAlpha(accent, 0x38), 4.0f * scale, 3.0f * scale);
            drawText(extractor, font, segment, x, y, scale, shear,
                    withAlpha(accent, 0x66), 0f, 1.5f * scale);
            drawText(extractor, font, segment, x, y, scale, shear,
                    config.lyricsColor, 0f, 0f);
            // 倒影
            drawReflection(extractor, font, segment, x, y + font.lineHeight * scale * 0.9f,
                    scale, shear, withAlpha(accent, 0x22));
        }

        String next = snapshot.nextLyric();
        if (config.lyricsMaxLines > 1 && next != null && !next.isBlank()) {
            float nextScale = Math.max(0.75f, base * 0.45f);
            float nextWidth = font.width(next) * nextScale;
            float nextY = centerY + blockHeight / 2f + font.lineHeight * base * 1.4f;
            var pose = extractor.pose();
            pose.pushMatrix();
            pose.translate(centerX - nextWidth / 2f, nextY);
            pose.scale(nextScale, nextScale);
            extractor.text(font, next, 0, 0, withAlpha(config.lyricsColor, 0x99), true);
            pose.popMatrix();
        }
    }

    private static void drawText(GuiGraphicsExtractor extractor, Font font, String text,
                                 float x, float y, float scale, float shear, int color,
                                 float offX, float offY) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x + offX, y + offY);
        pose.rotate(shear * 0.55f);
        pose.scale(scale, scale);
        extractor.text(font, text, 0, 0, color, true);
        pose.popMatrix();
    }

    private static void drawReflection(GuiGraphicsExtractor extractor, Font font, String text,
                                       float x, float y, float scale, float shear, int color) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate(-shear * 0.55f);
        pose.scale(scale, -scale * 0.62f);
        extractor.text(font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    private static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return result;
        }
        int target = Math.max(1, Math.min(3, Math.round(trimmed.length() / 14f)));
        if (target <= 1 || trimmed.length() <= 10) {
            result.add(trimmed);
            return result;
        }
        if (trimmed.contains(" ")) {
            String[] words = trimmed.split("\\s+");
            int per = Math.max(1, (int) Math.ceil(words.length / (double) target));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(words[i]);
                if ((i + 1) % per == 0 && result.size() < target - 1) {
                    result.add(builder.toString());
                    builder.setLength(0);
                }
            }
            if (builder.length() > 0) {
                result.add(builder.toString());
            }
        } else {
            int per = (int) Math.ceil(trimmed.length() / (double) target);
            for (int i = 0; i < target; i++) {
                int from = i * per;
                int to = Math.min(trimmed.length(), from + per);
                if (from >= to) {
                    break;
                }
                result.add(trimmed.substring(from, to));
            }
        }
        return result;
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
