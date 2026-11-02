package mclite;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/** Shared Swing layout primitives used by the launcher pages. */
public final class LauncherLayout {
    private LauncherLayout() {
    }

    public static JPanel pageHeader(String title, String subtitle, JComponent right) {
        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setBackground(Theme.CARD);
        top.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(9, 14, 9, 14)));
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel titleLabel = Ui.label(title, Theme.H2, Theme.TEXT);
        JLabel subtitleLabel = Ui.label(subtitle, Theme.MONO, Theme.DIM);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(titleLabel);
        info.add(Ui.vgap(1));
        info.add(subtitleLabel);
        top.add(info, BorderLayout.CENTER);
        if (right != null) {
            top.add(right, BorderLayout.EAST);
        }
        return top;
    }

    public static JPanel section(String title, String subtitle) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel heading = Ui.label(title, Theme.H1, Theme.TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel sub = Ui.label(subtitle, Theme.TINY, Theme.DIM);
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(Ui.vgap(3));
            panel.add(sub);
        }
        panel.add(Ui.vgap(10));
        return panel;
    }

    public static JComponent lockedHeight(JComponent component) {
        int height = Math.max(1, component.getPreferredSize().height);
        component.setMinimumSize(new Dimension(0, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return component;
    }

    public static JLabel sidebarNote(String text) {
        JLabel label = new JLabel("<html><body style='width:600px'>"
                + Formatting.html(text) + "</body></html>");
        label.setFont(Theme.TINY);
        label.setForeground(Theme.DIM);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public static JPanel divider() {
        JPanel line = new JPanel();
        line.setBackground(Theme.BORDER);
        line.setPreferredSize(new Dimension(0, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    public static JComponent infoRow(String text) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        JLabel dot = Ui.label("•", Theme.TINY, Theme.ACCENT);
        row.add(dot);
        row.add(Ui.hgap(7));
        row.add(Ui.label(text, Theme.TINY, Theme.DIM));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return row;
    }
}
