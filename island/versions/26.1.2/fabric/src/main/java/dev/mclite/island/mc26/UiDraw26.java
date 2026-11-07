package dev.mclite.island.mc26;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** 26.x 九宫格绘制：圆角卡片/按钮，避免逐行 fill。 */
final class UiDraw26 {

    static final Identifier CARD = tex("panel_card");
    static final Identifier HOVER = tex("panel_hover");
    static final Identifier DARK = tex("panel_dark");
    static final Identifier ACCENT = tex("panel_accent");
    static final Identifier SOFT = tex("panel_soft");
    static final Identifier DANGER = tex("panel_danger");
    static final Identifier WHITE = tex("panel_white");
    static final Identifier BORDER = tex("panel_border");

    private static final int TEX = 64;
    private static final int SRC = 20;

    private UiDraw26() {
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("mclite-island", "textures/gui/" + name + ".png");
    }

    static void panel(GuiGraphicsExtractor ex, Identifier texture,
                      int x, int y, int w, int h, int radius) {
        int c = Math.max(2, Math.min(Math.min(radius, SRC), Math.min(w, h) / 2));
        float u0 = 0f;
        float v0 = 0f;
        float u1 = c / (float) TEX;
        float v1 = c / (float) TEX;
        float ue = (TEX - c) / (float) TEX;
        float ve = (TEX - c) / (float) TEX;
        // 四角
        ex.blit(texture, x, y, c, c, u0, v0, u1, v1);
        ex.blit(texture, x + w - c, y, c, c, ue, v0, 1f, v1);
        ex.blit(texture, x, y + h - c, c, c, u0, ve, u1, 1f);
        ex.blit(texture, x + w - c, y + h - c, c, c, ue, ve, 1f, 1f);
        // 四边
        if (w > 2 * c) {
            ex.blit(texture, x + c, y, w - 2 * c, c, u1, v0, ue, v1);
            ex.blit(texture, x + c, y + h - c, w - 2 * c, c, u1, ve, ue, 1f);
        }
        if (h > 2 * c) {
            ex.blit(texture, x, y + c, c, h - 2 * c, u0, v1, u1, ve);
            ex.blit(texture, x + w - c, y + c, c, h - 2 * c, ue, v1, 1f, ve);
        }
        // 中心
        if (w > 2 * c && h > 2 * c) {
            ex.blit(texture, x + c, y + c, w - 2 * c, h - 2 * c, u1, v1, ue, ve);
        }
        panel(ex, BORDER, x, y, w, h, radius, true);
    }

    private static void panel(GuiGraphicsExtractor ex, Identifier border, int x, int y,
                              int w, int h, int radius, boolean ignored) {
        // 只画描边：用同一套九宫格，纹理本身是透明的，只含 1px 边
        int c = Math.max(2, Math.min(Math.min(radius, SRC), Math.min(w, h) / 2));
        float u1 = c / (float) TEX;
        float ue = (TEX - c) / (float) TEX;
        float v1 = c / (float) TEX;
        float ve = (TEX - c) / (float) TEX;
        ex.blit(border, x, y, c, c, 0f, 0f, u1, v1);
        ex.blit(border, x + w - c, y, c, c, ue, 0f, 1f, v1);
        ex.blit(border, x, y + h - c, c, c, 0f, ve, u1, 1f);
        ex.blit(border, x + w - c, y + h - c, c, c, ue, ve, 1f, 1f);
        if (w > 2 * c) {
            ex.blit(border, x + c, y, w - 2 * c, c, u1, 0f, ue, v1);
            ex.blit(border, x + c, y + h - c, w - 2 * c, c, u1, ve, ue, 1f);
        }
        if (h > 2 * c) {
            ex.blit(border, x, y + c, c, h - 2 * c, 0f, v1, u1, ve);
            ex.blit(border, x + w - c, y + c, c, h - 2 * c, ue, v1, 1f, ve);
        }
        if (w > 2 * c && h > 2 * c) {
            ex.blit(border, x + c, y + c, w - 2 * c, h - 2 * c, u1, v1, ue, ve);
        }
    }
}
