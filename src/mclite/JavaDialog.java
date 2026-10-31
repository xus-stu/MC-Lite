package mclite;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JavaDialog extends JDialog {
    public interface Listener {
        void changed(List<JavaManager.JavaInfo> runtimes, JavaManager.JavaInfo selected);
    }

    private final Config config;
    private final List<JavaManager.JavaInfo> runtimes;
    private final Listener listener;
    private final DefaultListModel<JavaManager.JavaInfo> model = new DefaultListModel<>();
    private final JList<JavaManager.JavaInfo> list = new JList<>(model);

    public JavaDialog(Frame owner, Config config, List<JavaManager.JavaInfo> initial,
                      Listener listener) {
        super(owner, "Java 运行时列表", true);
        this.config = config;
        this.runtimes = new ArrayList<>(initial);
        this.listener = listener;
        setSize(700, 500);
        setMinimumSize(new Dimension(560, 360));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        JPanel root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 16);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label("Java 运行时列表", Theme.H2, Theme.TEXT), BorderLayout.WEST);
        Ui.AccentButton close = Ui.smallButton("×", Ui.AccentButton.Style.GHOST);
        close.addActionListener(e -> dispose());
        head.add(close, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        list.setBackground(Theme.BG);
        list.setSelectionBackground(Theme.BG);
        list.setFixedCellHeight(56);
        list.setCellRenderer(new RuntimeRenderer());
        list.setBorder(new EmptyBorder(4, 0, 4, 0));
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    useSelected();
                }
            }
        });
        root.add(Ui.scrollDark(list), BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
        Ui.AccentButton add = Ui.smallButton("添加 Java…", Ui.AccentButton.Style.SECONDARY);
        add.addActionListener(e -> addRuntime());
        Ui.AccentButton remove = Ui.smallButton("移除手动添加", Ui.AccentButton.Style.GHOST);
        remove.addActionListener(e -> removeSelected());
        Ui.AccentButton use = Ui.smallButton("使用此 Java", Ui.AccentButton.Style.PRIMARY);
        use.addActionListener(e -> useSelected());
        actions.add(add);
        actions.add(Ui.hgap(8));
        actions.add(remove);
        actions.add(javax.swing.Box.createHorizontalGlue());
        actions.add(use);
        root.add(actions, BorderLayout.SOUTH);
        setContentPane(root);
        refresh();
    }

    private void refresh() {
        JavaManager.JavaInfo selected = list.getSelectedValue();
        model.clear();
        for (JavaManager.JavaInfo info : runtimes) {
            model.addElement(info);
        }
        if (selected != null) {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).path().equals(selected.path())) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (list.getSelectedIndex() < 0 && !model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void addRuntime() {
        Path chosen = Ui.chooseFile(this, "选择 java 可执行文件", null);
        if (chosen == null) {
            return;
        }
        JavaManager.Probe probe = JavaManager.probe(chosen);
        if (probe.major() <= 0) {
            ModernDialogs.message(this, "无效的 Java", "无法运行这个文件，或它不是 Java 可执行文件。");
            return;
        }
        JavaManager.JavaInfo info = new JavaManager.JavaInfo(chosen, probe.major(),
                "Java " + probe.major() + " · " + probe.vendor() + " · 手动添加", null, false);
        runtimes.removeIf(existing -> same(existing.path(), chosen));
        runtimes.add(0, info);
        config.addJavaPath(chosen);
        config.save();
        refresh();
        listener.changed(new ArrayList<>(runtimes), info);
    }

    private void removeSelected() {
        JavaManager.JavaInfo selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (config.javaPaths().contains(selected.path().toString())) {
            config.removeJavaPath(selected.path().toString());
            config.save();
            runtimes.remove(selected);
            refresh();
            listener.changed(new ArrayList<>(runtimes), null);
        } else {
            ModernDialogs.message(this, "自动检测的 Java",
                    "这个 Java 是自动检测到的，不能从列表永久移除。\n"
                            + "可以手动添加其他 Java，或在启动器配置中选择使用哪一个。");
        }
    }

    private void useSelected() {
        JavaManager.JavaInfo selected = list.getSelectedValue();
        if (selected != null) {
            listener.changed(new ArrayList<>(runtimes), selected);
            dispose();
        }
    }

    private static boolean same(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    private final class RuntimeRenderer extends JPanel
            implements ListCellRenderer<JavaManager.JavaInfo> {
        private final JLabel title = new JLabel();
        private final JLabel path = new JLabel();

        private RuntimeRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(0, 3));
            setBorder(new EmptyBorder(6, 10, 6, 10));
            title.setFont(Theme.BODY_BOLD);
            title.setForeground(Theme.TEXT);
            path.setFont(Theme.MONO);
            path.setForeground(Theme.DIM);
            add(title, BorderLayout.NORTH);
            add(path, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends JavaManager.JavaInfo> list,
                JavaManager.JavaInfo value, int index, boolean selected, boolean focus) {
            title.setText(value.label());
            path.setText(value.path().toString());
            putClientProperty("selected", selected);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            g2.setColor(Boolean.TRUE.equals(getClientProperty("selected"))
                    ? Theme.CARD_ALT : Theme.CARD);
            g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
            if (Boolean.TRUE.equals(getClientProperty("selected"))) {
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(2, 9, 3, getHeight() - 18, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
