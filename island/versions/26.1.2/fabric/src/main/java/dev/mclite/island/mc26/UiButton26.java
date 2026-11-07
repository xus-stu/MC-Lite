package dev.mclite.island.mc26;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 自绘圆角按钮：主按钮/幽灵按钮/软底，hover 有颜色变化。 */
final class UiButton26 extends AbstractWidget {

    enum Style { PRIMARY, GHOST, SOFT, DANGER }

    private final Runnable action;
    private final int width;
    private final int height;
    private final Style style;

    UiButton26(String label, int x, int y, int width, int height, Style style, Runnable action) {
        super(x, y, width, height, Component.literal(label));
        this.action = action;
        this.width = width;
        this.height = height;
        this.style = style;
    }

    static UiButton26 primary(String label, int x, int y, int w, int h, Runnable action) {
        return new UiButton26(label, x, y, w, h, Style.PRIMARY, action);
    }

    static UiButton26 ghost(String label, int x, int y, int w, int h, Runnable action) {
        return new UiButton26(label, x, y, w, h, Style.GHOST, action);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                            float delta) {
        boolean hover = isHovered();
        var texture = switch (style) {
            case PRIMARY -> hover ? UiDraw26.SOFT : UiDraw26.ACCENT;
            case DANGER -> UiDraw26.DANGER;
            case SOFT -> hover ? UiDraw26.HOVER : UiDraw26.SOFT;
            case GHOST -> hover ? UiDraw26.HOVER : null;
        };
        if (texture != null) {
            UiDraw26.panel(extractor, texture, getX(), getY(), width, height, 10);
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String label = getMessage().getString();
        int color = switch (style) {
            case PRIMARY -> 0xFF0E1216;
            case DANGER -> 0xFFFFFFFF;
            default -> 0xFFF2F5F8;
        };
        extractor.text(font, label, getX() + (width - font.width(label)) / 2,
                getY() + (height - 8) / 2, color, false);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        action.run();
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        // 自绘按钮不播音效，避免设置页频繁点击吵
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
