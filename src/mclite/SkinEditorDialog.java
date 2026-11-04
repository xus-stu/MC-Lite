package mclite;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/** Simple 64x64 skin painter: brush, eraser, fill, eyedropper, undo and PNG import. */
final class SkinEditorDialog extends JDialog {
    private static final int SIZE = 64;
    private static final int ZOOM = 8;

    private final Config config;
    private final String accountId;
    private final Runnable onSaved;
    private final BufferedImage canvas;
    private final Deque<BufferedImage> history = new ArrayDeque<>();
    private final CanvasPanel canvasPanel;
    private String tool = "brush";
    private Color color = Color.WHITE;
    private boolean strokeActive;

    SkinEditorDialog(Window owner, Config config, String accountId, Runnable onSaved) {
        super(owner, "自绘皮肤 · " + accountId, ModalityType.APPLICATION_MODAL);
        this.config = config;
        this.accountId = accountId;
        this.onSaved = onSaved;
        this.canvas = loadExisting();
        this.canvasPanel = new CanvasPanel();

        JPanel root = new JPanel(new BorderLayout(12, 0));
        root.setBackground(Theme.CARD);
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        setContentPane(root);

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(canvasPanel, BorderLayout.CENTER);
        root.add(left, BorderLayout.CENTER);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));
        tools.setPreferredSize(new Dimension(250, 0));
        JLabel hint = new JLabel("<html><body style='width:220px'>左键画，右键吸色。"
                + "皮肤为 64×64，保存后立即应用到当前账户。</body></html>");
        hint.setFont(Theme.TINY);
        hint.setForeground(Theme.DIM);
        tools.add(hint);
        tools.add(Box.createVerticalStrut(10));

        JPanel toolRow = new JPanel(new java.awt.GridLayout(2, 2, 6, 6));
        toolRow.setOpaque(false);
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (String[] item : new String[][]{{"画笔", "brush"}, {"橡皮", "eraser"},
                {"填充", "fill"}, {"吸管", "pick"}}) {
            JToggleButton button = new JToggleButton(item[0]);
            button.setSelected("brush".equals(item[1]));
            button.addActionListener(e -> tool = item[1]);
            group.add(button);
            toolRow.add(button);
        }
        tools.add(toolRow);
        tools.add(Box.createVerticalStrut(10));

        JButton colorButton = new JButton("选择颜色");
        colorButton.addActionListener(e -> {
            Color picked = JColorChooser.showDialog(this, "选择颜色", color);
            if (picked != null) {
                color = picked;
                colorButton.setBackground(picked);
            }
        });
        colorButton.setBackground(color);
        tools.add(colorButton);
        tools.add(Box.createVerticalStrut(8));

        JButton undo = new JButton("撤销");
        undo.addActionListener(e -> undo());
        tools.add(undo);
        tools.add(Box.createVerticalStrut(8));

        JButton importButton = new JButton("导入 PNG…");
        importButton.addActionListener(e -> importPng());
        tools.add(importButton);
        tools.add(Box.createVerticalGlue());

        JPanel actions = new JPanel(new java.awt.GridLayout(2, 1, 6, 6));
        actions.setOpaque(false);
        Ui.AccentButton save = Ui.button("保存到账户", Ui.AccentButton.Style.PRIMARY);
        save.addActionListener(e -> save());
        Ui.AccentButton cancel = Ui.smallButton("取消", Ui.AccentButton.Style.GHOST);
        cancel.addActionListener(e -> dispose());
        actions.add(save);
        actions.add(cancel);
        tools.add(actions);
        root.add(tools, BorderLayout.EAST);

        setSize(860, 620);
        setLocationRelativeTo(owner);
    }

    private BufferedImage loadExisting() {
        BufferedImage existing = null;
        java.awt.Image image = SkinStore.load(config, accountId);
        if (image instanceof BufferedImage buffered) {
            existing = buffered;
        }
        BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (existing != null && existing.getHeight() == 32) {
            g.drawImage(existing, 0, 0, null);
        } else if (existing != null) {
            g.drawImage(existing, 0, 0, SIZE, SIZE, null);
        } else {
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, SIZE, SIZE);
        }
        g.dispose();
        return result;
    }

    private void pushHistory() {
        BufferedImage copy = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(canvas, 0, 0, null);
        g.dispose();
        history.push(copy);
        while (history.size() > 20) {
            history.removeLast();
        }
    }

    private void undo() {
        if (history.isEmpty()) {
            return;
        }
        BufferedImage previous = history.pop();
        Graphics2D g = canvas.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(previous, 0, 0, null);
        g.dispose();
        canvasPanel.repaint();
    }

    private void importPng() {
        Path source = Ui.chooseFile(this, "导入皮肤 PNG",
                Path.of(System.getProperty("user.home")), "png");
        if (source == null || !Files.isRegularFile(source)) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) {
                return;
            }
            pushHistory();
            Graphics2D g = canvas.createGraphics();
            g.setComposite(java.awt.AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, SIZE, SIZE, null);
            g.dispose();
            canvasPanel.repaint();
        } catch (Exception ex) {
            ModernDialogs.message(this, "导入失败", ex.getMessage() == null ? "无法读取图片" : ex.getMessage());
        }
    }

    private void save() {
        try {
            SkinStore.saveImage(config, accountId, canvas);
            if (onSaved != null) {
                onSaved.run();
            }
            dispose();
        } catch (Exception ex) {
            ModernDialogs.message(this, "保存失败", ex.getMessage() == null ? "无法写入皮肤" : ex.getMessage());
        }
    }

    private void paintPixel(int x, int y) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
            return;
        }
        if ("eraser".equals(tool)) {
            canvas.setRGB(x, y, 0);
        } else {
            canvas.setRGB(x, y, color.getRGB());
        }
        canvasPanel.repaint();
    }

    private void floodFill(int x, int y) {
        int target = canvas.getRGB(x, y);
        int replacement = color.getRGB();
        if (target == replacement) {
            return;
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{x, y});
        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            int px = point[0];
            int py = point[1];
            if (px < 0 || py < 0 || px >= SIZE || py >= SIZE || canvas.getRGB(px, py) != target) {
                continue;
            }
            canvas.setRGB(px, py, replacement);
            queue.add(new int[]{px + 1, py});
            queue.add(new int[]{px - 1, py});
            queue.add(new int[]{px, py + 1});
            queue.add(new int[]{px, py - 1});
        }
        canvasPanel.repaint();
    }

    private final class CanvasPanel extends JPanel {
        CanvasPanel() {
            setPreferredSize(new Dimension(SIZE * ZOOM, SIZE * ZOOM));
            setBackground(Theme.BG);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int x = e.getX() / ZOOM;
                    int y = e.getY() / ZOOM;
                    if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
                        return;
                    }
                    if (e.getButton() == MouseEvent.BUTTON3 || "pick".equals(tool)) {
                        color = new Color(canvas.getRGB(x, y), true);
                        return;
                    }
                    pushHistory();
                    strokeActive = true;
                    if ("fill".equals(tool)) {
                        floodFill(x, y);
                    } else {
                        paintPixel(x, y);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (strokeActive && !"fill".equals(tool)) {
                        paintPixel(e.getX() / ZOOM, e.getY() / ZOOM);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    strokeActive = false;
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(canvas, 0, 0, SIZE * ZOOM, SIZE * ZOOM, null);
            g.setColor(new Color(0, 0, 0, 40));
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= SIZE; i += 8) {
                g.drawLine(i * ZOOM, 0, i * ZOOM, SIZE * ZOOM);
                g.drawLine(0, i * ZOOM, SIZE * ZOOM, i * ZOOM);
            }
            g.dispose();
        }
    }
}
