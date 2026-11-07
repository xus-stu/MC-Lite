package dev.mclite.island.fabric;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandLayout;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** 灵动岛 HUD：纯原版绘制，不做模糊/抓帧。 */
final class IslandHud {

    private static final int BG = 0xD0101418;
    private static final int BORDER = 0x40FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF9AA4B0;
    private static final int ACCENT = 0xFF7CC7FF;

    private IslandHud() {
    }

    static void render(GuiGraphics graphics, IslandConfig config, PlaybackService service) {
        if (config == null || service == null || !config.showIsland) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return;
        }
        IslandSnapshot snapshot = service.snapshot();
        boolean music = snapshot.hasTrack();
        boolean context = snapshot.hasContext();

        IslandLayout layout = IslandLayout.fromConfig(config)
                .clampTo(graphics.guiWidth(), graphics.guiHeight());
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(layout.x(), layout.y(), 0);
        pose.scale(layout.scale(), layout.scale(), 1f);

        int width = IslandLayout.BASE_WIDTH;
        int height = IslandLayout.BASE_HEIGHT;
        graphics.fill(0, 0, width, height, BG);
        graphics.fill(0, 0, width, 1, BORDER);
        graphics.fill(0, height - 1, width, height, BORDER);

        var font = client.font;
        String line1;
        String line2;
        if (music) {
            line1 = (snapshot.paused() ? "|| " : "♪ ") + snapshot.title();
            if (snapshot.lyric() != null && !snapshot.lyric().isBlank()) {
                line2 = snapshot.lyric();
            } else if (snapshot.hasContext() && config.contextWhilePlaying) {
                line2 = snapshot.contextTitle();
            } else {
                line2 = snapshot.artist();
            }
        } else if (context) {
            line1 = snapshot.contextTitle();
            line2 = snapshot.contextSubtitle();
        } else {
            line1 = "MCLite Island";
            line2 = service.playlist().isEmpty() ? "M 打开 · 音乐目录为空" : "M 打开播放器";
        }

        int maxText = width - 12;
        graphics.drawString(font, trim(font, line1, maxText), 6, 5, TEXT, false);
        graphics.drawString(font, trim(font, line2, maxText), 6, 16, music ? ACCENT : DIM, false);

        if (music) {
            String time = formatTime(snapshot.positionMs());
            graphics.drawString(font, time, width - 6 - font.width(time), 5, DIM, false);
            int barWidth = (int) (width * progress(snapshot));
            if (barWidth > 0) {
                graphics.fill(0, height - 3, Math.min(width, barWidth), height - 1, ACCENT);
            }
        }
        pose.popPose();
    }

    private static float progress(IslandSnapshot snapshot) {
        long duration = snapshot.durationMs();
        if (duration <= 0L) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, snapshot.positionMs() / (float) duration));
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

    private static String formatTime(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
