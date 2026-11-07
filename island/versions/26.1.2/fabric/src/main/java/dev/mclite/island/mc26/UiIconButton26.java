package dev.mclite.island.mc26;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 传输按钮：只有图标，没有圆底/胶囊底。
 *
 * <p>播放键用主题粉色，上一首/下一首用浅灰；hover 只提亮图标颜色，
 * 不再画一整块底色，保持现代播放器的干净感。</p>
 */
final class UiIconButton26 extends AbstractWidget {

    enum Icon { PREV, PLAY, PAUSE, NEXT }

    // 参考 Music Player / 画境：图标本身保持中性白，主题色只用于进度和选中态。
    private static final int PLAIN = 0xFFD7DEE8;
    private static final int PLAIN_HOVER = 0xFFFFFFFF;

    private final Runnable action;
    private final boolean primary;
    private Icon icon;

    UiIconButton26(Icon icon, int x, int y, int width, int height, boolean primary,
                   Runnable action) {
        super(x, y, width, height, Component.literal(label(icon)));
        this.icon = icon;
        this.primary = primary;
        this.action = action;
    }

    void setIcon(Icon next) {
        if (next != null && next != icon) {
            icon = next;
            setMessage(Component.literal(label(next)));
        }
    }

    private static String label(Icon icon) {
        return switch (icon) {
            case PREV -> "上一首";
            case PLAY -> "播放";
            case PAUSE -> "暂停";
            case NEXT -> "下一首";
        };
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                            float delta) {
        boolean hover = isHovered() && active;
        int color = hover ? PLAIN_HOVER : PLAIN;
        int cx = getX() + getWidth() / 2;
        int cy = getY() + getHeight() / 2;
        switch (icon) {
            case PLAY -> triangle(extractor, cx - 5, cy, 11, 14, color);
            case PAUSE -> {
                extractor.fill(cx - 5, cy - 7, cx - 2, cy + 7, color);
                extractor.fill(cx + 2, cy - 7, cx + 5, cy + 7, color);
            }
            case PREV -> {
                extractor.fill(cx - 8, cy - 6, cx - 6, cy + 6, color);
                triangleLeft(extractor, cx + 5, cy, 11, 13, color);
            }
            case NEXT -> {
                triangle(extractor, cx - 5, cy, 11, 13, color);
                extractor.fill(cx + 6, cy - 6, cx + 8, cy + 6, color);
            }
        }
    }

    /** 右向三角形：一列一列填。 */
    private static void triangle(GuiGraphicsExtractor extractor, int left, int cy,
                                 int width, int height, int color) {
        int half = height / 2;
        for (int i = 0; i < width; i += 2) {
            int h = Math.max(1, half - i * half / width);
            extractor.fill(left + i, cy - h, left + i + 2, cy + h, color);
        }
    }

    private static void triangleLeft(GuiGraphicsExtractor extractor, int right, int cy,
                                     int width, int height, int color) {
        int half = height / 2;
        for (int i = 0; i < width; i += 2) {
            int h = Math.max(1, half - i * half / width);
            extractor.fill(right - i - 2, cy - h, right - i, cy + h, color);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (active) {
            action.run();
        }
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        // 自绘按钮不播音效
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
