package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 26.x 灵动岛：居中顶部胶囊，纯原版绘制。 */
final class IslandHud26 {

    private static final int BORDER = 0x55FFFFFF;
    private static final int BG = 0xE612171D;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF9AA4B0;
    private static final int ACCENT = 0xFF7CC7FF;
    private static final int BASE_WIDTH = 260;
    private static final int BASE_HEIGHT = 30;
    private static final int RADIUS = 14;

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
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int width = BASE_WIDTH;
        int height = BASE_HEIGHT;
        int x = (screenWidth - width) / 2 + config.islandX;
        int y = 6 + config.islandY;

        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(config.islandScale, config.islandScale);

        // 外圈 = 描边，内圈 = 背景，用逐行填充伪造圆角
        fillRounded(extractor, 0, 0, width, height, RADIUS, BORDER);
        fillRounded(extractor, 1, 1, width - 2, height - 2, RADIUS - 1, BG);

        var font = client.font;
        String line1;
        String line2;
        if (snapshot.hasTrack()) {
            line1 = (snapshot.paused() ? "|| " : "♪ ") + snapshot.title();
            if (snapshot.lyric() != null && !snapshot.lyric().isBlank()) {
                line2 = snapshot.lyric();
            } else if (snapshot.hasContext() && config.contextWhilePlaying) {
                line2 = snapshot.contextTitle();
            } else {
                line2 = snapshot.artist();
            }
        } else if (snapshot.hasContext()) {
            line1 = snapshot.contextTitle();
            line2 = snapshot.contextSubtitle();
        } else {
            line1 = "MCLite Island";
            line2 = service.playlist().isEmpty() ? "M 打开 · 音乐目录为空" : "M 打开播放器";
        }

        int innerWidth = width - 24;
        String shown1 = trim(font, line1, innerWidth);
        String shown2 = trim(font, line2, innerWidth);
        int color2 = snapshot.hasTrack() ? ACCENT : DIM;
        extractor.text(font, shown1, (width - font.width(shown1)) / 2, 5, TEXT, false);
        extractor.text(font, shown2, (width - font.width(shown2)) / 2, 16, color2, false);

        if (snapshot.hasTrack()) {
            long duration = snapshot.durationMs();
            if (duration > 0L) {
                int barWidth = (int) ((width - 40) * Math.min(1f,
                        snapshot.positionMs() / (float) duration));
                if (barWidth > 0) {
                    int barY = height - 5;
                    int barX = 20;
                    extractor.fill(barX, barY, barX + barWidth, barY + 2, ACCENT);
                }
            }
        }
        pose.popMatrix();
    }

    private static void fillRounded(GuiGraphicsExtractor extractor, int x, int y,
                                    int width, int height, int radius, int color) {
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int edge = Math.min(row, height - 1 - row);
            int inset = 0;
            if (edge < safeRadius) {
                int dx = safeRadius - edge;
                inset = safeRadius - (int) Math.round(
                        Math.sqrt(Math.max(0, safeRadius * safeRadius - dx * dx)));
            }
            extractor.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static String trim(net.minecraft.client.gui.Font font, String text, int width) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }
}
