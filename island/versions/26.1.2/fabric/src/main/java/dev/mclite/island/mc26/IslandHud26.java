package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandLayout;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 26.x 灵动岛：GuiGraphicsExtractor + Matrix3x2fStack，纯原版绘制。 */
final class IslandHud26 {

    private static final int BG = 0xD0101418;
    private static final int BORDER = 0x40FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF9AA4B0;
    private static final int ACCENT = 0xFF7CC7FF;

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
        if (!snapshot.hasTrack() && !snapshot.hasContext()) {
            return;
        }
        int width = IslandLayout.BASE_WIDTH;
        int height = IslandLayout.BASE_HEIGHT;
        IslandLayout layout = IslandLayout.fromConfig(config)
                .clampTo(client.getWindow().getGuiScaledWidth(),
                        client.getWindow().getGuiScaledHeight());

        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(layout.x(), layout.y());
        pose.scale(layout.scale(), layout.scale());

        extractor.fill(0, 0, width, height, BG);
        extractor.fill(0, 0, width, 1, BORDER);
        extractor.fill(0, height - 1, width, height, BORDER);

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
        } else {
            line1 = snapshot.contextTitle();
            line2 = snapshot.contextSubtitle();
        }
        extractor.text(font, trim(font, line1, width - 12), 6, 5, TEXT, false);
        extractor.text(font, trim(font, line2, width - 12), 6, 16,
                snapshot.hasTrack() ? ACCENT : DIM, false);

        if (snapshot.hasTrack()) {
            String time = formatTime(snapshot.positionMs());
            extractor.text(font, time, width - 6 - font.width(time), 5, DIM, false);
            long duration = snapshot.durationMs();
            if (duration > 0L) {
                int bar = (int) (width * Math.min(1f, snapshot.positionMs() / (float) duration));
                if (bar > 0) {
                    extractor.fill(0, height - 3, bar, height - 1, ACCENT);
                }
            }
        }
        pose.popMatrix();
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
        long seconds = Math.max(0L, millis) / 1000L;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
