package dev.mclite.island.mc26;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 灵动岛的 Java2D 底图，纯 AWT，不依赖 Minecraft。
 *
 * <p>只画静态部分：胶囊、左侧封面块、细描边、柔和投影。文本、音量/进度、
 * 跳动的音量条由 {@link IslandHud26} 每帧用 {@code fill/text} 画，
 * 保证整体仍然只有一次纹理 blit。</p>
 *
 * <p>刻意不 import 任何 Minecraft 类型，方便用普通 javac 渲染 PNG 预览。</p>
 */
final class HudPaint26 {

    static final Color TOP = new Color(31, 41, 53, 214);
    static final Color BOTTOM = new Color(11, 15, 21, 232);
    static final Color LINE = new Color(255, 255, 255, 34);
    static final Color ART_TOP = new Color(126, 203, 255, 240);
    static final Color ART_BOTTOM = new Color(38, 78, 128, 250);

    /** 左侧封面块几何：调用方也用这组数字排版文本。 */
    static final int ART_SIZE = 32;
    static final int ART_X = 9;
    static final int TEXT_X = ART_X + ART_SIZE + 10;
    static final int PAD_RIGHT = 14;

    private HudPaint26() {
    }

    static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    /**
     * @param withArt 有歌曲时在左侧画封面块（无歌曲的待机胶囊不画）
     */
    static void pill(Graphics2D g, int w, int h, boolean withArt) {
        prepare(g);
        int radius = h;

        // 外投影：叠几层低透明度圆角矩形，烘焙后看不出阶梯
        for (int spread = 9; spread >= 1; spread--) {
            g.setColor(new Color(0, 0, 0, 2 + (9 - spread)));
            g.fill(new RoundRectangle2D.Double(-spread, -spread + 3,
                    w + spread * 2, h + spread * 2 + 2,
                    radius + spread * 2, radius + spread * 2));
        }

        RoundRectangle2D body = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, radius, radius);
        g.setPaint(new GradientPaint(0, 0, TOP, 0, h, BOTTOM));
        g.fill(body);

        Shape old = g.getClip();
        g.clip(body);
        // 左上角淡蓝高光，让黑色胶囊有一点“玻璃”层次而不是纯黑
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(w * 0.30f, h * 0.42f), Math.max(40f, h * 1.9f),
                new float[]{0f, 1f},
                new Color[]{new Color(96, 178, 255, 26), new Color(96, 178, 255, 0)}));
        g.fillRect(0, 0, w, h);
        // 顶部内侧高光
        g.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 26),
                0, Math.min(h, 18), new Color(255, 255, 255, 0)));
        g.fillRect(0, 1, w, Math.min(h, 18));
        g.setClip(old);

        g.setColor(LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(1.0, 1.0, w - 2, h - 2,
                Math.max(1, radius - 1), Math.max(1, radius - 1)));

        if (withArt) {
            art(g, ART_X, (h - ART_SIZE) / 2, ART_SIZE);
        }
    }

    /** 32x32 的渐变封面块：斜向高光 + 细描边，代替真实专辑封面。 */
    static void art(Graphics2D g, int x, int y, int size) {
        int radius = 10;
        RoundRectangle2D tile = new RoundRectangle2D.Double(x + 0.5, y + 0.5,
                size - 1, size - 1, radius, radius);

        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(x, y + 2, size, size, radius, radius));

        g.setPaint(new GradientPaint(x, y, ART_TOP, x + size, y + size, ART_BOTTOM));
        g.fill(tile);

        Shape old = g.getClip();
        g.clip(tile);
        Path2D shine = new Path2D.Double();
        shine.moveTo(x, y);
        shine.lineTo(x + size * 0.72, y);
        shine.lineTo(x, y + size * 0.52);
        shine.closePath();
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(shine);
        g.setClip(old);

        g.setColor(new Color(255, 255, 255, 64));
        g.setStroke(new BasicStroke(1f));
        g.draw(tile);
    }

    /** 胶囊底部一条 1px 的平台高光，把进度条和文字区分开。 */
    static void separator(Graphics2D g, int x, int y, int w) {
        g.setColor(new Color(255, 255, 255, 16));
        g.fillRect(x, y, w, 1);
    }
}
