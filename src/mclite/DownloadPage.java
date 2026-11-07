package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * Download center shell: top bar (search / filters / refresh), content subtabs and the
 * card container for versions, content browsing and the two detail pages.
 *
 * <p>Card bodies are still provided by {@link LauncherFrame}; they move in here in later steps.</p>
 */
final class DownloadPage {
    private final Runnable onSearchChanged;
    private final Consumer<String> onFilter;
    private final Runnable onRefresh;
    private final Runnable onDetail;
    private final Consumer<String> onTab;
    private final Runnable onRegionNotice;
    private JLabel title;
    private JLabel subtitle;
    private JPanel tools;
    private JPanel subtabs;
    private JPanel cards;
    private CardLayout cardsLayout;
    private Ui.SegmentButton versionsTab;
    private Ui.SegmentButton modsTab;
    private Ui.SegmentButton packsTab;
    private Ui.SegmentButton modpacksTab;
    private Ui.SegmentButton shadersTab;
    private Ui.SegmentButton pluginsTab;
    private JTextField searchField;
    private Ui.SegmentButton releaseFilter;
    private Ui.SegmentButton snapshotFilter;
    private Ui.SegmentButton allFilter;
    private Ui.AccentButton refreshButton;
    private Ui.AccentButton detailButton;

    DownloadPage(Runnable onSearchChanged, Consumer<String> onFilter,
                 Runnable onRefresh, Runnable onDetail, Consumer<String> onTab,
                 Runnable onRegionNotice) {
        this.onSearchChanged = onSearchChanged;
        this.onFilter = onFilter;
        this.onRefresh = onRefresh;
        this.onDetail = onDetail;
        this.onTab = onTab;
        this.onRegionNotice = onRegionNotice;
    }

    JPanel build(JPanel versionsCard, JPanel contentCard, JPanel versionDetailCard,
                 JPanel projectDetailCard, JPanel pluginsCard) {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(buildTopbar(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        subtabs = buildSubtabs();
        middle.add(subtabs, BorderLayout.NORTH);
        cardsLayout = new CardLayout();
        cards = new Ui.FadePanel(cardsLayout);
        cards.setOpaque(false);
        cards.add(versionsCard, "versions");
        cards.add(contentCard, "content");
        cards.add(versionDetailCard, "versionDetail");
        cards.add(projectDetailCard, "projectDetail");
        cards.add(pluginsCard, "plugins");
        middle.add(cards, BorderLayout.CENTER);
        page.add(middle, BorderLayout.CENTER);
        return page;
    }

    void setTitle(String text) {
        if (title != null) {
            title.setText(text);
        }
    }

    void setSubtitle(String text) {
        if (subtitle != null) {
            subtitle.setText(text);
        }
    }

    void setToolsVisible(boolean visible) {
        if (tools != null) {
            tools.setVisible(visible);
        }
    }

    void setSubtabsVisible(boolean visible) {
        if (subtabs != null) {
            subtabs.setVisible(visible);
        }
    }

    void showCard(String name) {
        if (cardsLayout == null) {
            return;
        }
        cardsLayout.show(cards, name);
        if (cards instanceof Ui.FadePanel fade) {
            fade.fadeIn();
        }
    }

    void selectTab(String type) {
        boolean versions = "versions".equals(type);
        if (versionsTab == null) {
            return;
        }
        versionsTab.setSelected(versions);
        modsTab.setSelected("mod".equals(type));
        packsTab.setSelected("resourcepack".equals(type));
        modpacksTab.setSelected("modpack".equals(type));
        shadersTab.setSelected("shader".equals(type));
        pluginsTab.setSelected("plugins".equals(type));
    }

    void selectFilter(String filter) {
        if (releaseFilter == null) {
            return;
        }
        releaseFilter.setSelected(filter.equals("release"));
        snapshotFilter.setSelected(filter.equals("snapshot"));
        allFilter.setSelected(filter.equals("all"));
    }

    String query() {
        return searchField == null ? "" : searchField.getText().trim();
    }

    void clearQuery() {
        if (searchField != null) {
            searchField.setText("");
        }
    }

    void setSearchEnabled(boolean enabled) {
        if (searchField != null) {
            searchField.setEnabled(enabled);
        }
    }

    void setFiltersEnabled(boolean enabled) {
        for (Ui.SegmentButton filter : new Ui.SegmentButton[]{releaseFilter, snapshotFilter, allFilter}) {
            if (filter != null) {
                filter.setEnabled(enabled);
            }
        }
    }

    void setRefreshEnabled(boolean enabled) {
        if (refreshButton != null) {
            refreshButton.setEnabled(enabled);
        }
    }

    void setDetailEnabled(boolean enabled) {
        if (detailButton != null) {
            detailButton.setEnabled(enabled);
        }
    }

    private JPanel buildTopbar() {
        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setBackground(Theme.CARD);
        top.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(8, 14, 8, 14)));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        title = Ui.label("下载中心", Theme.H2, Theme.TEXT);
        subtitle = Ui.label("Mojang 官方清单 · 选择版本下载或启动", Theme.MONO, Theme.DIM);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(title);
        info.add(Ui.vgap(1));
        info.add(subtitle);
        if (RegionGate.restricted()) {
            JLabel notice = Ui.label("<html>为遵守相关法律法规，不提供游戏本体和整合包下载；<br>"
                            + "模组、资源包、光影下载可用</html>", Theme.TINY, Theme.WARNING);
            notice.setAlignmentX(Component.LEFT_ALIGNMENT);
            notice.setCursor(java.awt.Cursor.getPredefinedCursor(
                    java.awt.Cursor.HAND_CURSOR));
            notice.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    onRegionNotice.run();
                }
            });
            info.add(Ui.vgap(3));
            info.add(notice);
        }
        top.add(info, BorderLayout.CENTER);

        JPanel toolRow = new JPanel();
        toolRow.setOpaque(false);
        toolRow.setLayout(new BoxLayout(toolRow, BoxLayout.X_AXIS));
        tools = toolRow;
        searchField = Ui.field("搜索版本号，例如 1.20.1 / 26.3");
        searchField.setPreferredSize(new Dimension(210, 30));
        searchField.setMaximumSize(new Dimension(210, 30));
        searchField.setAlignmentY(Component.CENTER_ALIGNMENT);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onSearchChanged.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onSearchChanged.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onSearchChanged.run();
            }
        });
        toolRow.add(searchField);
        toolRow.add(Ui.hgap(8));

        releaseFilter = new Ui.SegmentButton("正式版");
        snapshotFilter = new Ui.SegmentButton("快照");
        allFilter = new Ui.SegmentButton("全部");
        ButtonGroup filters = new ButtonGroup();
        filters.add(releaseFilter);
        filters.add(snapshotFilter);
        filters.add(allFilter);
        releaseFilter.addActionListener(e -> onFilter.accept("release"));
        snapshotFilter.addActionListener(e -> onFilter.accept("snapshot"));
        allFilter.addActionListener(e -> onFilter.accept("all"));
        toolRow.add(releaseFilter);
        toolRow.add(snapshotFilter);
        toolRow.add(allFilter);
        toolRow.add(Ui.hgap(8));

        detailButton = Ui.smallButton("详情", Ui.AccentButton.Style.SECONDARY);
        detailButton.setPreferredSize(new Dimension(52, 30));
        detailButton.setMaximumSize(new Dimension(52, 30));
        detailButton.setEnabled(false);
        detailButton.addActionListener(e -> onDetail.run());
        toolRow.add(detailButton);
        toolRow.add(Ui.hgap(6));
        refreshButton = Ui.smallButton("刷新", Ui.AccentButton.Style.SECONDARY);
        refreshButton.setPreferredSize(new Dimension(52, 30));
        refreshButton.setMaximumSize(new Dimension(52, 30));
        refreshButton.addActionListener(e -> onRefresh.run());
        toolRow.add(refreshButton);
        top.add(toolRow, BorderLayout.EAST);
        return top;
    }

    private JPanel buildSubtabs() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.CARD);
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(4, 14, 4, 14)));
        JPanel tabs = new JPanel();
        tabs.setOpaque(false);
        tabs.setLayout(new BoxLayout(tabs, BoxLayout.X_AXIS));
        versionsTab = new Ui.SegmentButton("游戏版本");
        modsTab = new Ui.SegmentButton("模组");
        packsTab = new Ui.SegmentButton("资源包");
        modpacksTab = new Ui.SegmentButton("整合包");
        shadersTab = new Ui.SegmentButton("光影");
        pluginsTab = new Ui.SegmentButton("启动器插件");
        ButtonGroup group = new ButtonGroup();
        group.add(versionsTab);
        group.add(modsTab);
        group.add(packsTab);
        group.add(modpacksTab);
        group.add(shadersTab);
        group.add(pluginsTab);
        versionsTab.setSelected(true);
        versionsTab.addActionListener(e -> onTab.accept("versions"));
        modsTab.addActionListener(e -> onTab.accept("mod"));
        packsTab.addActionListener(e -> onTab.accept("resourcepack"));
        modpacksTab.addActionListener(e -> onTab.accept("modpack"));
        shadersTab.addActionListener(e -> onTab.accept("shader"));
        pluginsTab.addActionListener(e -> onTab.accept("plugins"));
        tabs.add(versionsTab);
        tabs.add(modsTab);
        tabs.add(packsTab);
        tabs.add(modpacksTab);
        tabs.add(shadersTab);
        tabs.add(pluginsTab);
        bar.add(tabs, BorderLayout.WEST);
        return bar;
    }
}
