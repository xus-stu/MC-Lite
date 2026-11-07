package dev.mclite.island.mc26;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 圆形传输按钮（上一首/播放/暂停/下一首）。
 *
 * <p>圆底和 hover 态各烘焙成一张小纹理，每帧只 blit 一次 + 用 fill 画
 * 十几个矩形拼图标，不依赖字体里的 emoji，也不会每帧重画圆形。</p>
 */
final class UiIconButton26 extends AbstractWidget {

    enum Icon { PREV, PLAY, PAUSE, NEXT }

    private static final UISurface26.Painter NORMAL =
            (g, w, h) -> UiPaint26.transportButton(g, w, h, 0);
    private static final UISurface26.Painter HOVER =
            (g, w, h) -> UiPaint26.transportButton(g, w, h, 1);
    private static final UISurface26.Painter ACCENT =
            (g, w, h) -> UiPaint26.transportButton(g, w, h, 2);

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
        UISurface26.Painter painter = primary ? ACCENT : hover ? HOVER : NORMAL;
        String key = primary ? "transport/accent" : hover ? "transport/hover" : "transport/normal";
        UISurface26.Surface surface = UISurface26.get(key, getWidth(), getHeight(), painter,
                UiScale26.current());
        UISurface26.draw(extractor, surface, getX(), getY());
        int color = primary ? 0xFF0B1720 : 0xFFEAF2F8;
        int cx = getX() + getWidth() / 2;
        int cy = getY() + getHeight() / 2;
        switch (icon) {
            case PLAY -> triangle(extractor, cx - 4, cy, 11, 13, color);
            case PAUSE -> {
                extractor.fill(cx - 5, cy - 6, cx - 2, cy + 6, color);
                extractor.fill(cx + 2, cy - 6, cx + 5, cy + 6, color);
            }
            case PREV -> {
                extractor.fill(cx - 7, cy - 6, cx - 5, cy + 6, color);
                triangleLeft(extractor, cx + 5, cy, 10, 12, color);
            }
            case NEXT -> {
                triangle(extractor, cx - 5, cy, 10, 12, color);
                extractor.fill(cx + 5, cy - 6, cx + 7, cy + 6, color);
            }
        }
    }

    /** 右向三角形：一列一列填，边缘靠 2px 步进保持干净。 */
    private static void triangle(GuiGraphicsExtractor extractor, int left, int cy,
                                 int width, int height, int color) {
        int half = height / 2;
        for (int i = 0; i < width; i += 2) {
            int h = Math.max(1, half - i / 2);
            extractor.fill(left + i, cy - h, left + i + 2, cy + h, color);
        }
    }

    private static void triangleLeft(GuiGraphicsExtractor extractor, int right, int cy,
                                     int width, int height, int color) {
        int half = height / 2;
        for (int i = 0; i < width; i += 2) {
            int h = Math.max(1, half - i / 2);
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
