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
    private Style style;

    UiButton26(String label, int x, int y, int width, int height, Style style, Runnable action) {
        super(x, y, width, height, Component.literal(label == null ? "" : label));
        this.action = action;
        this.style = style;
    }

    static UiButton26 primary(String label, int x, int y, int w, int h, Runnable action) {
        return new UiButton26(label, x, y, w, h, Style.PRIMARY, action);
    }

    static UiButton26 ghost(String label, int x, int y, int w, int h, Runnable action) {
        return new UiButton26(label, x, y, w, h, Style.GHOST, action);
    }

    static UiButton26 soft(String label, int x, int y, int w, int h, Runnable action) {
        return new UiButton26(label, x, y, w, h, Style.SOFT, action);
    }

    void setLabel(String label) {
        String next = label == null ? "" : label;
        if (!next.equals(getMessage().getString())) {
            setMessage(Component.literal(next));
        }
    }

    void setStyle(Style next) {
        if (next != null && next != style) {
            style = next;
        }
    }

    Style style() {
        return style;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                            float delta) {
        boolean hover = isHovered() && active;
        int w = getWidth();
        int h = getHeight();
        switch (style) {
            case PRIMARY -> {
                UiDraw26.panel(extractor, hover ? UiDraw26.WHITE : UiDraw26.ACCENT,
                        getX(), getY(), w, h);
                // 顶部 1px 高光，避免整块纯色显得像贴图
                extractor.fill(getX() + 8, getY() + 1, getX() + w - 8, getY() + 2,
                        0x30FFFFFF);
            }
            case DANGER -> UiDraw26.panel(extractor, UiDraw26.DANGER, getX(), getY(), w, h);
            case SOFT -> UiDraw26.panel(extractor, hover ? UiDraw26.ON : UiDraw26.SOFT,
                    getX(), getY(), w, h);
            case GHOST -> {
                if (hover) {
                    UiDraw26.panel(extractor, UiDraw26.HOVER, getX(), getY(), w, h);
                } else {
                    // 很淡的软底，让“幽灵按钮”仍有圆角形状可循
                    UiDraw26.panelTinted(extractor, UiDraw26.SOFT, getX(), getY(), w, h,
                            0x70FFFFFF);
                }
            }
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String label = UiText26.fit(font, getMessage().getString(), w - 8);
        int color = switch (style) {
            case PRIMARY -> 0xFF0E1216;
            case DANGER -> 0xFFFFFFFF;
            case GHOST -> hover ? 0xFFFFFFFF : 0xFFD7DEE6;
            default -> active ? 0xFFF2F5F8 : 0xFF7A8794;
        };
        UiDraw26.textCentered(extractor, font, label, getX() + w / 2, getY() + (h - 8) / 2, color);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (active) {
            action.run();
        }
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
