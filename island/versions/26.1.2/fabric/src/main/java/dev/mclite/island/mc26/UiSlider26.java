package dev.mclite.island.mc26;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 简单的自绘滑杆：一条 4px 轨道 + 圆角填充 + 白色把手。
 * 不依赖原版 AbstractSliderButton，支持点击跳转和拖动。
 */
final class UiSlider26 {

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean dragging;

    void bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    float fractionAt(double mouseX) {
        int inner = Math.max(1, width - 8);
        return Math.max(0f, Math.min(1f, (float) ((mouseX - x - 4) / inner)));
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        return true;
    }

    boolean mouseDragged(double mouseX) {
        return dragging;
    }

    void release() {
        dragging = false;
    }

    boolean isDragging() {
        return dragging;
    }

    void render(GuiGraphicsExtractor extractor, float fraction, boolean hover) {
        if (width <= 0) {
            return;
        }
        float value = Math.max(0f, Math.min(1f, fraction));
        int trackY = y + (height - 4) / 2;
        if (dragging || hover) {
            UiDraw26.fillRounded(extractor, x - 1, trackY - 2, width + 2, 8, 4, 0x1A6CC6FF);
        }
        UiDraw26.fillRounded(extractor, x, trackY, width, 4, 2,
                hover || dragging ? 0x44FFFFFF : 0x2AFFFFFF);
        int filled = 4 + Math.round((width - 8) * value);
        if (filled > 0) {
            UiDraw26.fillRounded(extractor, x, trackY, filled, 4, 2,
                    dragging ? 0xFF9AD8FF : 0xFF6CC6FF);
        }
        int knobX = x + 4 + Math.round((width - 8) * value) - 5;
        int knobY = y + (height - 12) / 2;
        UiDraw26.fillRounded(extractor, knobX - 1, knobY + 1, 12, 12, 6, 0x44000000);
        UiDraw26.panel(extractor, UiDraw26.WHITE, knobX, knobY, 10, 12);
    }
}
