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

    /** 标题栏/侧边栏用：透明底 + 主题色信标，小尺寸也清晰。 */
    public static BufferedImage glyph(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 32f, size / 32f);

        // 光束
        Path2D beam = new Path2D.Float();
        beam.moveTo(13.5f, 2f);
        beam.lineTo(18.5f, 2f);
        beam.lineTo(21f, 13f);
        beam.lineTo(11f, 13f);
        beam.closePath();
        g.setColor(new Color(Theme.ACCENT.getRGB() | 0xFF000000, true));
        g.setColor(Theme.alpha(Theme.ACCENT, 150));
        g.fill(beam);

        // 三层底座
        g.setColor(Theme.mix(Theme.ACCENT, Theme.BG, 0.25f));
        tier(g, 16f, 29.5f, 26f, 24.5f);
        g.setColor(Theme.mix(Theme.ACCENT, Theme.BG, 0.05f));
        tier(g, 16f, 24.5f, 19f, 20f);
        g.setColor(Theme.ACCENT);
        tier(g, 16f, 20f, 12f, 15.5f);

        // 核心宝石
        Path2D gem = new Path2D.Float();
        gem.moveTo(16f, 10.5f);
        gem.lineTo(19f, 14f);
        gem.lineTo(16f, 17.5f);
        gem.lineTo(13f, 14f);
        gem.closePath();
        g.setColor(Theme.TEXT);
        g.fill(gem);
        g.dispose();
        return image;
    }

    private static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 256f, size / 256f);

        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x14221F),
                0, 256, new Color(0x0A1211)));
        g.fill(new RoundRectangle2D.Float(8, 8, 240, 240, 54, 54));

        // 光束：粗一点，小尺寸也认得出
        Path2D beam = new Path2D.Float();
        beam.moveTo(110, 30);
        beam.lineTo(146, 30);
        beam.lineTo(172, 122);
        beam.lineTo(84, 122);
        beam.closePath();
        g.setPaint(new java.awt.GradientPaint(128, 26,
                new Color(0x5FE3D0, true), 128, 122, new Color(0x8BFFF0)));
        g.setColor(new Color(0x6FF3E2));
        g.setPaint(new java.awt.GradientPaint(128, 26, new Color(95, 227, 208, 40),
                128, 122, new Color(139, 255, 240)));
        g.fill(beam);

        // 三层底座
        g.setColor(new Color(0x2A4A45));
        tier(g, 128, 218, 192, 188);
        g.setColor(new Color(0x356059));
        tier(g, 128, 188, 142, 164);
        g.setColor(new Color(0x417A70));
        tier(g, 128, 164, 94, 140);

        // 底座发光边
        g.setColor(new Color(0x8BFFF0, true));
        g.setColor(new Color(139, 255, 240, 90));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(32, 216, 224, 216);

        // 核心宝石
        Path2D gem = new Path2D.Float();
        gem.moveTo(128, 92);
        gem.lineTo(158, 120);
        gem.lineTo(128, 148);
        gem.lineTo(98, 120);
        gem.closePath();
        g.setColor(new Color(0x9FF7EA));
        g.fill(gem);
        g.setColor(new Color(0xE6FFFB));
        g.setStroke(new BasicStroke(4f));
        g.draw(gem);

        g.dispose();
        return image;
    }

    private static void tier(Graphics2D g, float centerX, float baseY, float width, float topY) {
        float topW = width * 0.6f;
        Path2D shape = new Path2D.Float();
        shape.moveTo(centerX - width / 2f, baseY);
        shape.lineTo(centerX + width / 2f, baseY);
        shape.lineTo(centerX + topW / 2f, topY);
        shape.lineTo(centerX - topW / 2f, topY);
        shape.closePath();
        g.fill(shape);
    }

    private static void tier(Graphics2D g, int centerX, int baseY, int width, int topY) {
        int topW = Math.round(width * 0.6f);
        Path2D shape = new Path2D.Float();
        shape.moveTo(centerX - width / 2f, baseY);
        shape.lineTo(centerX + width / 2f, baseY);
        shape.lineTo(centerX + topW / 2f, topY);
        shape.lineTo(centerX - topW / 2f, topY);
        shape.closePath();
        g.fill(shape);
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
