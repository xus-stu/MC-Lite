package dev.mclite.island.mc26;

import net.minecraft.client.gui.Font;

/**
 * 一个小型文本缓存：只有原文或可用宽度变化时才重新截断/测量。
 * 界面每帧都会请求同一批字符串，用它可以避免每帧分配。
 */
final class UiText26 {

    private String source = "";
    private String shown = "";
    private int maxWidth = -1;
    private int width;
    private Font sourceFont;

    void set(Font font, String text, int available) {
        String safe = text == null ? "" : text;
        if (font == sourceFont && safe.equals(source) && available == maxWidth) {
            return;
        }
        sourceFont = font;
        source = safe;
        maxWidth = available;
        if (available <= 0) {
            shown = "";
            width = 0;
            return;
        }
        int full = font.width(safe);
        if (full <= available) {
            shown = safe;
            width = full;
            return;
        }
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        shown = font.plainSubstrByWidth(safe, Math.max(0, available - ellipsisWidth)) + ellipsis;
        width = font.width(shown);
    }

    String text() {
        return shown;
    }

    int width() {
        return width;
    }

    void clear() {
        source = "";
        shown = "";
        maxWidth = -1;
        width = 0;
        sourceFont = null;
    }

    static String fit(Font font, String text, int available) {
        if (text == null || text.isEmpty() || available <= 0) {
            return "";
        }
        if (font.width(text) <= available) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        return font.plainSubstrByWidth(text, Math.max(0, available - ellipsisWidth)) + ellipsis;
    }
}
