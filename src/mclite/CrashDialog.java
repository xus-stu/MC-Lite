package mclite;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.nio.file.Path;

public final class CrashDialog extends JDialog {
    public CrashDialog(Component parent, CrashAnalyzer.Result result, int exitCode) {
        super(parent == null ? null : (Frame) SwingUtilities.getWindowAncestor(parent),
                "游戏崩溃", true);
        setUndecorated(true);
        setBackground(Theme.CARD);
        JPanel root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 18);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = Ui.label("游戏崩溃 · 退出代码 " + exitCode, Theme.H2, Theme.TEXT);
        head.add(title, BorderLayout.WEST);
        Ui.AccentButton close = Ui.smallButton("×", Ui.AccentButton.Style.GHOST);
        close.addActionListener(e -> dispose());
        head.add(close, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        JLabel summary = Ui.label(result.summary(), Theme.MONO, Theme.WARNING);
        body.add(summary, BorderLayout.NORTH);

        DefaultListModel<String> suspects = new DefaultListModel<>();
        for (String mod : result.suspectMods()) {
            suspects.addElement(mod);
        }
        JList<String> suspectList = new JList<>(suspects);
        suspectList.setBackground(Theme.BG);
        suspectList.setForeground(Theme.MUTED);
        suspectList.setFont(Theme.MONO);
        suspectList.setFixedCellHeight(24);
        suspectList.setBorder(new EmptyBorder(3, 8, 3, 8));
        JScrollPane suspectScroll = Ui.scrollDark(suspectList);
        suspectScroll.setPreferredSize(new Dimension(700, 90));
        JPanel suspectBox = new Ui.RoundedPanel(Theme.BG, Theme.BORDER, 12);
        suspectBox.setLayout(new BorderLayout(0, 4));
        suspectBox.setBorder(new EmptyBorder(8, 10, 8, 10));
        suspectBox.add(Ui.label("可能相关的模组 · " + suspects.size(), Theme.TINY, Theme.DIM),
                BorderLayout.NORTH);
        if (suspects.isEmpty()) {
            suspectBox.add(Ui.label("没有从日志中匹配到具体模组", Theme.TINY, Theme.MUTED),
                    BorderLayout.CENTER);
        } else {
            suspectBox.add(suspectScroll, BorderLayout.CENTER);
        }
        body.add(suspectBox, BorderLayout.CENTER);

        JTextArea log = new JTextArea(result.tail());
        log.setEditable(false);
        log.setFocusable(false);
        log.setFont(Theme.MONO);
        log.setBackground(Theme.BG);
        log.setForeground(Theme.DIM);
        log.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane logScroll = Ui.scrollDark(log);
        logScroll.setPreferredSize(new Dimension(700, 220));
        JPanel logBox = new Ui.RoundedPanel(Theme.BG, Theme.BORDER, 12);
        logBox.setLayout(new BorderLayout());
        logBox.setBorder(new EmptyBorder(4, 4, 4, 4));
        logBox.add(logScroll, BorderLayout.CENTER);
        body.add(logBox, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(Ui.label("日志：" + result.source(), Theme.MONO, Theme.DIM),
                BorderLayout.CENTER);
        JPanel actionButtons = new JPanel();
        actionButtons.setOpaque(false);
        actionButtons.setLayout(new javax.swing.BoxLayout(actionButtons,
                javax.swing.BoxLayout.X_AXIS));
        Ui.AccentButton open = Ui.smallButton("打开日志", Ui.AccentButton.Style.GHOST);
        open.addActionListener(e -> {
            try {
                if (result.source() != null && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(result.source().toFile());
                }
            } catch (Exception ignored) {
            }
        });
        Ui.AccentButton closeBottom = Ui.smallButton("关闭", Ui.AccentButton.Style.SECONDARY);
        closeBottom.addActionListener(e -> dispose());
        actionButtons.add(open);
        actionButtons.add(Ui.hgap(8));
        actionButtons.add(closeBottom);
        actions.add(actionButtons, BorderLayout.EAST);
        root.add(actions, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        centerOnScreen(this, parent);
    }
    private static void centerOnScreen(javax.swing.JDialog dialog, Component parent) {
        java.awt.GraphicsConfiguration configuration = parent == null
                ? java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration()
                : parent.getGraphicsConfiguration();
        java.awt.Rectangle screen = configuration.getBounds();
        dialog.setLocation(screen.x + Math.max(0, (screen.width - dialog.getWidth()) / 2),
                screen.y + Math.max(0, (screen.height - dialog.getHeight()) / 2));
    }

}
