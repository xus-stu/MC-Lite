package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;

public final class UpdateDialog extends JDialog {
    private boolean confirmed;

    public UpdateDialog(Component parent, String current, String latest) {
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
        setMinimumSize(new Dimension(520, 260));
        setLocationRelativeTo(parent);
    }

    public boolean confirmed() {
        return confirmed;
    }
}
