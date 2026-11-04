package mclite;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Version/instance browser page, extracted from LauncherFrame. */
final class InstancesPage {
    private final LauncherFrame owner;
    private final Runnable onCopy;
    private final Runnable onExport;
    private final Runnable onImport;
    private final Runnable onMeta;
    private final java.util.function.Supplier<Path> gameDir;
    private final Map<String, int[]> counts = new HashMap<>();
    private JList<VersionManifest.VersionEntry> list;
    private DefaultListModel<VersionManifest.VersionEntry> model;
    private JLabel detailTitle;
    private JLabel detailMeta;
    private JLabel modsHeader;
    private JLabel packsHeader;
    private DefaultListModel<String> modsModel;
    private DefaultListModel<String> packsModel;
    private Ui.DarkCombo<String> groupFilter;
    private boolean updatingGroupFilter;

    InstancesPage(LauncherFrame owner, Runnable onCopy, Runnable onExport, Runnable onImport,
                  Runnable onMeta, java.util.function.Supplier<Path> gameDir) {
        this.owner = owner;
        this.onCopy = onCopy;
        this.onExport = onExport;
        this.onImport = onImport;
        this.onMeta = onMeta;
        this.gameDir = gameDir;
    }

    JPanel build() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        Ui.AccentButton refresh = Ui.smallButton("刷新", Ui.AccentButton.Style.SECONDARY);
        refresh.addActionListener(e -> {
            owner.refreshAllVersions();
            owner.refreshInstalledCache();
            refresh();
        });
        Ui.AccentButton launch = Ui.smallButton("启动所选实例", Ui.AccentButton.Style.PRIMARY);
        launch.addActionListener(e -> {
            VersionManifest.VersionEntry entry = selected();
            if (entry != null) {
                owner.useVersion(entry, true);
            }
        });
        Ui.AccentButton delete = Ui.smallButton("删除实例", Ui.AccentButton.Style.DANGER);
        delete.setToolTipText("永久删除所选实例的版本文件夹");
        delete.addActionListener(e -> owner.deleteSelectedInstance());
        Ui.AccentButton update = Ui.smallButton("更新模组", Ui.AccentButton.Style.GHOST);
        update.setToolTipText("把推荐优化模组更新到当前版本");
        update.addActionListener(e -> owner.updateRecommendedMods());
        Ui.AccentButton rename = Ui.smallButton("重命名", Ui.AccentButton.Style.GHOST);
        rename.setToolTipText("修改实例名称和版本文件夹");
        rename.addActionListener(e -> owner.renameSelectedInstance());
        Ui.AccentButton zip = Ui.smallButton("ZIP 安装", Ui.AccentButton.Style.GHOST);
        zip.setToolTipText("从本地 .mrpack 或 CurseForge 整合包 ZIP 安装一个新实例");
        zip.addActionListener(e -> owner.importLocalModpack());
        Ui.AccentButton datapack = Ui.smallButton("数据包", Ui.AccentButton.Style.GHOST);
        datapack.setToolTipText("把数据包 ZIP 安装到所选实例的某个存档");
        datapack.addActionListener(e -> owner.installDatapack());
        Ui.AccentButton copy = Ui.smallButton("复制", Ui.AccentButton.Style.GHOST);
        copy.setToolTipText("复制所选实例（存档、模组、配置一起复制）");
        copy.addActionListener(e -> onCopy.run());
        Ui.AccentButton export = Ui.smallButton("导出 ZIP", Ui.AccentButton.Style.GHOST);
        export.setToolTipText("把所选实例打包成可迁移的 ZIP");
        export.addActionListener(e -> onExport.run());
        Ui.AccentButton importZip = Ui.smallButton("导入 ZIP", Ui.AccentButton.Style.GHOST);
        importZip.setToolTipText("从本启动器导出的实例 ZIP 导入");
        importZip.addActionListener(e -> onImport.run());
        Ui.AccentButton meta = Ui.smallButton("备注/分组", Ui.AccentButton.Style.GHOST);
        meta.setToolTipText("给所选实例设置分组和备注");
        meta.addActionListener(e -> onMeta.run());
        groupFilter = new Ui.DarkCombo<>();
        groupFilter.setPreferredSize(new Dimension(132, 30));
        groupFilter.setMaximumSize(new Dimension(132, 30));
        groupFilter.addActionListener(e -> {
            if (!updatingGroupFilter) {
                refresh();
            }
        });
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(Theme.BG);
        header.add(LauncherLayout.pageHeader("版本列表",
                "查看并启动游戏文件夹中的版本、模组和资源包", null), BorderLayout.NORTH);

        JPanel toolbarCard = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 12);
        toolbarCard.setLayout(new BoxLayout(toolbarCard, BoxLayout.Y_AXIS));
        toolbarCard.setBorder(new EmptyBorder(8, 12, 8, 12));
        JPanel row1 = new JPanel();
        row1.setOpaque(false);
        row1.setLayout(new BoxLayout(row1, BoxLayout.X_AXIS));
        row1.add(groupFilter);
        row1.add(Ui.hgap(8));
        row1.add(launch);
        row1.add(Ui.hgap(8));
        row1.add(update);
        row1.add(Ui.hgap(8));
        row1.add(rename);
        row1.add(Ui.hgap(8));
        row1.add(delete);
        row1.add(Ui.hgap(8));
        row1.add(zip);
        row1.add(Box.createHorizontalGlue());
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JPanel row2 = new JPanel();
        row2.setOpaque(false);
        row2.setLayout(new BoxLayout(row2, BoxLayout.X_AXIS));
        row2.add(datapack);
        row2.add(Ui.hgap(8));
        row2.add(copy);
        row2.add(Ui.hgap(8));
        row2.add(export);
        row2.add(Ui.hgap(8));
        row2.add(importZip);
        row2.add(Ui.hgap(8));
        row2.add(meta);
        row2.add(Ui.hgap(8));
        row2.add(refresh);
        row2.add(Box.createHorizontalGlue());
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        toolbarCard.add(row1);
        toolbarCard.add(Box.createVerticalStrut(6));
        toolbarCard.add(row2);

        JPanel toolbarWrap = new JPanel(new BorderLayout());
        toolbarWrap.setOpaque(true);
        toolbarWrap.setBackground(Theme.BG);
        toolbarWrap.setBorder(new EmptyBorder(8, 14, 0, 14));
        toolbarWrap.add(toolbarCard, BorderLayout.CENTER);
        header.add(toolbarWrap, BorderLayout.CENTER);
        page.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 16, 16, 16));
        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setBackground(Theme.BG);
        list.setSelectionBackground(Theme.BG);
        list.setFixedCellHeight(62);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new InstanceRenderer());
        list.setBorder(new EmptyBorder(2, 2, 2, 2));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetail(list.getSelectedValue());
            }
        });
        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                list.putClientProperty("hoverIndex", list.locationToIndex(e.getPoint()));
                list.repaint();
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                list.putClientProperty("hoverIndex", -1);
                list.repaint();
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(342, 0));
        left.setMinimumSize(new Dimension(300, 0));
        left.add(Ui.scrollDark(list), BorderLayout.CENTER);
        body.add(left, BorderLayout.WEST);

        JPanel detail = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER, 16);
        detail.setLayout(new BorderLayout(0, 10));
        detail.setBorder(new EmptyBorder(16, 18, 16, 18));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        detailTitle = Ui.label("选择一个版本", Theme.H2, Theme.TEXT);
        detailMeta = Ui.label("左侧列表只读查看", Theme.MONO, Theme.DIM);
        detailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailMeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(detailTitle);
        head.add(Ui.vgap(3));
        head.add(detailMeta);
        detail.add(head, BorderLayout.NORTH);

        JPanel lists = new JPanel(new java.awt.GridLayout(2, 1, 0, 10));
        lists.setOpaque(false);
        lists.add(buildFileSection(true));
        lists.add(buildFileSection(false));
        detail.add(lists, BorderLayout.CENTER);
        body.add(detail, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildFileSection(boolean mods) {
        JPanel box = new Ui.RoundedPanel(Theme.BG, Theme.BORDER, 12);
        box.setLayout(new BorderLayout(0, 4));
        box.setBorder(new EmptyBorder(8, 10, 8, 10));
        JLabel header = Ui.label(mods ? "模组 · 0" : "资源包 · 0", Theme.TINY, Theme.DIM);
        DefaultListModel<String> model = new DefaultListModel<>();
        if (mods) {
            modsHeader = header;
            modsModel = model;
        } else {
            packsHeader = header;
            packsModel = model;
        }
        JList<String> list = new JList<>(model);
        list.setBackground(Theme.BG);
        list.setForeground(Theme.MUTED);
        list.setFont(Theme.MONO);
        list.setFixedCellHeight(25);
        list.setSelectionBackground(Theme.SEL);
        list.setSelectionForeground(Theme.TEXT);
        list.setBorder(new EmptyBorder(2, 8, 2, 8));
        box.add(header, BorderLayout.NORTH);
        box.add(Ui.scrollDark(list), BorderLayout.CENTER);
        return box;
    }

    JList<VersionManifest.VersionEntry> list() {
        return list;
    }

    DefaultListModel<VersionManifest.VersionEntry> model() {
        return model;
    }

    VersionManifest.VersionEntry selected() {
        return list == null ? null : list.getSelectedValue();
    }

    boolean isEmpty() {
        return model == null || model.isEmpty();
    }

    VersionManifest.VersionEntry first() {
        return model == null || model.isEmpty() ? null : model.get(0);
    }

    void resetGroupFilter() {
        if (groupFilter == null) {
            return;
        }
        updatingGroupFilter = true;
        try {
            groupFilter.setSelectedItem("全部分组");
        } finally {
            updatingGroupFilter = false;
        }
    }

    void selectId(String id) {
        if (list == null || model == null || id == null) {
            return;
        }
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(id)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    void refresh() {
        if (model == null) {
            return;
        }
        counts.clear();
        List<VersionManifest.VersionEntry> entries = new ArrayList<>(owner.allVersions);
        if (entries.isEmpty()) {
            entries.addAll(LocalVersions.scan(owner.gamePaths));
        }
        Map<String, InstanceMeta.Entry> meta = InstanceMeta.load(gameDir.get());
        refreshGroupFilter(meta);
        String selectedGroup = groupFilter == null ? "全部分组" : (String) groupFilter.getSelectedItem();
        entries.removeIf(entry -> !entry.local() && !owner.installedCache.contains(entry.id()));
        if (selectedGroup != null && !"全部分组".equals(selectedGroup)) {
            entries.removeIf(entry -> {
                InstanceMeta.Entry item = meta.get(entry.id());
                return item == null || !selectedGroup.equals(item.group());
            });
        }
        entries.sort((a, b) -> Boolean.compare(b.local(), a.local()));
        VersionManifest.VersionEntry previous = list.getSelectedValue();
        DefaultListModel<VersionManifest.VersionEntry> next = new DefaultListModel<>();
        for (VersionManifest.VersionEntry entry : entries) {
            next.addElement(entry);
        }
        model = next;
        list.setModel(next);
        if (!next.isEmpty()) {
            int target = 0;
            if (previous != null) {
                for (int i = 0; i < next.size(); i++) {
                    if (next.get(i).id().equals(previous.id())) {
                        target = i;
                        break;
                    }
                }
            }
            list.setSelectedIndex(target);
            updateDetail(next.get(target));
        } else {
            detailTitle.setText("没有已安装的实例");
            detailMeta.setText("未安装的版本不会显示在这里");
            modsHeader.setText("模组 · 0");
            packsHeader.setText("资源包 · 0");
        }
    }

    private void refreshGroupFilter(Map<String, InstanceMeta.Entry> meta) {
        if (groupFilter == null) {
            return;
        }
        String previous = (String) groupFilter.getSelectedItem();
        java.util.TreeSet<String> groups = new java.util.TreeSet<>();
        for (InstanceMeta.Entry item : meta.values()) {
            if (item.group() != null && !item.group().isBlank()) {
                groups.add(item.group());
            }
        }
        java.util.List<String> wanted = new ArrayList<>();
        wanted.add("全部分组");
        wanted.addAll(groups);
        java.util.List<String> current = new ArrayList<>();
        for (int i = 0; i < groupFilter.getItemCount(); i++) {
            current.add(groupFilter.getItemAt(i));
        }
        if (wanted.equals(current)) {
            return;
        }
        updatingGroupFilter = true;
        try {
            groupFilter.removeAllItems();
            for (String group : wanted) {
                groupFilter.addItem(group);
            }
            groupFilter.setSelectedItem(previous != null && wanted.contains(previous) ? previous : "全部分组");
        } finally {
            updatingGroupFilter = false;
        }
    }

    private void updateDetail(VersionManifest.VersionEntry entry) {
        if (modsModel == null || packsModel == null) {
            return;
        }
        modsModel.clear();
        packsModel.clear();
        if (entry == null) {
            detailTitle.setText("选择一个版本");
            detailMeta.setText("左侧列表只读查看");
            modsHeader.setText("模组 · 0");
            packsHeader.setText("资源包 · 0");
            return;
        }
        detailTitle.setText(entry.id());
        boolean installed = Files.isRegularFile(owner.gamePaths.versionJson(entry.id()));
        InstanceMeta.Entry meta = InstanceMeta.load(gameDir.get()).get(entry.id());
        detailMeta.setText(entry.displayType() + "  ·  " + Formatting.date(entry.releaseTime())
                + (entry.local() ? "  ·  本地实例" : installed ? "  ·  已安装" : "  ·  未安装")
                + (meta == null || meta.group().isBlank() ? "" : "  ·  分组 " + meta.group()));
        detailMeta.setToolTipText(meta == null || meta.note().isBlank() ? null : meta.note());
        Path content = owner.config.isolateVersions()
                ? owner.config.effectiveGameDir(entry.id()) : owner.config.gameDir();
        List<Path> mods = listFiles(content.resolve("mods"));
        List<Path> packs = listFiles(content.resolve("resourcepacks"));
        for (Path file : mods) {
            modsModel.addElement(file.getFileName().toString());
        }
        for (Path file : packs) {
            packsModel.addElement(file.getFileName().toString());
        }
        modsHeader.setText("模组 · " + mods.size());
        packsHeader.setText("资源包 · " + packs.size());
    }

    private int[] count(VersionManifest.VersionEntry entry) {
        return counts.computeIfAbsent(entry.id(), key -> {
            Path content = owner.config.isolateVersions()
                    ? owner.config.effectiveGameDir(entry.id()) : owner.config.gameDir();
            return new int[]{listFiles(content.resolve("mods")).size(),
                    listFiles(content.resolve("resourcepacks")).size()};
        });
    }

    private static List<Path> listFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private final class InstanceRenderer extends JPanel
            implements ListCellRenderer<VersionManifest.VersionEntry> {
        private final JLabel id = new JLabel();
        private final JLabel meta = new JLabel();
        private final JLabel content = new JLabel();
        private final InstanceIcon icon = new InstanceIcon(34);
        private final JLabel local = new JLabel("本地");

        private InstanceRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(9, 0));
            setBorder(new EmptyBorder(7, 9, 7, 9));
            add(icon, BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            id.setFont(Theme.BODY_BOLD);
            id.setForeground(Theme.TEXT);
            meta.setFont(Theme.MONO);
            meta.setForeground(Theme.DIM);
            content.setFont(Theme.TINY);
            content.setForeground(Theme.MUTED);
            text.add(id);
            text.add(meta);
            text.add(content);
            add(text, BorderLayout.CENTER);
            local.setFont(Theme.MONO);
            local.setForeground(Theme.ACCENT);
            add(local, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends VersionManifest.VersionEntry> list,
                VersionManifest.VersionEntry entry, int index, boolean selected, boolean focus) {
            id.setText(entry.id());
            meta.setText(entry.displayType() + "  ·  " + Formatting.date(entry.releaseTime()));
            int[] counts = count(entry);
            content.setText(counts[0] + " 个模组  ·  " + counts[1] + " 个资源包");
            local.setVisible(entry.local());
            Path gameDir = owner.config.isolateVersions()
                    ? owner.config.effectiveGameDir(entry.id()) : owner.config.gameDir();
            icon.setInstance(entry.id(), owner.gamePaths.versionJson(entry.id()), gameDir);
            putClientProperty("selected", selected);
            Integer hover = (Integer) list.getClientProperty("hoverIndex");
            putClientProperty("hover", hover != null && hover == index);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            boolean selected = Boolean.TRUE.equals(getClientProperty("selected"));
            if (selected) {
                g2.setColor(Theme.SEL);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(2, 9, 3, getHeight() - 18, 3, 3);
            } else if (Boolean.TRUE.equals(getClientProperty("hover"))) {
                g2.setColor(Theme.HOVER);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);
            } else {
                g2.setColor(Theme.CARD);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
