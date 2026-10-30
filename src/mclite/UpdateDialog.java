package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;

public final class UpdateDialog extends JDialog {
    private boolean confirmed;

    public UpdateDialog(Component parent, String current, String latest) {
        this(parent, current, latest, "");
    }

    public UpdateDialog(Component parent, String current, String latest, String notes) {
        super(parent == null ? null : (Frame) javax.swing.SwingUtilities.getWindowAncestor(parent),
                "启动器更新", true);
        setUndecorated(true);
        setBackground(Theme.CARD);
        JPanel root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 18);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(20, 22, 16, 22));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label("发现新版本", Theme.H2, Theme.TEXT), BorderLayout.WEST);
        Ui.AccentButton close = Ui.smallButton("×", Ui.AccentButton.Style.GHOST);
        close.addActionListener(e -> dispose());
        head.add(close, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        JLabel version = Ui.label("V" + latest, Theme.TITLE, Theme.ACCENT);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel currentLabel = Ui.label("当前版本 V" + current, Theme.TINY, Theme.DIM);
        currentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel note = Ui.label("<html><body style='width:420px'>"
                + "启动器有新版本可用。下载会在后台进行，不会影响正在运行的游戏。"
                + "下载完成后，下次打开启动器时生效；更新会直接替换当前的 jar 或 macOS 应用包。"
                + "</body></html>", Theme.BODY, Theme.MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(version);
        body.add(Ui.vgap(4));
        body.add(currentLabel);
        body.add(Ui.vgap(12));
        body.add(note);
        body.add(Ui.vgap(10));
        JLabel changesLabel = Ui.label("更新内容", Theme.TINY, Theme.DIM);
        changesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(changesLabel);
        body.add(Ui.vgap(4));
        JTextArea changes = new JTextArea(notesText(notes));
        changes.setEditable(false);
        changes.setLineWrap(true);
        changes.setWrapStyleWord(true);
        changes.setFont(Theme.MONO);
        changes.setForeground(Theme.MUTED);
        changes.setBackground(Theme.CARD_ALT);
        changes.setBorder(new EmptyBorder(8, 10, 8, 10));
        changes.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(changes);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.setPreferredSize(new Dimension(500, 140));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(scroll);
        body.add(Ui.vgap(4));
        JLabel source = Ui.label("说明来自 GitHub Release，不包含在游戏包内", Theme.TINY, Theme.DIM);
        source.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(source);
        root.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
        actions.add(Box.createHorizontalGlue());
        Ui.AccentButton later = Ui.smallButton("稍后", Ui.AccentButton.Style.GHOST);
        later.addActionListener(e -> dispose());
        Ui.AccentButton download = Ui.smallButton("下载更新", Ui.AccentButton.Style.PRIMARY);
        download.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        actions.add(later);
        actions.add(Ui.hgap(8));
        actions.add(download);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(560, 360));
        setLocationRelativeTo(parent);
    }

    public static void showNotes(Component parent, String version, String notes) {
        JTextArea area = new JTextArea(notesText(notes));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.MONO);
        area.setForeground(Theme.MUTED);
        area.setBackground(Theme.CARD_ALT);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.setPreferredSize(new Dimension(560, 260));
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(scroll, BorderLayout.CENTER);
        content.add(Ui.label("说明来自 GitHub Release，不包含在游戏包内",
                Theme.TINY, Theme.DIM), BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(parent, content,
                "已下载 V" + version + " · 更新内容", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String notesText(String notes) {
        String text = notes == null ? "" : notes.strip();
        if (text.isEmpty()) {
            return "本次更新没有填写说明。";
        }
        if (text.length() > 6000) {
            text = text.substring(0, 6000) + "\n…";
        }
        return text;
    }

    public boolean confirmed() {
        return confirmed;
    }
}
