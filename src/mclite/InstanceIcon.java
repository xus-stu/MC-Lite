package mclite;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InstanceIcon extends JComponent {
    private final int size;
    private BufferedImage image;
    private String loader = "vanilla";
    private String key;

    public InstanceIcon(int size) {
        this.size = size;
        setPreferredSize(new java.awt.Dimension(size, size));
        setMinimumSize(new java.awt.Dimension(size, size));
        setMaximumSize(new java.awt.Dimension(size, size));
    }

    public void setInstance(String versionId, Path versionJson, Path contentDir) {
        String next = versionId + "|" + versionJson + "|" + contentDir;
        if (next.equals(key)) {
            return;
        }
        key = next;
        loader = detectLoader(versionId, versionJson);
        image = loadImage(contentDir);
        repaint();
    }

    private BufferedImage loadImage(Path dir) {
        if (dir == null) {
            return null;
        }
        String[] names = {"icon.png", "icon.jpg", "icon.jpeg", "icon.gif", "icon.webp",
                "pack.png", "modpack.png", ".mclite/icon.png", "cover.png"};
        for (String name : names) {
            Path file = dir.resolve(name);
            if (Files.isRegularFile(file)) {
                try {
                    return ImageIO.read(file.toFile());
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private static String detectLoader(String id, Path json) {
        String lower = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
        String text = "";
        if (json != null && Files.isRegularFile(json)) {
            try {
                text = Files.readString(json).toLowerCase(java.util.Locale.ROOT);
            } catch (IOException ignored) {
            }
        }
        String combined = text + " " + lower;
        if (combined.contains("neoforge")) {
            return "neoforge";
        }
        if (combined.contains("quilt")) {
            return "quilt";
        }
        if (combined.contains("fabric")) {
            return "fabric";
        }
        if (combined.contains("forge")) {
            return "forge";
        }
        return "vanilla";
    }

    private static final java.util.Map<String, BufferedImage> LOADER_ICONS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static BufferedImage loaderImage(String key) {
        return LOADER_ICONS.computeIfAbsent(key, name -> {
            try (var in = InstanceIcon.class.getResourceAsStream("/mclite/assets/" + name + ".png")) {
                return in == null ? null : ImageIO.read(in);
            } catch (IOException ex) {
                return null;
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int s = Math.min(size, getWidth());
        g2.setClip(new RoundRectangle2D.Float(0, 0, s, s, 12, 12));
        BufferedImage shown = image != null ? image : loaderImage(loader);
        if (shown == null && !"vanilla".equals(loader)) {
            shown = loaderImage("vanilla");
        }
        if (shown != null) {
            double scale = Math.max(s / (double) shown.getWidth(),
                    s / (double) shown.getHeight());
            int w = (int) Math.ceil(shown.getWidth() * scale);
            int h = (int) Math.ceil(shown.getHeight() * scale);
            g2.drawImage(shown, (s - w) / 2, (s - h) / 2, w, h, null);
        }
        g2.setClip(null);
        g2.dispose();
    }

}
