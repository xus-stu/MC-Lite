package dev.mclite.island.mc26;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 灵动岛的 Java2D 底图（纯 AWT，方便单独渲染预览）。
 *
 * <p>参考 iPhone 灵动岛 / MacBook 刘海：一块很小的黑色胶囊，不放大封面、
 * 不做大卡片。真正的内容只有左侧跳动的电平和一行标题/歌词。</p>
 */
final class HudPaint26 {

    static final Color TOP = new Color(24, 26, 32, 246);
    static final Color BOTTOM = new Color(8, 9, 12, 250);
    static final Color LINE = new Color(255, 255, 255, 30);

    private HudPaint26() {
    }

    static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    static void pill(Graphics2D g, int w, int h) {
        prepare(g);
        int radius = h;

        // 外投影：小胶囊也要有一点浮空感
        for (int spread = 6; spread >= 1; spread--) {
            g.setColor(new Color(0, 0, 0, 2 + (6 - spread)));
            g.fill(new RoundRectangle2D.Double(-spread, -spread + 2,
                    w + spread * 2, h + spread * 2 + 1,
                    radius + spread * 2, radius + spread * 2));
        }

        RoundRectangle2D body = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1,
                radius, radius);
        g.setPaint(new GradientPaint(0, 0, TOP, 0, h, BOTTOM));
        g.fill(body);

        Shape old = g.getClip();
        g.clip(body);
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(w * 0.18f, h * 0.3f), Math.max(36f, h * 1.6f),
                new float[]{0f, 1f},
                new Color[]{new Color(255, 168, 200, 18), new Color(255, 168, 200, 0)}));
        g.fillRect(0, 0, w, h);
        g.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 22),
                0, Math.min(h, 12), new Color(255, 255, 255, 0)));
        g.fillRect(0, 1, w, Math.min(h, 12));
        g.setClip(old);

        g.setColor(LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(1.0, 1.0, w - 2, h - 2,
                Math.max(1, radius - 1), Math.max(1, radius - 1)));
    }
}
