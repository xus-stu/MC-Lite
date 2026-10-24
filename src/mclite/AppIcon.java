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

    private static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(new Color(0x151515));
        g.fill(new RoundRectangle2D.Float(8, 8, size - 16, size - 16, 54, 54));
        g.setColor(new Color(0xF5F5F5));

        int cupX = 60;
        int cupY = 105;
        int cupW = 108;
        int cupH = 82;
        g.setStroke(new BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D cup = new Path2D.Float();
        cup.moveTo(cupX, cupY);
        cup.lineTo(cupX + cupW, cupY);
        cup.quadTo(cupX + cupW - 8, cupY + cupH - 20, cupX + cupW / 2, cupY + cupH);
        cup.quadTo(cupX + 8, cupY + cupH - 20, cupX, cupY);
        g.draw(cup);
        g.drawArc(cupX + cupW - 4, cupY + 18, 48, 44, -80, 180);
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cupX - 6, 214, cupX + cupW + 20, 214);

        g.setColor(new Color(0xF5F5F5));
        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 2; i++) {
            int x = 92 + i * 40;
            Path2D steam = new Path2D.Float();
            steam.moveTo(x, 92);
            steam.curveTo(x - 16, 74, x + 16, 58, x, 40);
            g.draw(steam);
        }
        g.dispose();
        return image;
    }
}
