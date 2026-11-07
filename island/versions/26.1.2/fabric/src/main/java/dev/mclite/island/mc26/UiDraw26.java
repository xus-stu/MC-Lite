package dev.mclite.island.mc26;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * 圆角面板绘制：只在上下圆角区域逐行填充，中间用一整块矩形。
 * 26.x 的 blit UV 语义在无映射环境里不可靠，这里不用贴图，保证不会画出畸形弧线。
 */
final class UiDraw26 {

    static final Identifier CARD = Identifier.fromNamespaceAndPath("mclite-island", "card");
    static final Identifier HOVER = Identifier.fromNamespaceAndPath("mclite-island", "hover");
    static final Identifier DARK = Identifier.fromNamespaceAndPath("mclite-island", "dark");
    static final Identifier ACCENT = Identifier.fromNamespaceAndPath("mclite-island", "accent");
    static final Identifier SOFT = Identifier.fromNamespaceAndPath("mclite-island", "soft");
    static final Identifier DANGER = Identifier.fromNamespaceAndPath("mclite-island", "danger");
    static final Identifier WHITE = Identifier.fromNamespaceAndPath("mclite-island", "white");

    private UiDraw26() {
    }

    static void panel(GuiGraphicsExtractor ex, Identifier style, int x, int y, int w, int h, int radius) {
        int fill = color(style);
        fillRounded(ex, x, y, w, h, radius, fill);
        int border = style == WHITE ? 0x22000000 : 0x22FFFFFF;
        outline(ex, x, y, w, h, radius, border);
    }

    private static int color(Identifier style) {
        if (style == HOVER) {
            return 0xE61B232B;
        }
        if (style == DARK) {
            return 0xE60E1216;
        }
        if (style == ACCENT) {
            return 0xFF6CC6FF;
        }
        if (style == SOFT) {
            return 0x556CC6FF;
        }
        if (style == DANGER) {
            return 0xFFFF6B6B;
        }
        if (style == WHITE) {
            return 0xFFFFFFFF;
        }
        return 0xE6141A20;
    }

    static void fillRounded(GuiGraphicsExtractor ex, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            ex.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int row = 0; row < r; row++) {
            int inset = inset(r, row);
            ex.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            ex.fill(x + inset, y + h - 1 - row, x + w - inset, y + h - row, color);
        }
        ex.fill(x, y + r, x + w, y + h - r, color);
    }

    private static void outline(GuiGraphicsExtractor ex, int x, int y, int w, int h,
                                int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        for (int row = 0; row < r; row++) {
            int inset = inset(r, row);
            ex.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            ex.fill(x + inset, y + h - 1 - row, x + w - inset, y + h - row, color);
        }
        ex.fill(x, y + r, x + 1, y + h - r, color);
        ex.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    private static int inset(int radius, int row) {
        int dy = radius - row;
        return radius - (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
    }
}
