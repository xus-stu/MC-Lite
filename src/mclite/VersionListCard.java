package mclite;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;

/** Versions card of the download center: the filtered version list and its empty state. */
final class VersionListCard {
    private final Runnable onSelectionChanged;
    private final Runnable onOpenDetail;
    private DefaultListModel<VersionManifest.VersionEntry> model;
    private JList<VersionManifest.VersionEntry> list;
    private JPanel cards;
    private CardLayout layout;
    private boolean refreshing;

    VersionListCard(Runnable onSelectionChanged, Runnable onOpenDetail) {
        this.onSelectionChanged = onSelectionChanged;
        this.onOpenDetail = onOpenDetail;
    }

    JPanel build(ListCellRenderer<VersionManifest.VersionEntry> renderer) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(true);
        wrap.setBackground(Theme.BG);

        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setFixedCellHeight(52);
        list.setBackground(Theme.BG);
        list.setSelectionBackground(Theme.BG);
        list.setSelectionForeground(Theme.TEXT);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(renderer);
        list.setBorder(new EmptyBorder(4, 8, 24, 8));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing) {
                onSelectionChanged.run();
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onOpenDetail.run();
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                list.putClientProperty("hoverIndex", -1);
                list.repaint();
            }
        });
        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                list.putClientProperty("hoverIndex", list.locationToIndex(e.getPoint()));
                list.repaint();
            }
        });

        JScrollPane scroll = Ui.scrollDark(list);
        layout = new CardLayout();
        cards = new JPanel(layout);
        cards.setOpaque(false);
        cards.add(scroll, "list");
        JPanel empty = new JPanel(new BorderLayout());
        empty.setOpaque(false);
        JLabel emptyLabel = Ui.label("没有匹配的版本", Theme.BODY, Theme.DIM);
        emptyLabel.setHorizontalAlignment(JLabel.CENTER);
        empty.add(emptyLabel, BorderLayout.CENTER);
        cards.add(empty, "empty");
        wrap.add(cards, BorderLayout.CENTER);
        return wrap;
    }

    void setEntries(List<VersionManifest.VersionEntry> entries) {
        if (model == null) {
            return;
        }
        refreshing = true;
        try {
            model.clear();
            if (entries != null) {
                for (VersionManifest.VersionEntry entry : entries) {
                    model.addElement(entry);
                }
            }
        } finally {
            refreshing = false;
        }
        if (layout != null) {
            layout.show(cards, entries == null || entries.isEmpty() ? "empty" : "list");
        }
    }

    /** Runs the given selection changes without firing the frame callback (used while filtering). */
    void setRefreshing(boolean value) {
        this.refreshing = value;
    }

    boolean selectId(String id) {
        if (list == null || id == null) {
            return false;
        }
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(id)) {
                list.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    void selectIndex(int index) {
        if (list != null && index >= 0 && index < model.size()) {
            list.setSelectedIndex(index);
        }
    }

    void selectFirst() {
        selectIndex(0);
    }

    int selectedIndex() {
        return list == null ? -1 : list.getSelectedIndex();
    }

    VersionManifest.VersionEntry selectedValue() {
        return list == null ? null : list.getSelectedValue();
    }

    int size() {
        return model == null ? 0 : model.size();
    }

    VersionManifest.VersionEntry entryAt(int index) {
        return model == null || index < 0 || index >= model.size() ? null : model.get(index);
    }

    boolean isEmpty() {
        return size() == 0;
    }

    void reveal(int index) {
        if (list != null && index >= 0 && index < model.size()) {
            list.ensureIndexIsVisible(index);
        }
    }

    /** Scrolls to the row with a little extra bottom margin (kept from the old frame code). */
    void revealWithMargin(int index) {
        if (list == null || index < 0 || index >= model.size()) {
            return;
        }
        java.awt.Rectangle bounds = list.getCellBounds(index, index);
        if (bounds == null) {
            return;
        }
        bounds.y = Math.max(0, bounds.y - 2);
        bounds.height += 20;
        list.scrollRectToVisible(bounds);
    }

    void setEnabled(boolean enabled) {
        if (list != null) {
            list.setEnabled(enabled);
        }
    }

    void repaintList() {
        if (list != null) {
            list.repaint();
        }
    }
}
