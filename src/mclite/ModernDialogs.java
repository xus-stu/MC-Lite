package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class ModernDialogs {
    private ModernDialogs() {
    }

    public static void message(Component parent, String title, String text) {
        show(parent, title, text, false);
    }

    public static boolean confirm(Component parent, String title, String text) {
        return show(parent, title, text, true);
    }

    private static boolean show(Component parent, String title, String text, boolean confirm) {
        Frame owner = parent == null ? null
                : (Frame) SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, true);
        dialog.setUndecorated(true);
        dialog.setBackground(Theme.CARD);
        JPanel root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 18);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label(title, Theme.H2, Theme.TEXT), BorderLayout.WEST);
        Ui.AccentButton close = Ui.smallButton("×", Ui.AccentButton.Style.GHOST);
        close.addActionListener(e -> dialog.dispose());
        head.add(close, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        JTextArea body = new JTextArea(text == null ? "" : text);
        body.setEditable(false);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setForeground(Theme.MUTED);
        body.setFont(Theme.BODY);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setBorder(new EmptyBorder(0, 0, 0, 0));
        JScrollPane scroll = Ui.scrollDark(body);
        scroll.setPreferredSize(new Dimension(500, 170));
        scroll.setBorder(null);
        root.add(scroll, BorderLayout.CENTER);

        final boolean[] answer = {false};
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.add(Box.createHorizontalGlue());
        if (confirm) {
            Ui.AccentButton no = Ui.smallButton("取消", Ui.AccentButton.Style.GHOST);
            no.addActionListener(e -> dialog.dispose());
            Ui.AccentButton yes = Ui.smallButton("继续", Ui.AccentButton.Style.PRIMARY);
            yes.addActionListener(e -> {
                answer[0] = true;
                dialog.dispose();
            });
            actions.add(no);
            actions.add(Ui.hgap(8));
            actions.add(yes);
        } else {
            Ui.AccentButton ok = Ui.smallButton("确定", Ui.AccentButton.Style.PRIMARY);
            ok.addActionListener(e -> dialog.dispose());
            actions.add(ok);
        }
        root.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        centerOnScreen(dialog, parent);
        dialog.setVisible(true);
        return answer[0];
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
