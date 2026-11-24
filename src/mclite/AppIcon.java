package mclite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class AppIcon {
    private AppIcon() {
    }

    public static void install(Window window) {
        BufferedImage image = draw(256);
        window.setIconImages(List.of(image));
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar.getTaskbar().setIconImage(image);
            }
        } catch (Exception ignored) {
        }
    }

    public static BufferedImage image(int size) {
        return draw(size);
    }

    private static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        float scale = size / 256f;
        g.scale(scale, scale);

        // 深色圆角底
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x10201E),
                0, 256, new Color(0x0A1413)));
        g.fill(new RoundRectangle2D.Float(8, 8, 240, 240, 54, 54));

        // 信标光束：从核心向上扩散，越往上越淡
        Path2D beam = new Path2D.Float();
        beam.moveTo(112, 58);
        beam.lineTo(144, 58);
        beam.lineTo(164, 150);
        beam.lineTo(92, 150);
        beam.closePath();
        g.setPaint(new java.awt.GradientPaint(128, 40,
                new Color(0x8BFFF0, true), 128, 152, new Color(0x43E8D2)));
        g.setColor(new Color(0x5FF7E4, true));
        g.setPaint(new java.awt.GradientPaint(128, 36, new Color(95, 247, 228, 30),
                128, 152, new Color(95, 247, 228, 210)));
        g.fill(beam);

        // 三层信标底座（金字塔）
        Color stone = new Color(0x2C4240);
        Color stoneLit = new Color(0x3A5654);
        drawTier(g, 128, 214, 150, 176, stone);
        drawTier(g, 128, 176, 108, 150, stoneLit);
        drawTier(g, 128, 150, 66, 126, stone);

        // 光束穿过底座的中缝
        g.setColor(new Color(95, 247, 228, 90));
        g.fillRect(124, 150, 8, 64);

        // 发光核心（菱形）
        Path2D core = new Path2D.Float();
        core.moveTo(128, 96);
        core.lineTo(148, 120);
        core.lineTo(128, 146);
        core.lineTo(108, 120);
        core.closePath();
        g.setColor(new Color(150, 255, 244, 60));
        g.fill(new java.awt.geom.Ellipse2D.Float(94, 92, 68, 68));
        g.setColor(new Color(0xD9FFFA));
        g.fill(core);
        g.setColor(new Color(0x3DBFAF));
        g.setStroke(new BasicStroke(4f));
        g.draw(core);

        g.dispose();
        return image;
    }

    private static void drawTier(Graphics2D g, int centerX, int baseY, int baseW, int topY,
                                 Color color) {
        int topW = Math.round(baseW * 0.62f);
        Path2D tier = new Path2D.Float();
        tier.moveTo(centerX - baseW / 2f, baseY);
        tier.lineTo(centerX + baseW / 2f, baseY);
        tier.lineTo(centerX + topW / 2f, topY);
        tier.lineTo(centerX - topW / 2f, topY);
        tier.closePath();
        g.setColor(color);
        g.fill(tier);
        }
}
