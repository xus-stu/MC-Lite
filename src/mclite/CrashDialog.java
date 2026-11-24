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

    /** 可疑模组列表：选中/悬停用圆角高亮，和启动器其它列表一致。 */
    private static final class SuspectRenderer extends JPanel
            implements javax.swing.ListCellRenderer<String> {
        private final JLabel label = new JLabel();

        private SuspectRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout());
            label.setFont(Theme.MONO);
            label.setForeground(Theme.MUTED);
            label.setBorder(new EmptyBorder(0, 8, 0, 8));
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list,
                                                      String value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            label.setText(value);
            label.setForeground(isSelected ? Theme.TEXT : Theme.MUTED);
            putClientProperty("selected", isSelected);
            return this;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = Ui.prepare(g);
            int width = getWidth();
            int height = getHeight();
            if (Boolean.TRUE.equals(getClientProperty("selected"))) {
                g2.setColor(Theme.SEL);
                g2.fillRoundRect(2, 2, width - 4, height - 4, 9, 9);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(2, 6, 3, height - 12, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

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
        suspectList.setCellRenderer(new SuspectRenderer());
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
        suspectBox.add(Ui.label(suspects.isEmpty() ? "可疑模组 · 没能定位到具体模组"
                        : "可疑模组（按可能性排序）· " + suspects.size(), Theme.TINY, Theme.DIM),
                BorderLayout.NORTH);
        if (suspects.isEmpty()) {
            suspectBox.add(Ui.label("日志里没有模组栈帧线索，按下面的方案排查", Theme.TINY, Theme.MUTED),
                    BorderLayout.CENTER);
        } else {
            suspectBox.add(suspectScroll, BorderLayout.CENTER);
        }
        JPanel middle = new JPanel();
        middle.setOpaque(false);
        middle.setLayout(new javax.swing.BoxLayout(middle, javax.swing.BoxLayout.Y_AXIS));
        middle.add(suspectBox);
        middle.add(Ui.vgap(8));
        body.add(middle, BorderLayout.CENTER);

        StringBuilder advice = new StringBuilder();
        for (String line : result.advice()) {
            advice.append("• ").append(line).append("\n");
        }
        JTextArea adviceArea = new JTextArea(advice.toString().trim());
        adviceArea.setEditable(false);
        adviceArea.setFocusable(false);
        adviceArea.setLineWrap(true);
        adviceArea.setWrapStyleWord(true);
        adviceArea.setFont(Theme.SMALL);
        adviceArea.setBackground(Theme.BG);
        adviceArea.setForeground(Theme.TEXT);
        adviceArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane adviceScroll = Ui.scrollDark(adviceArea);
        adviceScroll.setPreferredSize(new Dimension(700, 96));
        JPanel adviceBox = new Ui.RoundedPanel(Theme.BG, Theme.BORDER, 12);
        adviceBox.setLayout(new BorderLayout(0, 4));
        adviceBox.setBorder(new EmptyBorder(8, 10, 8, 10));
        adviceBox.add(Ui.label("解决方案", Theme.TINY, Theme.ACCENT), BorderLayout.NORTH);
        adviceBox.add(adviceScroll, BorderLayout.CENTER);
        suspectBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        adviceBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 124));
        middle.add(adviceBox);

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
