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
 * 全屏 3D 歌词舞台：参考 MuoniumPlayer 的多种文字 PV 风格。
 *
 * <p>0 倾斜 Tilt / 1 苹果 Apple / 2 立体 Diorama / 3 华彩 Cadenza /
 * 4 光晕 Luminous / 5 烟雾 Fume / 6 钟摆 Pendolo / 7 合唱 Cappella。</p>
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
        float base = Math.max(1.15f, Math.min(3.3f, height / 255f * config.lyricsScale));
        float maxWidth = width * 0.86f;
        int widest = Math.max(1, font.width(text));
        if (widest * base > maxWidth) {
            base = Math.max(0.85f, maxWidth / widest);
        }
        float cx = width / 2f;
        float cy = height * 0.46f + (1f - enter) * 20f;
        int color = config.lyricsColor;
        long now = System.nanoTime();

        switch (Math.floorMod(config.lyrics3dStyle, 8)) {
            case 0 -> tilt(extractor, font, text, cx, cy, base, enter, color);
            case 1 -> apple(extractor, font, text, cx, cy, base, enter, color);
            case 2 -> diorama(extractor, font, text, cx, cy, base, enter, color);
            case 3 -> cadenza(extractor, font, text, cx, cy, base, enter, color);
            case 4 -> luminous(extractor, font, text, cx, cy, base, enter, color, now);
            case 5 -> fume(extractor, font, text, cx, cy, base, enter, color, now);
            case 6 -> pendolo(extractor, font, text, cx, cy, base, enter, color, now);
            default -> cappella(extractor, font, text, cx, cy, base, enter, color);
        }

        String next = snapshot.nextLyric();
        if (config.lyricsMaxLines > 1 && next != null && !next.isBlank()) {
            float nextScale = Math.max(0.7f, base * 0.44f);
            float nextWidth = font.width(next) * nextScale;
            float nextY = cy + font.lineHeight * base * 2.2f;
            var pose = extractor.pose();
            pose.pushMatrix();
            pose.translate(cx - nextWidth / 2f, nextY);
            pose.scale(nextScale, nextScale);
            extractor.text(font, next, 0, 0, withAlpha(color, 0x99), true);
            pose.popMatrix();
        }
    }

    // 0 倾斜：拆段 + 交替旋转
    private static void tilt(GuiGraphicsExtractor extractor, Font font, String text, float cx,
                             float cy, float base, float enter, int color) {
        List<String> segments = split(text, 3);
        int count = segments.size();
        float gap = base * 13.5f;
        float top = cy - (count - 1) * gap / 2f;
        for (int i = 0; i < count; i++) {
            String segment = segments.get(i);
            float scale = base * (i == count / 2 ? 1.06f : 0.94f);
            float x = cx - font.width(segment) * scale / 2f;
            float y = top + i * gap;
            float angle = (i % 2 == 0 ? 0.16f : -0.13f) * 0.55f;
            layer(extractor, font, segment, x, y, scale, angle, withAlpha(color, 0x55),
                    0f, 2f * scale);
            layer(extractor, font, segment, x, y, scale, angle, color, 0f, 0f);
            reflection(extractor, font, segment, x, y + font.lineHeight * scale, scale,
                    angle, withAlpha(color, 0x22));
        }
    }

    // 1 苹果：大号居中，柔光
    private static void apple(GuiGraphicsExtractor extractor, Font font, String text, float cx,
                              float cy, float base, float enter, int color) {
        float scale = base * 1.18f * (0.92f + 0.08f * enter);
        float x = cx - font.width(text) * scale / 2f;
        for (int i = 3; i >= 1; i--) {
            layer(extractor, font, text, x, cy, scale, 0f, withAlpha(color, 0x18),
                    0f, i * 1.6f * scale);
        }
        layer(extractor, font, text, x, cy, scale, 0f, color, 0f, 0f);
    }

    // 2 立体：三层纵深
    private static void diorama(GuiGraphicsExtractor extractor, Font font, String text, float cx,
                                float cy, float base, float enter, int color) {
        float scale = base * 1.05f;
        float x = cx - font.width(text) * scale / 2f;
        layer(extractor, font, text, x, cy + 2f * scale, scale * 1.01f, 0f,
                withAlpha(color, 0x66), 0f, 0f);
        layer(extractor, font, text, x, cy + scale, scale, 0f,
                withAlpha(color, 0xAA), 0f, 0f);
        layer(extractor, font, text, x, cy, scale, 0f, color, 0f, 0f);
        reflection(extractor, font, text, x, cy + font.lineHeight * scale, scale, 0f,
                withAlpha(color, 0x33));
    }

    // 3 华彩：逐段亮起
    private static void cadenza(GuiGraphicsExtractor extractor, Font font, String text, float cx,
                                float cy, float base, float enter, int color) {
        List<String> words = split(text, 4);
        int total = 0;
        for (String word : words) {
            total += font.width(word);
        }
        float scale = base;
        float x = cx - total * scale / 2f;
        float lit = 1f - enter;
        for (String word : words) {
            int wordWidth = font.width(word);
            int wordColor = (x - (cx - total * scale / 2f)) / Math.max(1f, total * scale) < lit
                    ? color : withAlpha(color, 0x55);
            layer(extractor, font, word, x, cy, scale, 0f, wordColor, 0f, 0f);
            x += wordWidth * scale;
        }
    }

    // 4 光晕：脉动
    private static void luminous(GuiGraphicsExtractor extractor, Font font, String text, float cx,
                                 float cy, float base, float enter, int color, long now) {
        float pulse = 0.75f + 0.25f * (float) Math.sin(now / 420_000_000.0);
        float scale = base * 1.1f;
        float x = cx - font.width(text) * scale / 2f;
        for (int i = 4; i >= 1; i--) {
            layer(extractor, font, text, x, cy, scale, 0f,
                    withAlpha(color, (int) (0x30 * pulse)), 0f, i * 2f * scale);
        }
        layer(extractor, font, text, x, cy, scale, 0f, color, 0f, 0f);
    }

    // 5 烟雾：向上漂移
    private static void fume(GuiGraphicsExtractor extractor, Font font, String text, float cx,
                             float cy, float base, float enter, int color, long now) {
        float drift = (float) Math.sin(now / 900_000_000.0) * 6f;
        float scale = base;
        float x = cx - font.width(text) * scale / 2f;
        for (int i = 3; i >= 1; i--) {
            layer(extractor, font, text, x + drift * i * 0.4f, cy - i * 3.5f * scale,
                    scale * (1f - i * 0.02f), 0f, withAlpha(color, 0x22), 0f, 0f);
        }
        layer(extractor, font, text, x, cy, scale, 0f, color, 0f, 0f);
    }

    // 6 钟摆：整体轻微摆动
    private static void pendolo(GuiGraphicsExtractor extractor, Font font, String text, float cx,
                                float cy, float base, float enter, int color, long now) {
        float angle = (float) Math.sin(now / 800_000_000.0) * 0.055f;
        float scale = base * 1.05f;
        float x = cx - font.width(text) * scale / 2f;
        layer(extractor, font, text, x, cy, scale, angle, withAlpha(color, 0x66), 0f, 2f);
        layer(extractor, font, text, x, cy, scale, angle, color, 0f, 0f);
    }

    // 7 合唱：两段左右错位
    private static void cappella(GuiGraphicsExtractor extractor, Font font, String text, float cx,
                                 float cy, float base, float enter, int color) {
        List<String> segments = split(text, 2);
        int count = segments.size();
        for (int i = 0; i < count; i++) {
            String segment = segments.get(i);
            float scale = base * 0.98f;
            float x = cx - font.width(segment) * scale / 2f + (i == 0 ? -1 : 1) * base * 8f;
            float y = cy + (i - (count - 1) / 2f) * base * 13f;
            layer(extractor, font, segment, x, y, scale, i == 0 ? -0.08f : 0.08f,
                    color, 0f, 0f);
        }
    }

    private static void layer(GuiGraphicsExtractor extractor, Font font, String text, float x,
                              float y, float scale, float angle, int color, float offX,
                              float offY) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x + offX, y + offY);
        pose.rotate(angle);
        pose.scale(scale, scale);
        extractor.text(font, text, 0, 0, color, true);
        pose.popMatrix();
    }

    private static void reflection(GuiGraphicsExtractor extractor, Font font, String text,
                                   float x, float y, float scale, float angle, int color) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate(-angle);
        pose.scale(scale, -scale * 0.58f);
        extractor.text(font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    private static List<String> split(String text, int maxParts) {
        List<String> result = new ArrayList<>();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return result;
        }
        int target = Math.max(1, Math.min(maxParts, Math.round(trimmed.length() / 12f)));
        if (target <= 1 || trimmed.length() <= 9) {
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
