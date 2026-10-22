package mclite;

import javax.swing.BorderFactory;
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
import java.util.ArrayList;
import java.util.List;

public final class VersionsDialog extends JDialog {
    public interface Listener {
        void selected(VersionManifest.VersionEntry entry);
    }

    private final List<VersionManifest.VersionEntry> all;
    private DefaultListModel<VersionManifest.VersionEntry> model = new DefaultListModel<>();
    private final JList<VersionManifest.VersionEntry> list = new JList<>(model);
    private final Ui.PlaceholderField search = Ui.field("搜索版本或实例…");
    private final Listener listener;

    public VersionsDialog(Frame owner, String title, List<VersionManifest.VersionEntry> entries,
                          Listener listener) {
        super(owner, title, true);
        this.all = new ArrayList<>(entries);
        this.listener = listener;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(540, 560);
        setMinimumSize(new Dimension(460, 400));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.CARD);

        JPanel root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 16);
        root.setLayout(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(18, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        JLabel hint = Ui.label(title, Theme.H1, Theme.TEXT);
        top.add(hint, BorderLayout.NORTH);
        search.setPreferredSize(new Dimension(320, 32));
        top.add(search, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        list.setBackground(Theme.BG);
        list.setSelectionBackground(Theme.BG);
        list.setFixedCellHeight(48);
        list.setCellRenderer(new EntryRenderer());
        list.setBorder(new EmptyBorder(4, 0, 4, 0));
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    choose();
                }
            }
        });
        root.add(Ui.scrollDark(list), BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
        Ui.AccentButton cancel = Ui.smallButton("取消", Ui.AccentButton.Style.GHOST);
        cancel.addActionListener(e -> dispose());
        Ui.AccentButton use = Ui.smallButton("使用此版本", Ui.AccentButton.Style.PRIMARY);
        use.addActionListener(e -> choose());
        actions.add(cancel);
        actions.add(javax.swing.Box.createHorizontalGlue());
        actions.add(use);
        root.add(actions, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }
        });
        setContentPane(root);
        refresh();
    }

    private void refresh() {
        String query = search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        VersionManifest.VersionEntry previous = list.getSelectedValue();
        DefaultListModel<VersionManifest.VersionEntry> nextModel = new DefaultListModel<>();
        for (VersionManifest.VersionEntry entry : all) {
            if (query.isEmpty() || entry.id().toLowerCase(java.util.Locale.ROOT).contains(query)
                    || entry.type().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                nextModel.addElement(entry);
            }
        }
        model = nextModel;
        list.setModel(nextModel);
        if (previous != null) {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).id().equals(previous.id())) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (list.getSelectedIndex() < 0 && !model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void choose() {
        VersionManifest.VersionEntry entry = list.getSelectedValue();
        if (entry != null) {
            listener.selected(entry);
            dispose();
        }
    }

    private final class EntryRenderer extends JPanel
            implements ListCellRenderer<VersionManifest.VersionEntry> {
        private final JLabel title = new JLabel();
        private final JLabel meta = new JLabel();
        private final JLabel local = new JLabel("本地实例");

        private EntryRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(8, 2));
            setBorder(new EmptyBorder(5, 10, 5, 10));
            title.setFont(Theme.BODY_BOLD);
            title.setForeground(Theme.TEXT);
            meta.setFont(Theme.MONO);
            meta.setForeground(Theme.DIM);
            local.setFont(Theme.MONO);
            local.setForeground(Theme.ACCENT);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
            text.add(title);
            text.add(meta);
            add(text, BorderLayout.CENTER);
            add(local, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends VersionManifest.VersionEntry> list,
                VersionManifest.VersionEntry entry, int index, boolean selected,
                boolean focus) {
            title.setText(entry.id());
            meta.setText(entry.displayType() + "  ·  " + Formatting.date(entry.releaseTime()));
            local.setVisible(entry.local());
            title.setForeground(selected ? Theme.ACCENT : Theme.TEXT);
            meta.setForeground(selected ? Theme.MUTED : Theme.DIM);
            local.setForeground(Theme.ACCENT);
            putClientProperty("selected", selected);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            if (Boolean.TRUE.equals(getClientProperty("selected"))) {
                g2.setColor(Theme.CARD_ALT);
                g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 12, 12);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(2, 9, 3, getHeight() - 18, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
