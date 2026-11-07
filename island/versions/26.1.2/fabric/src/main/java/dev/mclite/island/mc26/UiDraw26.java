package dev.mclite.island.mc26;

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

    private UiDraw26() {
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(NS, "mclite/" + name);
    }

    static void panel(GuiGraphicsExtractor ex, Identifier sprite, int x, int y, int w, int h, int radius) {
        ex.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
    }

    static void panel(GuiGraphicsExtractor ex, Identifier sprite, int x, int y, int w, int h) {
        panel(ex, sprite, x, y, w, h, 0);
    }

    /** 兜底：没有 sprite 的场合（例如进度条）用矩形。 */
    static void fillRounded(GuiGraphicsExtractor ex, int x, int y, int w, int h, int radius, int color) {
        ex.fill(x, y, x + w, y + h, color);
    }
}
