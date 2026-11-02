package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JComponent;
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
        JDialog dialog = createDialog(parent, title);
        dialog.setUndecorated(true);
        dialog.setBackground(new java.awt.Color(0, 0, 0, 0));
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
    public static String choice(Component parent, String title, String prompt,
                                String[] options, String initial) {
        if (options == null || options.length == 0) {
            return null;
        }
        javax.swing.JComboBox<String> combo = new Ui.DarkCombo<>();
        for (String option : options) {
            combo.addItem(option);
        }
        combo.setSelectedItem(initial == null ? options[0] : initial);
        return select(parent, title, prompt, combo, () -> (String) combo.getSelectedItem());
    }

    public static String input(Component parent, String title, String prompt, String initial) {
        Ui.PlaceholderField field = Ui.field(initial == null ? "" : initial, "");
        field.setText(initial == null ? "" : initial);
        field.setPreferredSize(new Dimension(420, 32));
        return select(parent, title, prompt, field, field::getText);
    }

    public static int option(Component parent, String title, String prompt,
                             String[] options, int initial) {
        if (options == null || options.length == 0) {
            return -1;
        }
        javax.swing.JComboBox<String> combo = new Ui.DarkCombo<>();
        for (String option : options) {
            combo.addItem(option);
        }
        combo.setSelectedIndex(Math.max(0, Math.min(options.length - 1, initial)));
        String selected = select(parent, title, prompt, combo, () -> (String) combo.getSelectedItem());
        if (selected == null) {
            return -1;
        }
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    private static String select(Component parent, String title, String prompt,
                                 JComponent field, java.util.function.Supplier<String> value) {
        JDialog dialog = createDialog(parent, title);
        dialog.setUndecorated(true);
        dialog.setBackground(new java.awt.Color(0, 0, 0, 0));
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

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JLabel label = Ui.label(prompt == null ? "" : prompt, Theme.BODY, Theme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(label);
        body.add(Box.createVerticalStrut(8));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        body.add(field);
        root.add(body, BorderLayout.CENTER);

        String[] result = {null};
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.add(Box.createHorizontalGlue());
        Ui.AccentButton cancel = Ui.smallButton("取消", Ui.AccentButton.Style.GHOST);
        cancel.addActionListener(e -> dialog.dispose());
        Ui.AccentButton ok = Ui.smallButton("确定", Ui.AccentButton.Style.PRIMARY);
        Runnable accept = () -> {
            result[0] = value.get();
            dialog.dispose();
        };
        ok.addActionListener(e -> accept.run());
        if (field instanceof javax.swing.JTextField text) {
            text.addActionListener(e -> accept.run());
        }
        actions.add(cancel);
        actions.add(Ui.hgap(8));
        actions.add(ok);
        root.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(420, dialog.getHeight()));
        centerOnScreen(dialog, parent);
        dialog.setVisible(true);
        return result[0];
    }

    private static JDialog createDialog(Component parent, String title) {
        java.awt.Window ancestor = parent == null ? null
                : SwingUtilities.getWindowAncestor(parent);
        if (ancestor instanceof Frame frame) {
            return new JDialog(frame, title, true);
        }
        if (ancestor instanceof java.awt.Dialog dialog) {
            return new JDialog(dialog, title, true);
        }
        return new JDialog((Frame) null, title, true);
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
