package mclite;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class LaunchProgress extends JDialog {
    private final JLabel stageLabel;
    private final Ui.ThinProgress progress = new Ui.ThinProgress();

    public LaunchProgress(Component parent, String version) {
        super(parent == null ? null : (Frame) SwingUtilities.getWindowAncestor(parent),
                "启动 " + version, false);
        setUndecorated(true);

        setBackground(Theme.CARD);
        JPanel root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 16);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(18, 22, 18, 22));
        JLabel title = Ui.label("正在启动 " + version, Theme.H2, Theme.TEXT);
        stageLabel = Ui.label("准备中…", Theme.MONO, Theme.DIM);
        JPanel text = new JPanel(new BorderLayout(0, 4));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(stageLabel, BorderLayout.CENTER);
        root.add(text, BorderLayout.NORTH);
        progress.setPreferredSize(new Dimension(360, 5));
        progress.setIndeterminate(true);
        root.add(progress, BorderLayout.CENTER);
        setContentPane(root);
        pack();
        centerOn(parent);
    }

    private void centerOn(Component parent) {
        java.awt.GraphicsConfiguration configuration = parent == null
                ? java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration()
                : parent.getGraphicsConfiguration();
        java.awt.Rectangle screen = configuration.getBounds();
        setLocation(screen.x + Math.max(0, (screen.width - getWidth()) / 2),
                screen.y + Math.max(0, (screen.height - getHeight()) / 2));
    }

    public void stage(String text) {
        stageLabel.setText(text);
        stageLabel.repaint();
        progress.repaint();

        java.awt.Toolkit.getDefaultToolkit().sync();
    }
}
