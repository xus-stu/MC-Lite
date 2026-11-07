package dev.mclite.island.mc26;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** 26.x 圆角面板：使用 GUI sprite 的 nine_slice，自带抗锯齿。 */
final class UiDraw26 {

    static final String NS = "mclite-island";
    static final Identifier CARD = sprite("card");
    static final Identifier HOVER = sprite("hover");
    static final Identifier DARK = sprite("dark");
    static final Identifier ACCENT = sprite("accent");
    static final Identifier SOFT = sprite("soft");
    static final Identifier DANGER = sprite("danger");
    static final Identifier WHITE = sprite("white");
    static final Identifier ON = sprite("on");
    static final Identifier OFF = sprite("off");

    static final int TEXT = 0xFFF2F5F8;
    static final int DIM = 0xFF93A0AD;
    static final int MUTED = 0xFF6B7885;
    static final int ACCENT_TEXT = 0xFF8FD3FF;
    static final int DANGER_TEXT = 0xFFFF8A8A;
    static final int STROKE = 0x2EFFFFFF;

    private UiDraw26() {
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(NS, "mclite/" + name);
    }

    static void panel(GuiGraphicsExtractor ex, Identifier sprite, int x, int y, int w, int h, int radius) {
        if (w <= 0 || h <= 0) {
            return;
        }
        ex.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
    }

    static void panel(GuiGraphicsExtractor ex, Identifier sprite, int x, int y, int w, int h) {
        panel(ex, sprite, x, y, w, h, 0);
    }

    /** 带 alpha 的 sprite 着色（用于把胶囊再压透一点）。 */
    static void panelTinted(GuiGraphicsExtractor ex, Identifier sprite, int x, int y, int w, int h,
                            int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        ex.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, color);
    }

    /** 细描边：先铺一层浅色圆角，再用面板盖住内部，只留 1px 边缘。 */
    static void panelWithStroke(GuiGraphicsExtractor ex, Identifier sprite, int x, int y,
                                int w, int h, int radius, int strokeColor) {
        fillRounded(ex, x - 1, y - 1, w + 2, h + 2, radius + 1, strokeColor);
        panel(ex, sprite, x, y, w, h, radius);
    }

    /**
     * 兜底圆角矩形：只画上下两段圆角行 + 中间一整块，不逐行铺满。
     * 正常路径优先用 nine_slice；这个只在滑杆、描边等小元素上使用。
     */
    static void fillRounded(GuiGraphicsExtractor ex, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            ex.fill(x, y, x + w, y + h, color);
            return;
        }
        ex.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            int dy = r - row;
            int inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            int left = x + inset;
            int right = x + w - inset;
            if (right <= left) {
                continue;
            }
            ex.fill(left, y + row, right, y + row + 1, color);
            int bottom = y + h - 1 - row;
            ex.fill(left, bottom, right, bottom + 1, color);
        }
    }

    static void textCentered(GuiGraphicsExtractor ex, Font font, String text, int centerX, int y,
                             int color) {
        ex.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static void textRight(GuiGraphicsExtractor ex, Font font, String text, int right, int y,
                          int color) {
        ex.text(font, text, right - font.width(text), y, color, false);
    }

    static boolean hovered(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
