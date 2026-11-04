package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Content browser card: search bar, result list, selection footer and pagination. */
final class ContentPage {
    private final Runnable onSearch;
    private final Runnable onRefresh;
    private final Runnable onDetail;
    private final Runnable onInstall;
    private final Runnable onImport;
    private final Consumer<Modrinth.Project> onMcmod;
    private final Runnable onSelectionChanged;
    private final Runnable onPageChanged;
    private final Predicate<Modrinth.Project> installed;
    private JTextField searchField;
    private JTextField versionField;
    private Ui.DarkCombo<String> loaderCombo;
    private Ui.DarkCombo<String> sourceCombo;
    private Ui.DarkCombo<String> sortCombo;
    private boolean sortUpdating;
    private DefaultListModel<Modrinth.Project> model;
    private JList<Modrinth.Project> list;
    private Ui.FadePanel listFade;
    private Ui.AccentButton installButton;
    private Ui.AccentButton searchButton;
    private Ui.AccentButton refreshButton;
    private Ui.AccentButton mcmodButton;
    private Ui.AccentButton importButton;
    private Ui.AccentButton detailButton;
    private JLabel selectionLabel;
    private JTextField pageLabel;
    private Ui.AccentButton prevPage;
    private Ui.AccentButton nextPage;
    private int pageIndex;
    private String type = "mod";
    private boolean versionAuto = true;
    private boolean versionUpdating;
    private String lastSearchedVersion = "";
    private Ui.ToggleChip installedOnlyFilter;
    private List<Modrinth.Project> allResults = List.of();

    ContentPage(Runnable onSearch, Runnable onRefresh, Runnable onDetail, Runnable onInstall,
                Runnable onImport, Consumer<Modrinth.Project> onMcmod, Runnable onSelectionChanged,
                Runnable onPageChanged, Predicate<Modrinth.Project> installed) {
        this.onSearch = onSearch;
        this.onRefresh = onRefresh;
        this.onDetail = onDetail;
        this.onInstall = onInstall;
        this.onImport = onImport;
        this.onMcmod = onMcmod;
        this.onSelectionChanged = onSelectionChanged;
        this.onPageChanged = onPageChanged;
        this.installed = installed;
    }

    JPanel build(ListCellRenderer<Modrinth.Project> renderer) {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);

        JPanel searchBar = new JPanel(new BorderLayout());
        searchBar.setBackground(Theme.CARD);
        searchBar.setBorder(new EmptyBorder(7, 14, 7, 14));
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        sourceCombo = new Ui.DarkCombo<>();
        sourceCombo.addItem("Modrinth");
        sourceCombo.addItem("CurseForge");
        sourceCombo.setPreferredSize(new Dimension(120, 30));
        sourceCombo.setMaximumSize(new Dimension(120, 30));
        sourceCombo.addActionListener(e -> {
            updateSortOptions();
            if (model != null) {
                model.clear();
            }
            searchFromFirstPage();
        });
        sortCombo = new Ui.DarkCombo<>();
        sortCombo.setPreferredSize(new Dimension(112, 30));
        sortCombo.setMaximumSize(new Dimension(112, 30));
        sortCombo.addActionListener(e -> {
            if (!sortUpdating) {
                searchFromFirstPage();
            }
        });
        searchField = Ui.field("搜索 Modrinth / CurseForge 模组、资源包、整合包…");
        searchField.setPreferredSize(new Dimension(380, 30));
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        searchField.addActionListener(e -> searchFromFirstPage());
        versionField = Ui.field("1.20.1", "游戏版本");
        versionField.setPreferredSize(new Dimension(100, 30));
        versionField.setMaximumSize(new Dimension(100, 30));
        versionField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!versionUpdating) {
                    versionAuto = false;
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!versionUpdating) {
                    versionAuto = false;
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                if (!versionUpdating) {
                    versionAuto = false;
                }
            }
        });
        loaderCombo = new Ui.DarkCombo<>();
        loaderCombo.addItem("自动");
        loaderCombo.addItem("Fabric");
        loaderCombo.addItem("Forge");
        loaderCombo.addItem("NeoForge");
        loaderCombo.addItem("Quilt");
        loaderCombo.setPreferredSize(new Dimension(112, 30));
        loaderCombo.setMaximumSize(new Dimension(112, 30));
        loaderCombo.setEnabled(false);
        loaderCombo.setVisible(false);
        searchButton = Ui.smallButton("搜索", Ui.AccentButton.Style.SECONDARY);
        searchButton.setPreferredSize(new Dimension(58, 30));
        searchButton.setMaximumSize(new Dimension(58, 30));
        searchButton.addActionListener(e -> searchFromFirstPage());
        refreshButton = Ui.smallButton("刷新", Ui.AccentButton.Style.SECONDARY);
        refreshButton.setPreferredSize(new Dimension(52, 30));
        refreshButton.setMaximumSize(new Dimension(52, 30));
        refreshButton.setToolTipText("重新搜索当前内容");
        refreshButton.addActionListener(e -> refreshFromFirstPage());
        JPanel searchRow = new JPanel();
        searchRow.setOpaque(false);
        searchRow.setLayout(new BoxLayout(searchRow, BoxLayout.X_AXIS));
        searchRow.add(sourceCombo);
        searchRow.add(Ui.hgap(8));
        searchRow.add(searchField);
        searchRow.add(Ui.hgap(8));
        searchRow.add(searchButton);
        searchRow.add(Ui.hgap(6));
        searchRow.add(refreshButton);
        searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JPanel optionsRow = new JPanel();
        optionsRow.setOpaque(false);
        optionsRow.setLayout(new BoxLayout(optionsRow, BoxLayout.X_AXIS));
        optionsRow.add(Ui.label("游戏版本", Theme.TINY, Theme.DIM));
        optionsRow.add(Ui.hgap(6));
        optionsRow.add(versionField);
        optionsRow.add(Ui.hgap(14));
        optionsRow.add(Ui.label("加载器", Theme.TINY, Theme.DIM));
        optionsRow.add(Ui.hgap(6));
        optionsRow.add(loaderCombo);
        optionsRow.add(Ui.hgap(14));
        optionsRow.add(Ui.label("排序", Theme.TINY, Theme.DIM));
        optionsRow.add(Ui.hgap(6));
        optionsRow.add(sortCombo);
        optionsRow.add(Ui.hgap(10));
        installedOnlyFilter = new Ui.ToggleChip("仅未安装");
        installedOnlyFilter.addActionListener(e -> applyResultsFilter());
        optionsRow.add(installedOnlyFilter);
        optionsRow.add(Ui.hgap(10));
        optionsRow.add(Ui.label("留空版本号时按默认值查询", Theme.MONO, Theme.DIM));
        optionsRow.add(Box.createHorizontalGlue());
        optionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        updateSortOptions();

        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.add(searchRow);
        row.add(Ui.vgap(8));
        row.add(optionsRow);
        searchBar.add(row, BorderLayout.CENTER);
        page.add(searchBar, BorderLayout.NORTH);

        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setBackground(Theme.BG);
        list.setSelectionBackground(Theme.BG);
        list.setSelectionForeground(Theme.TEXT);
        list.setFixedCellHeight(64);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(renderer);
        list.setBorder(new EmptyBorder(4, 8, 14, 8));
        list.addListSelectionListener(e -> onSelectionChanged.run());
        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                list.putClientProperty("hoverIndex", index);
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
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onDetail.run();
                }
            }
        });
        listFade = new Ui.FadePanel(new BorderLayout());
        listFade.add(Ui.scrollDark(list), BorderLayout.CENTER);
        JPanel listArea = new JPanel(new BorderLayout()) {
            @Override
            protected void paintChildren(Graphics g) {
                super.paintChildren(g);
                int height = Math.min(26, getHeight());
                if (height <= 0) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new java.awt.GradientPaint(0, getHeight() - height,
                        new Color(Theme.BG.getRed(), Theme.BG.getGreen(), Theme.BG.getBlue(), 0),
                        0, getHeight(), Theme.BG));
                g2.fillRect(0, getHeight() - height, getWidth(), height);
                g2.dispose();
            }
        };
        listArea.setOpaque(false);
        listArea.add(listFade, BorderLayout.CENTER);
        page.add(listArea, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(true);
        bottom.setBackground(Theme.CARD);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(7, 14, 7, 14)));
        selectionLabel = Ui.label("搜索 Modrinth 内容", Theme.MONO, Theme.DIM);
        bottom.add(selectionLabel, BorderLayout.CENTER);
        installButton = Ui.smallButton("安装所选", Ui.AccentButton.Style.PRIMARY);
        installButton.setPreferredSize(new Dimension(96, 30));
        installButton.setMaximumSize(new Dimension(96, 30));
        installButton.setEnabled(false);
        installButton.addActionListener(e -> onInstall.run());
        importButton = Ui.smallButton("导入整合包 ZIP", Ui.AccentButton.Style.GHOST);
        importButton.setToolTipText("支持 Modrinth .mrpack 和 CurseForge 整合包 zip");
        importButton.addActionListener(e -> onImport.run());
        importButton.setVisible(false);
        detailButton = Ui.smallButton("版本 / 详情", Ui.AccentButton.Style.SECONDARY);
        detailButton.setPreferredSize(new Dimension(96, 30));
        detailButton.setMaximumSize(new Dimension(96, 30));
        detailButton.setEnabled(false);
        detailButton.addActionListener(e -> onDetail.run());
        mcmodButton = Ui.smallButton("MC百科", Ui.AccentButton.Style.GHOST);
        mcmodButton.setPreferredSize(new Dimension(68, 30));
        mcmodButton.setMaximumSize(new Dimension(68, 30));
        mcmodButton.setVisible(false);
        mcmodButton.addActionListener(e -> {
            Modrinth.Project selected = list.getSelectedValue();
            if (selected != null) {
                onMcmod.accept(selected);
            }
        });
        prevPage = Ui.smallButton("<", Ui.AccentButton.Style.GHOST);
        prevPage.setPreferredSize(new Dimension(28, 30));
        prevPage.setMaximumSize(new Dimension(28, 30));
        prevPage.addActionListener(e -> {
            if (pageIndex > 0) {
                pageIndex--;
                onPageChanged.run();
            }
        });
        nextPage = Ui.smallButton(">", Ui.AccentButton.Style.GHOST);
        nextPage.setPreferredSize(new Dimension(28, 30));
        nextPage.setMaximumSize(new Dimension(28, 30));
        nextPage.addActionListener(e -> {
            pageIndex++;
            onPageChanged.run();
        });
        pageLabel = new JTextField("1");
        pageLabel.setFont(Theme.MONO);
        pageLabel.setForeground(Theme.DIM);
        pageLabel.setBackground(Theme.CARD_ALT);
        pageLabel.setCaretColor(Theme.ACCENT);
        pageLabel.setHorizontalAlignment(JTextField.CENTER);
        pageLabel.setBorder(new EmptyBorder(2, 2, 2, 2));
        pageLabel.setPreferredSize(new Dimension(42, 28));
        pageLabel.setMaximumSize(new Dimension(42, 28));
        pageLabel.setToolTipText("输入页码后按回车跳转");
        pageLabel.addActionListener(e -> applyPageInput());
        pageLabel.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                pageLabel.setText(Integer.toString(pageIndex + 1));
            }
        });
        prevPage.setEnabled(false);
        nextPage.setEnabled(false);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.add(prevPage);
        actions.add(pageLabel);
        actions.add(nextPage);
        actions.add(Ui.hgap(10));
        actions.add(mcmodButton);
        actions.add(Ui.hgap(8));
        actions.add(importButton);
        actions.add(Ui.hgap(8));
        actions.add(detailButton);
        actions.add(Ui.hgap(8));
        actions.add(installButton);
        bottom.add(actions, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);
        page.setComponentZOrder(bottom, 0);
        return page;
    }

    private void searchFromFirstPage() {
        pageIndex = 0;
        onSearch.run();
    }

    /** Refresh must bypass the cache, otherwise a bad/empty result sticks for the TTL. */
    private void refreshFromFirstPage() {
        pageIndex = 0;
        onRefresh.run();
    }

    private void applyPageInput() {
        String text = pageLabel.getText().trim();
        int page;
        try {
            page = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            pageLabel.setText(Integer.toString(pageIndex + 1));
            return;
        }
        page = Math.max(1, page);
        if (page - 1 == pageIndex) {
            pageLabel.setText(Integer.toString(pageIndex + 1));
            return;
        }
        pageIndex = page - 1;
        pageLabel.setText(Integer.toString(page));
        onPageChanged.run();
    }

    void updateSortOptions() {
        if (sortCombo == null) {
            return;
        }
        sortUpdating = true;
        try {
            sortCombo.removeAllItems();
            if ("curseforge".equals(source())) {
                sortCombo.addItem("推荐");
                sortCombo.addItem("热门");
                sortCombo.addItem("最近更新");
                sortCombo.addItem("名称");
            } else {
                sortCombo.addItem("相关度");
                sortCombo.addItem("下载量");
                sortCombo.addItem("关注量");
                sortCombo.addItem("最新发布");
                sortCombo.addItem("最近更新");
            }
        } finally {
            sortUpdating = false;
        }
    }

    String source() {
        Object selected = sourceCombo == null ? null : sourceCombo.getSelectedItem();
        return "CurseForge".equals(selected) ? "curseforge" : "modrinth";
    }

    String sortValue() {
        int index = sortCombo == null ? 0 : Math.max(0, sortCombo.getSelectedIndex());
        if ("curseforge".equals(source())) {
            return switch (index) {
                case 1 -> "popularity";
                case 2 -> "updated";
                case 3 -> "name";
                default -> "featured";
            };
        }
        return switch (index) {
            case 1 -> "downloads";
            case 2 -> "follows";
            case 3 -> "newest";
            case 4 -> "updated";
            default -> "relevance";
        };
    }

    String loader() {
        Object selected = loaderCombo == null ? null : loaderCombo.getSelectedItem();
        if (selected == null || "自动".equals(selected)) {
            return null;
        }
        return selected.toString().toLowerCase(java.util.Locale.ROOT);
    }

    void setType(String type) {
        this.type = type == null ? "mod" : type;
    }

    String type() {
        return type;
    }

    String query() {
        return searchField == null ? "" : searchField.getText().trim();
    }

    String versionText() {
        return versionField == null ? "" : versionField.getText().trim();
    }

    void setVersionText(String text) {
        if (versionField != null && !versionField.hasFocus()) {
            versionUpdating = true;
            try {
                versionField.setText(text == null ? "" : text);
            } finally {
                versionUpdating = false;
            }
        }
    }

    boolean versionHasFocus() {
        return versionField != null && versionField.hasFocus();
    }

    boolean versionAuto() {
        return versionAuto;
    }

    void setVersionAuto(boolean auto) {
        this.versionAuto = auto;
    }

    String lastSearchedVersion() {
        return lastSearchedVersion;
    }

    void setLastSearchedVersion(String version) {
        this.lastSearchedVersion = version == null ? "" : version;
    }

    int pageIndex() {
        return pageIndex;
    }

    void setPageIndex(int index) {
        this.pageIndex = Math.max(0, index);
    }

    boolean pageLabelHasFocus() {
        return pageLabel != null && pageLabel.hasFocus();
    }

    void setPageLabel(int page) {
        if (pageLabel != null) {
            pageLabel.setText(Integer.toString(page));
        }
    }

    void setPageButtonsEnabled(boolean prev, boolean next) {
        if (prevPage != null) {
            prevPage.setEnabled(prev);
        }
        if (nextPage != null) {
            nextPage.setEnabled(next);
        }
    }

    void clearResults() {
        allResults = List.of();
        if (model != null) {
            model.clear();
        }
    }

    /** Replaces the result set; the "only not installed" filter is applied here. */
    void setResults(List<Modrinth.Project> projects) {
        allResults = projects == null ? List.of() : List.copyOf(projects);
        applyResultsFilter();
    }

    private void applyResultsFilter() {
        if (model == null) {
            return;
        }
        model.clear();
        boolean onlyMissing = installedOnlyFilter != null && installedOnlyFilter.isSelected();
        for (Modrinth.Project project : allResults) {
            if (!onlyMissing || installed == null || !installed.test(project)) {
                model.addElement(project);
            }
        }
    }

    int resultCount() {
        return model == null ? 0 : model.size();
    }

    Modrinth.Project resultAt(int index) {
        return model == null || index < 0 || index >= model.size() ? null : model.get(index);
    }

    Modrinth.Project selectedProject() {
        return list == null ? null : list.getSelectedValue();
    }

    java.util.List<Modrinth.Project> selectedProjects() {
        return list == null ? java.util.List.of() : list.getSelectedValuesList();
    }

    void selectIndex(int index) {
        if (list != null) {
            list.setSelectedIndex(index);
        }
    }

    void selectFirst() {
        if (list != null && model != null && !model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    void setSelectionText(String text) {
        if (selectionLabel != null) {
            selectionLabel.setText(text);
        }
    }

    void setInstallButton(String text, boolean enabled) {
        if (installButton != null) {
            installButton.setText(text);
            installButton.setEnabled(enabled);
        }
    }

    void setInstallEnabled(boolean enabled) {
        if (installButton != null) {
            installButton.setEnabled(enabled);
        }
    }

    void setInstallVisible(boolean visible) {
        if (installButton != null) {
            installButton.setVisible(visible);
        }
    }

    void setDetailEnabled(boolean enabled) {
        if (detailButton != null) {
            detailButton.setEnabled(enabled);
        }
    }

    void setMcmodVisible(boolean visible) {
        if (mcmodButton != null) {
            mcmodButton.setVisible(visible);
        }
    }

    void setImportVisible(boolean visible) {
        if (importButton != null) {
            importButton.setVisible(visible);
        }
    }

    void setLoaderVisible(boolean visible) {
        if (loaderCombo != null) {
            loaderCombo.setVisible(visible);
            loaderCombo.setEnabled(visible);
        }
    }

    void setControlsEnabled(boolean enabled) {
        // 浏览、搜索、翻页始终可用；安装按钮由选中状态单独控制，详情按钮不受 busy 影响。
        if (searchField == null) {
            return;
        }
        searchField.setEnabled(true);
        if (versionField != null) {
            versionField.setEnabled(true);
        }
        if (sourceCombo != null) {
            sourceCombo.setEnabled(true);
        }
        if (sortCombo != null) {
            sortCombo.setEnabled(true);
        }
        if (searchButton != null) {
            searchButton.setEnabled(true);
        }
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
        }
        if (list != null) {
            list.setEnabled(true);
        }
        boolean hasSelection = list != null && list.getSelectedValue() != null;
        if (detailButton != null) {
            detailButton.setEnabled(hasSelection);
        }
        if (installButton != null) {
            installButton.setEnabled(hasSelection && enabled);
        }
    }

    // 供 LauncherFrame 的搜索/安装流程使用的最小访问器。
    JList<Modrinth.Project> list() {
        return list;
    }

    DefaultListModel<Modrinth.Project> model() {
        return model;
    }

    JTextField searchField() {
        return searchField;
    }

    JTextField versionField() {
        return versionField;
    }

    Ui.AccentButton installButton() {
        return installButton;
    }

    Ui.AccentButton importButton() {
        return importButton;
    }

    Ui.AccentButton detailButton() {
        return detailButton;
    }

    Ui.AccentButton mcmodButton() {
        return mcmodButton;
    }

    JLabel selectionLabel() {
        return selectionLabel;
    }

    Ui.FadePanel listFade() {
        return listFade;
    }
}
