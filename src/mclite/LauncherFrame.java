package mclite;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class LauncherFrame extends JFrame {
    private static final DateTimeFormatter CACHE_TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Config config;
    private GamePaths gamePaths;
    private VersionManifest manifest;
    private List<VersionManifest.VersionEntry> allVersions = List.of();
    private DefaultListModel<VersionManifest.VersionEntry> listModel = new DefaultListModel<>();
    private final JList<VersionManifest.VersionEntry> versionList = new JList<>(listModel);
    private final Map<String, VersionMeta> metaCache = new ConcurrentHashMap<>();
    private final Set<String> installedCache = new HashSet<>();
    private final List<JavaManager.JavaInfo> javaInfos = new ArrayList<>();
    private final Ui.DarkCombo<JavaChoice> javaCombo = new Ui.DarkCombo<>();

    private JTextField searchField;
    private JTextField gameDirField;
    private JTextField jvmArgsField;
    private Ui.ValueSlider memorySlider;
    private Ui.ToggleChip autoMemoryToggle;
    private Ui.ToggleChip autoJvmToggle;
    private Ui.ToggleChip autoJavaToggle;
    private Ui.ValueSlider widthSlider;
    private Ui.ValueSlider heightSlider;
    private JToggleButton assetsToggle;
    private JToggleButton mirrorToggle;
    private Ui.SegmentButton releaseFilter;
    private Ui.SegmentButton snapshotFilter;
    private Ui.SegmentButton allFilter;
    private Ui.AccentButton refreshButton;
    private Ui.AccentButton installButton;
    private Ui.AccentButton launchButton;
    private Ui.AccentButton cancelButton;
    private Ui.AccentButton logButton;
    private Ui.AccentButton tasksButton;
    private Ui.AccentButton openDirButton;
    private JLabel detailTitle;
    private JTextArea detailMeta;
    private JLabel statusLabel;
    private JLabel sourceLabel;
    private JLabel countLabel;
    private JTextArea javaHint;
    private JLabel emptyLabel;
    private JPanel listCards;
    private CardLayout listCardLayout;
    private Ui.ThinProgress progressBar;
    private CardLayout contentCards;
    private JPanel contentCardsPanel;
    private Ui.NavTab downloadTab;
    private Ui.NavTab configTab;
    private Ui.NavTab launchTab;
    private Ui.NavTab settingsTab;
    private Ui.AccentButton versionPickerButton;
    private Ui.AccentButton instanceButton;
    private Ui.AccentButton sidebarSwitchButton;
    private Ui.AccentButton instanceLaunchButton;
    private boolean pendingLaunch;
    private JList<VersionManifest.VersionEntry> instancesList;
    private DefaultListModel<VersionManifest.VersionEntry> instancesListModel;
    private JLabel instancesDetailTitle;
    private JLabel instancesDetailMeta;
    private JLabel instanceModsHeader;
    private JLabel instancePacksHeader;
    private DefaultListModel<String> instanceModsModel;
    private DefaultListModel<String> instancePacksModel;
    private final Map<String, int[]> instanceCounts = new HashMap<>();
    private Ui.AccentButton headerBackButton;
    private String currentTop = "download";
    private String currentDownloadCard = "versions";
    private JPanel downloadTools;
    private JPanel sidebar;
    private Ui.AccentButton sidebarToggle;
    private JLabel downloadTitle;
    private JLabel downloadSubtitle;
    private JPanel downloadCards;
    private JPanel downloadSubtabs;
    private CardLayout downloadCardsLayout;
    private Ui.SegmentButton versionsTab;
    private Ui.SegmentButton modsTab;
    private Ui.SegmentButton packsTab;
    private Ui.SegmentButton modpacksTab;
    private Ui.SegmentButton shadersTab;
    private JTextField contentSearchField;
    private JTextField contentVersionField;
    private Ui.DarkCombo<String> contentLoaderCombo;
    private Ui.DarkCombo<String> contentSourceCombo;
    private JTextField curseForgeKeyField;
    private JTextArea uiCssArea;
    private Ui.DarkCombo<FontCatalog.Choice> fontCombo;
    private DefaultListModel<Modrinth.Project> contentModel;
    private JList<Modrinth.Project> contentList;
    private Ui.FadePanel contentListFade;
    private Ui.AccentButton contentInstallButton;
    private Ui.AccentButton contentSearchButton;
    private Ui.AccentButton contentRefreshButton;
    private Ui.AccentButton contentMcmodButton;
    private Ui.AccentButton contentDetailButton;
    private JLabel contentSelectionLabel;
    private Ui.AccentButton downloadDetailButton;
    private String contentType = "mod";
    private boolean contentSearching;
    private int contentPage;
    private JLabel contentPageLabel;
    private Ui.AccentButton contentPrevPage;
    private Ui.AccentButton contentNextPage;
    private int contentGeneration;
    private boolean contentVersionAuto = true;
    private boolean contentVersionUpdating;
    private String lastSearchedContentVersion = "";
    private final java.util.Map<String, String> contentChineseTitles = new java.util.concurrent.ConcurrentHashMap<>();
    private Ui.ToggleChip forceTranslationToggle;
    private JLabel versionDetailTitle;
    private JTextArea versionDetailMeta;
    private String versionLoaderType = "vanilla";
    private JTextField versionLoaderVersion;
    private String versionDetailId;
    private Ui.AccentButton versionInstallButton;
    private Modrinth.Project detailProject;
    private JLabel projectDetailTitle;
    private JLabel projectDetailMeta;
    private JLabel projectDetailSummary;
    private Ui.ProjectIcon projectDetailIcon;
    private DefaultListModel<Modrinth.Version> projectVersionModel;
    private JList<Modrinth.Version> projectVersionList;
    private Ui.AccentButton projectInstallButton;
    private Ui.AccentButton projectRefreshButton;
    private JLabel sidebarAccount;
    private JLabel launchAccount;
    private JLabel launchVersion;
    private JTextArea launchMeta;
    private JLabel launchJava;
    private JLabel launchMemory;
    private JLabel launchJvm;
    private JLabel launchAssets;
    private JLabel accountsDirLabel;
    private JLabel configAccountLabel;
    private Ui.SkinHead sidebarSkin;
    private Ui.SkinHead launchSkin;
    private Ui.SkinHead configSkin;

    private VersionManifest.VersionEntry selectedEntry;
    private VersionMeta selectedMeta;
    private int selectionGeneration;
    private boolean refreshingList;
    private boolean refreshingJavaCombo;
    private boolean busy;
    private boolean manifestLoading;
    private CancellationToken taskToken;
    private TaskProgress taskProgress;
    private final TaskCenter taskCenter = new TaskCenter();
    private DownloadTask activeTask;
    private Timer progressTimer;
    private Timer searchTimer;
    private Process gameProcess;
    private Path lastGameLog;
    private boolean launching;
    private Path lastGameContentDir;

    public LauncherFrame(Config config) {
        super("MC Lite 启动器");
        this.config = config;
        this.gamePaths = new GamePaths(config.gameDir());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setSize(1180, 900);
        reloadAccounts();
        setContentPane(buildRoot());
        adoptHmclGameDirIfEmpty();
        if (config.sidebarCollapsed()) {
            sidebar.setVisible(false);
            sidebar.setPreferredSize(new Dimension(0, 0));
            sidebar.setMinimumSize(new Dimension(0, 0));
        }
        updateAccountLabels();
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeApplication();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                repaintAfterWake();
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                repaintAfterWake();
            }
        });
        addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                repaintAfterWake();
            }

            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
            }
        });
        refreshInstalledCache();
        refreshJavaChoices(null);
        startJavaDetection();
        loadManifest();
    }

    private void repaintAfterWake() {
        SwingUtilities.invokeLater(() -> {
            repaintTree(this);
            invalidate();
            validate();
        });
    }

    private static void repaintTree(java.awt.Component component) {
        component.invalidate();
        component.repaint();
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                repaintTree(child);
            }
        }
    }

    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(Theme.BG_SOFT);
        bar.setPreferredSize(new Dimension(0, 40));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(0, 12, 0, 12)));

        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.X_AXIS));
        headerBackButton = Ui.smallButton("← 返回", Ui.AccentButton.Style.SECONDARY);
        headerBackButton.setPreferredSize(new Dimension(64, 26));
        headerBackButton.setMaximumSize(new Dimension(64, 26));
        headerBackButton.setEnabled(false);
        headerBackButton.setToolTipText("返回上一级");
        headerBackButton.addActionListener(e -> goBack());
        identity.add(headerBackButton);
        identity.add(Ui.hgap(6));
        sidebarToggle = Ui.smallButton(config.sidebarCollapsed() ? "☰" : "‹",
                Ui.AccentButton.Style.GHOST);
        sidebarToggle.setPreferredSize(new Dimension(28, 26));
        sidebarToggle.setMaximumSize(new Dimension(28, 26));
        sidebarToggle.setToolTipText("收起 / 展开侧边栏");
        sidebarToggle.addActionListener(e -> toggleSidebar());
        identity.add(sidebarToggle);
        identity.add(Ui.hgap(8));
        identity.add(new Ui.LogoMark(20));
        identity.add(Ui.hgap(9));
        identity.add(Ui.label("MC Lite", Theme.BODY_BOLD, Theme.TEXT));
        JPanel separator = new JPanel();
        separator.setBackground(Theme.BORDER);
        separator.setPreferredSize(new Dimension(1, 14));
        separator.setMaximumSize(new Dimension(1, 14));
        identity.add(Ui.hgap(9));
        identity.add(separator);
        identity.add(Ui.hgap(9));
        identity.add(Ui.label("Minecraft 版本启动器", Theme.TINY, Theme.DIM));
        bar.add(identity, BorderLayout.WEST);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.X_AXIS));
        nav.add(Box.createHorizontalGlue());
        downloadTab = new Ui.NavTab("下载中心");
        launchTab = new Ui.NavTab("启动游戏");
        configTab = new Ui.NavTab("启动器配置");
        settingsTab = new Ui.NavTab("设置");
        ButtonGroup group = new ButtonGroup();
        group.add(downloadTab);
        group.add(launchTab);
        group.add(configTab);
        group.add(settingsTab);
        downloadTab.setSelected(true);
        downloadTab.addActionListener(e -> showPage("download"));
        launchTab.addActionListener(e -> showPage("launch"));
        configTab.addActionListener(e -> showPage("config"));
        settingsTab.addActionListener(e -> showPage("settings"));
        nav.add(downloadTab);
        nav.add(launchTab);
        versionPickerButton = Ui.smallButton("版本列表", Ui.AccentButton.Style.SECONDARY);
        versionPickerButton.setPreferredSize(new Dimension(126, 28));
        versionPickerButton.setMaximumSize(new Dimension(126, 28));
        versionPickerButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        versionPickerButton.setToolTipText("选择游戏版本 / 实例");
        versionPickerButton.addActionListener(e -> selectPage("instances"));
        nav.add(Ui.hgap(4));
        nav.add(versionPickerButton);
        nav.add(configTab);
        nav.add(settingsTab);
        nav.add(Box.createHorizontalGlue());
        bar.add(nav, BorderLayout.CENTER);

        JPanel badges = new JPanel();
        badges.setOpaque(false);
        badges.setLayout(new BoxLayout(badges, BoxLayout.X_AXIS));
        badges.add(new Ui.Pill("官方源", Theme.alpha(Theme.ACCENT, 28), Theme.ACCENT,
                Theme.alpha(Theme.ACCENT, 90)));
        badges.add(Ui.hgap(6));
        badges.add(new Ui.Pill("SHA-1", Theme.CARD_ALT, Theme.DIM, Theme.BORDER));
        badges.add(Ui.hgap(10));
        badges.add(Ui.label("V" + GameLauncher.LAUNCHER_VERSION, Theme.MONO, Theme.DIM));
        bar.add(badges, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(true);
        body.setBackground(Theme.BG);
        sidebar = buildSidebar();
        body.add(sidebar, BorderLayout.WEST);
        body.add(buildContent(), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.CARD);
        sidebar.setPreferredSize(new Dimension(286, 0));
        sidebar.setMinimumSize(new Dimension(286, 0));
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, Theme.BORDER));
        sidebar.add(buildSidebarForm(), BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel buildSidebarForm() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(20, 16, 12, 16));
        form.add(lockedHeight(buildSidebarAccount()));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));
        form.add(lockedHeight(buildSidebarVersion()));
        form.add(Box.createVerticalGlue());
        return form;
    }

    private JComponent buildSidebarAccount() {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(Ui.label("账户", Theme.TINY, Theme.DIM), BorderLayout.WEST);
        Ui.AccentButton switchAccount = Ui.smallButton("切换", Ui.AccentButton.Style.LINK);
        switchAccount.setFont(Theme.TINY);
        switchAccount.addActionListener(e -> openAccountsDialog());
        heading.add(switchAccount, BorderLayout.EAST);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(heading);
        section.add(Ui.vgap(9));

        JPanel identity = new JPanel(new BorderLayout(9, 0));
        identity.setOpaque(false);
        sidebarSkin = new Ui.SkinHead(36);
        identity.add(sidebarSkin, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        sidebarAccount = Ui.label(config.username(), Theme.BODY_BOLD, Theme.TEXT);
        JLabel type = Ui.label("离线账户", Theme.TINY, Theme.DIM);
        text.add(sidebarAccount);
        text.add(Ui.vgap(1));
        text.add(type);
        identity.add(text, BorderLayout.CENTER);
        identity.setAlignmentX(Component.LEFT_ALIGNMENT);
        identity.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        section.add(identity);
        return section;
    }

    private JComponent buildSidebarVersion() {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel heading = Ui.label("当前版本", Theme.TINY, Theme.DIM);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(heading);
        section.add(Ui.vgap(6));
        detailTitle = Ui.label("选择一个版本", Theme.H1, Theme.TEXT);
        detailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(detailTitle);
        section.add(Ui.vgap(3));
        detailMeta = new JTextArea(4, 18);
        detailMeta.setEditable(false);
        detailMeta.setFocusable(false);
        detailMeta.setOpaque(false);
        detailMeta.setLineWrap(true);
        detailMeta.setWrapStyleWord(true);
        detailMeta.setFont(Theme.MONO);
        detailMeta.setForeground(Theme.DIM);
        detailMeta.setBorder(new EmptyBorder(0, 0, 0, 0));
        detailMeta.setText("从「下载中心」选择版本");
        detailMeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailMeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        section.add(detailMeta);
        section.add(Ui.vgap(10));
        instanceButton = Ui.smallButton("游戏实例", Ui.AccentButton.Style.SECONDARY);
        instanceButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        instanceButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        instanceButton.setToolTipText("查看游戏文件夹中的全部实例");
        instanceButton.addActionListener(e -> selectPage("instances"));
        section.add(instanceButton);
        section.add(Ui.vgap(6));
        sidebarSwitchButton = Ui.smallButton("切换启动版本", Ui.AccentButton.Style.GHOST);
        sidebarSwitchButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sidebarSwitchButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarSwitchButton.setToolTipText("选择启动游戏时使用的版本或整合包");
        sidebarSwitchButton.addActionListener(e -> {
            List<VersionManifest.VersionEntry> entries = allVersions.isEmpty()
                    ? LocalVersions.scan(gamePaths) : allVersions;
            if (entries.isEmpty()) {
                showError("没有可用版本", "请先到「下载中心」刷新版本列表。");
                return;
            }
            new VersionsDialog(this, "切换启动版本", entries,
                    entry -> useVersion(entry, false)).setVisible(true);
        });
        section.add(sidebarSwitchButton);
        return section;
    }

    private JPanel buildContent() {
        contentCards = new CardLayout();
        contentCardsPanel = new Ui.FadePanel(contentCards);
        contentCardsPanel.setOpaque(false);
        contentCardsPanel.add(buildDownloadPage(), "download");
        contentCardsPanel.add(buildLaunchPage(), "launch");
        contentCardsPanel.add(buildInstancesPage(), "instances");
        contentCardsPanel.add(buildConfigPage(), "config");
        contentCardsPanel.add(buildSettingsPage(), "settings");

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(true);
        content.setBackground(Theme.BG);
        content.add(contentCardsPanel, BorderLayout.CENTER);
        content.add(buildStatusbar(), BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildDownloadPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(buildDownloadTopbar(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        downloadSubtabs = buildDownloadSubtabs();
        middle.add(downloadSubtabs, BorderLayout.NORTH);
        downloadCardsLayout = new CardLayout();
        downloadCards = new Ui.FadePanel(downloadCardsLayout);
        downloadCards.setOpaque(false);
        downloadCards.add(buildVersionList(), "versions");
        downloadCards.add(buildContentPage(), "content");
        downloadCards.add(buildVersionDetailPage(), "versionDetail");
        downloadCards.add(buildProjectDetailPage(), "projectDetail");
        middle.add(downloadCards, BorderLayout.CENTER);
        page.add(middle, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildDownloadSubtabs() {
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
        ButtonGroup group = new ButtonGroup();
        group.add(versionsTab);
        group.add(modsTab);
        group.add(packsTab);
        group.add(modpacksTab);
        group.add(shadersTab);
        versionsTab.setSelected(true);
        versionsTab.addActionListener(e -> showDownloadTab("versions"));
        modsTab.addActionListener(e -> showDownloadTab("mod"));
        packsTab.addActionListener(e -> showDownloadTab("resourcepack"));
        modpacksTab.addActionListener(e -> showDownloadTab("modpack"));
        shadersTab.addActionListener(e -> showDownloadTab("shader"));
        tabs.add(versionsTab);
        tabs.add(modsTab);
        tabs.add(packsTab);
        tabs.add(modpacksTab);
        tabs.add(shadersTab);
        bar.add(tabs, BorderLayout.WEST);
        return bar;
    }

    private JPanel buildDownloadTopbar() {
        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setBackground(Theme.CARD);
        top.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(8, 14, 8, 14)));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        downloadTitle = Ui.label("下载中心", Theme.H2, Theme.TEXT);
        downloadSubtitle = Ui.label("Mojang 官方清单 · 选择版本下载或启动", Theme.MONO, Theme.DIM);
        downloadTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        downloadSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(downloadTitle);
        info.add(Ui.vgap(1));
        info.add(downloadSubtitle);
        if (RegionGate.restricted()) {
            JLabel notice = Ui.label("<html>为遵守相关法律法规，不提供游戏本体和整合包下载；<br>"
                            + "模组、资源包、光影下载可用</html>", Theme.TINY, Theme.WARNING);
            notice.setAlignmentX(Component.LEFT_ALIGNMENT);
            notice.setCursor(java.awt.Cursor.getPredefinedCursor(
                    java.awt.Cursor.HAND_CURSOR));
            notice.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    RegionGate.showGuide(LauncherFrame.this);
                }
            });
            info.add(Ui.vgap(3));
            info.add(notice);
        }
        top.add(info, BorderLayout.CENTER);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.setLayout(new BoxLayout(tools, BoxLayout.X_AXIS));
        downloadTools = tools;
        searchField = Ui.field("搜索版本号，例如 1.20.1 / 26.3");
        searchField.setPreferredSize(new Dimension(210, 30));
        searchField.setMaximumSize(new Dimension(210, 30));
        searchField.setAlignmentY(Component.CENTER_ALIGNMENT);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleSearch();
            }
        });
        tools.add(searchField);
        tools.add(Ui.hgap(8));

        releaseFilter = new Ui.SegmentButton("正式版");
        snapshotFilter = new Ui.SegmentButton("快照");
        allFilter = new Ui.SegmentButton("全部");
        ButtonGroup filters = new ButtonGroup();
        filters.add(releaseFilter);
        filters.add(snapshotFilter);
        filters.add(allFilter);
        releaseFilter.addActionListener(e -> setFilter("release"));
        snapshotFilter.addActionListener(e -> setFilter("snapshot"));
        allFilter.addActionListener(e -> setFilter("all"));
        tools.add(releaseFilter);
        tools.add(snapshotFilter);
        tools.add(allFilter);
        tools.add(Ui.hgap(8));

        downloadDetailButton = Ui.smallButton("详情", Ui.AccentButton.Style.SECONDARY);
        downloadDetailButton.setPreferredSize(new Dimension(52, 30));
        downloadDetailButton.setMaximumSize(new Dimension(52, 30));
        downloadDetailButton.setEnabled(false);
        downloadDetailButton.addActionListener(e -> openVersionDetail());
        tools.add(downloadDetailButton);
        tools.add(Ui.hgap(6));
        refreshButton = Ui.smallButton("刷新", Ui.AccentButton.Style.SECONDARY);
        refreshButton.setPreferredSize(new Dimension(52, 30));
        refreshButton.setMaximumSize(new Dimension(52, 30));
        refreshButton.addActionListener(e -> loadManifest());
        tools.add(refreshButton);
        top.add(tools, BorderLayout.EAST);
        return top;
    }

    private JPanel buildVersionList() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(true);
        wrap.setBackground(Theme.BG);

        versionList.setFixedCellHeight(52);
        versionList.setBackground(Theme.BG);
        versionList.setSelectionBackground(Theme.BG);
        versionList.setSelectionForeground(Theme.TEXT);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setCellRenderer(new VersionRenderer());
        versionList.setBorder(new EmptyBorder(4, 8, 8, 8));
        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshingList) {
                onVersionSelected();
            }
        });
        versionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openVersionDetail();
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                versionList.putClientProperty("hoverIndex", -1);
                versionList.repaint();
            }
        });
        versionList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                versionList.putClientProperty("hoverIndex", versionList.locationToIndex(e.getPoint()));
                versionList.repaint();
            }
        });

        JScrollPane scroll = Ui.scrollDark(versionList);
        listCardLayout = new CardLayout();
        listCards = new JPanel(listCardLayout);
        listCards.setOpaque(false);
        listCards.add(scroll, "list");
        JPanel empty = new JPanel(new BorderLayout());
        empty.setOpaque(false);
        emptyLabel = Ui.label("没有匹配的版本", Theme.BODY, Theme.DIM);
        emptyLabel.setHorizontalAlignment(JLabel.CENTER);
        empty.add(emptyLabel, BorderLayout.CENTER);
        listCards.add(empty, "empty");
        wrap.add(listCards, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildContentPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);

        JPanel searchBar = new JPanel(new BorderLayout());
        searchBar.setBackground(Theme.CARD);
        searchBar.setBorder(new EmptyBorder(7, 14, 7, 14));
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        contentSourceCombo = new Ui.DarkCombo<>();
        contentSourceCombo.addItem("Modrinth");
        contentSourceCombo.addItem("CurseForge");
        contentSourceCombo.setPreferredSize(new Dimension(120, 30));
        contentSourceCombo.setMaximumSize(new Dimension(120, 30));
        contentSourceCombo.addActionListener(e -> {
            contentModel.clear();
            searchContent();
        });
        contentSearchField = Ui.field("搜索 Modrinth / CurseForge 模组、资源包、整合包…");
        contentSearchField.setPreferredSize(new Dimension(380, 30));
        contentSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        contentSearchField.addActionListener(e -> searchContent());
        contentVersionField = Ui.field("1.20.1", "游戏版本");
        contentVersionField.setPreferredSize(new Dimension(100, 30));
        contentVersionField.setMaximumSize(new Dimension(100, 30));
        contentVersionField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!contentVersionUpdating) {
                    contentVersionAuto = false;
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!contentVersionUpdating) {
                    contentVersionAuto = false;
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                if (!contentVersionUpdating) {
                    contentVersionAuto = false;
                }
            }
        });
        contentLoaderCombo = new Ui.DarkCombo<>();
        contentLoaderCombo.addItem("自动");
        contentLoaderCombo.addItem("Fabric");
        contentLoaderCombo.addItem("Forge");
        contentLoaderCombo.addItem("NeoForge");
        contentLoaderCombo.addItem("Quilt");
        contentLoaderCombo.setPreferredSize(new Dimension(112, 30));
        contentLoaderCombo.setMaximumSize(new Dimension(112, 30));
        contentLoaderCombo.setEnabled(false);
        contentLoaderCombo.setVisible(false);
        contentSearchButton = Ui.smallButton("搜索", Ui.AccentButton.Style.SECONDARY);
        contentSearchButton.setPreferredSize(new Dimension(58, 30));
        contentSearchButton.setMaximumSize(new Dimension(58, 30));
        contentSearchButton.addActionListener(e -> searchContent());
        contentRefreshButton = Ui.smallButton("刷新", Ui.AccentButton.Style.SECONDARY);
        contentRefreshButton.setPreferredSize(new Dimension(52, 30));
        contentRefreshButton.setMaximumSize(new Dimension(52, 30));
        contentRefreshButton.setToolTipText("重新搜索当前内容");
        contentRefreshButton.addActionListener(e -> searchContent());
        JPanel searchRow = new JPanel();
        searchRow.setOpaque(false);
        searchRow.setLayout(new BoxLayout(searchRow, BoxLayout.X_AXIS));
        searchRow.add(contentSourceCombo);
        searchRow.add(Ui.hgap(8));
        searchRow.add(contentSearchField);
        searchRow.add(Ui.hgap(8));
        searchRow.add(contentSearchButton);
        searchRow.add(Ui.hgap(6));
        searchRow.add(contentRefreshButton);
        searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JPanel optionsRow = new JPanel();
        optionsRow.setOpaque(false);
        optionsRow.setLayout(new BoxLayout(optionsRow, BoxLayout.X_AXIS));
        optionsRow.add(Ui.label("游戏版本", Theme.TINY, Theme.DIM));
        optionsRow.add(Ui.hgap(6));
        optionsRow.add(contentVersionField);
        optionsRow.add(Ui.hgap(14));
        optionsRow.add(Ui.label("加载器", Theme.TINY, Theme.DIM));
        optionsRow.add(Ui.hgap(6));
        optionsRow.add(contentLoaderCombo);
        optionsRow.add(Ui.hgap(10));
        optionsRow.add(Ui.label("留空版本号时按默认值查询", Theme.MONO, Theme.DIM));
        optionsRow.add(Box.createHorizontalGlue());
        optionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.add(searchRow);
        row.add(Ui.vgap(8));
        row.add(optionsRow);
        searchBar.add(row, BorderLayout.CENTER);
        page.add(searchBar, BorderLayout.NORTH);

        contentModel = new DefaultListModel<>();
        contentList = new JList<>(contentModel);
        contentList.setBackground(Theme.BG);
        contentList.setSelectionBackground(Theme.BG);
        contentList.setSelectionForeground(Theme.TEXT);
        contentList.setFixedCellHeight(64);
        contentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contentList.setCellRenderer(new ContentRenderer());
        contentList.setBorder(new EmptyBorder(4, 8, 8, 8));
        contentList.addListSelectionListener(e -> updateContentSelection());
        contentList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int index = contentList.locationToIndex(e.getPoint());
                contentList.putClientProperty("hoverIndex", index);
                contentList.repaint();
            }
        });
        contentList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                contentList.putClientProperty("hoverIndex", -1);
                contentList.repaint();
            }
        });
        contentList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openProjectDetail();
                }
            }
        });
        contentListFade = new Ui.FadePanel(new BorderLayout());
        contentListFade.add(Ui.scrollDark(contentList), BorderLayout.CENTER);
        page.add(contentListFade, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBackground(Theme.CARD);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(7, 14, 7, 14)));
        contentSelectionLabel = Ui.label("搜索 Modrinth 内容", Theme.MONO, Theme.DIM);
        bottom.add(contentSelectionLabel, BorderLayout.CENTER);
        contentInstallButton = Ui.smallButton("安装所选", Ui.AccentButton.Style.PRIMARY);
        contentInstallButton.setPreferredSize(new Dimension(96, 30));
        contentInstallButton.setMaximumSize(new Dimension(96, 30));
        contentInstallButton.setEnabled(false);
        contentInstallButton.addActionListener(e -> startContentInstall());
        contentDetailButton = Ui.smallButton("版本 / 详情", Ui.AccentButton.Style.SECONDARY);
        contentDetailButton.setPreferredSize(new Dimension(96, 30));
        contentDetailButton.setMaximumSize(new Dimension(96, 30));
        contentDetailButton.setEnabled(false);
        contentDetailButton.addActionListener(e -> openProjectDetail());
        contentMcmodButton = Ui.smallButton("MC百科", Ui.AccentButton.Style.GHOST);
        contentMcmodButton.setPreferredSize(new Dimension(68, 30));
        contentMcmodButton.setMaximumSize(new Dimension(68, 30));
        contentMcmodButton.setVisible(false);
        contentMcmodButton.addActionListener(e -> {
            Modrinth.Project project = contentList.getSelectedValue();
            if (project != null && project.mcmodUrl() != null) {
                openUrl(project.mcmodUrl());
            }
        });
        contentPrevPage = Ui.smallButton("‹", Ui.AccentButton.Style.GHOST);
        contentPrevPage.setPreferredSize(new Dimension(28, 30));
        contentPrevPage.setMaximumSize(new Dimension(28, 30));
        contentPrevPage.addActionListener(e -> {
            if (contentPage > 0) {
                contentPage--;
                runContentSearch();
            }
        });
        contentNextPage = Ui.smallButton("›", Ui.AccentButton.Style.GHOST);
        contentNextPage.setPreferredSize(new Dimension(28, 30));
        contentNextPage.setMaximumSize(new Dimension(28, 30));
        contentNextPage.addActionListener(e -> {
            contentPage++;
            runContentSearch();
        });
        contentPageLabel = Ui.label("1", Theme.MONO, Theme.DIM);
        contentPageLabel.setBorder(new EmptyBorder(0, 5, 0, 5));
        contentPrevPage.setEnabled(false);
        contentNextPage.setEnabled(false);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.add(contentPrevPage);
        actions.add(contentPageLabel);
        actions.add(contentNextPage);
        actions.add(Ui.hgap(10));
        actions.add(contentMcmodButton);
        actions.add(Ui.hgap(8));
        actions.add(contentDetailButton);
        actions.add(Ui.hgap(8));
        actions.add(contentInstallButton);
        bottom.add(actions, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);
        return page;
    }

    private JPanel buildLaunchPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(pageHeader("启动游戏", "确认账户、版本和 Java 后启动", null), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel account = section("账户", "离线账户，仅用于单机 / 局域网");
        JPanel accountRow = new JPanel(new BorderLayout(10, 0));
        accountRow.setOpaque(false);
        launchSkin = new Ui.SkinHead(34);
        accountRow.add(launchSkin, BorderLayout.WEST);
        launchAccount = Ui.label(config.username() + "  ·  离线账户", Theme.BODY_BOLD, Theme.TEXT);
        accountRow.add(launchAccount, BorderLayout.CENTER);
        Ui.AccentButton switchAccount = Ui.smallButton("切换账户", Ui.AccentButton.Style.SECONDARY);
        switchAccount.addActionListener(e -> openAccountsDialog());
        accountRow.add(switchAccount, BorderLayout.EAST);
        accountRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        accountRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        account.add(accountRow);
        form.add(lockedHeight(account));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel version = section("版本", "下载或修复会先补全客户端、运行库和资源");
        launchVersion = Ui.label("未选择版本", Theme.TITLE, Theme.TEXT);
        launchVersion.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.add(launchVersion);
        version.add(Ui.vgap(4));
        launchMeta = new JTextArea(3, 50);
        launchMeta.setEditable(false);
        launchMeta.setFocusable(false);
        launchMeta.setOpaque(false);
        launchMeta.setLineWrap(true);
        launchMeta.setWrapStyleWord(true);
        launchMeta.setFont(Theme.MONO);
        launchMeta.setForeground(Theme.DIM);
        launchMeta.setBorder(new EmptyBorder(0, 0, 0, 0));
        launchMeta.setText("请到「下载中心」选择一个版本");
        launchMeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        launchMeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        version.add(launchMeta);
        form.add(lockedHeight(version));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel details = section("启动参数", "这些值来自「启动器配置」和「设置」");
        launchJava = Ui.label("检测中…", Theme.MONO, Theme.MUTED);
        launchMemory = Ui.label(config.memoryMb() + " MB", Theme.MONO, Theme.MUTED);
        launchJvm = Ui.label(config.jvmArgs().isBlank() ? "无额外参数" : config.jvmArgs(),
                Theme.MONO, Theme.MUTED);
        launchAssets = Ui.label(config.downloadAssets() ? "完整资源" : "仅核心文件",
                Theme.MONO, Theme.MUTED);
        details.add(infoLine("Java", launchJava));
        details.add(Ui.vgap(8));
        details.add(infoLine("内存", launchMemory));
        details.add(Ui.vgap(8));
        details.add(infoLine("资源", launchAssets));
        details.add(Ui.vgap(8));
        details.add(infoLine("JVM", launchJvm));
        form.add(lockedHeight(details));
        form.add(Ui.vgap(30));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        installButton = Ui.button("下载 / 修复", Ui.AccentButton.Style.SECONDARY);
        installButton.addActionListener(e -> startInstall(false));
        logButton = Ui.smallButton("日志", Ui.AccentButton.Style.GHOST);
        logButton.addActionListener(e -> openRecentLog());
        cancelButton = Ui.smallButton("取消", Ui.AccentButton.Style.DANGER);
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> cancelTask());
        launchButton = new Ui.AccentButton("启动游戏", Ui.AccentButton.Style.PRIMARY,
                Theme.H2, new Insets(10, 24, 10, 24));
        launchButton.setPreferredSize(new Dimension(180, 40));
        launchButton.setMaximumSize(new Dimension(180, 40));
        launchButton.addActionListener(e -> startLaunch());
        actions.add(installButton);
        actions.add(Ui.hgap(8));
        actions.add(logButton);
        actions.add(Ui.hgap(8));
        actions.add(cancelButton);
        actions.add(Box.createHorizontalGlue());
        actions.add(launchButton);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        form.add(actions);
        form.add(Ui.vgap(14));
        form.add(sidebarNote("启动前会检查客户端、运行库和资源；缺失时会提示下载 / 修复。"));
        form.add(Box.createVerticalGlue());
        page.add(form, BorderLayout.CENTER);
        updateButtons();
        return page;
    }

    private JPanel infoLine(String key, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        JLabel label = Ui.label(key, Theme.TINY, Theme.DIM);
        label.setPreferredSize(new Dimension(52, 18));
        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return row;
    }

    private void updateLaunchInfo() {
        if (launchVersion == null) {
            return;
        }
        launchVersion.setText(selectedEntry == null ? "未选择版本" : selectedEntry.id());
        updateVersionPicker();
        launchMeta.setText(detailMeta == null ? "" : detailMeta.getText());
        launchAccount.setText(config.username() + "  ·  离线账户");
        launchMemory.setText(config.effectiveMemoryMb() + " MB"
                + (config.autoMemory() ? "（自动）" : ""));
        String jvm = config.jvmArgs().isBlank() ? "" : config.jvmArgs();
        if (config.autoJvmTuning()) {
            jvm = jvm.isBlank() ? "自动优化（G1，启动时验证）"
                    : jvm + "  ·  自动优化";
        }
        launchJvm.setText(jvm.isBlank() ? "无额外参数" : jvm);
        launchAssets.setText(config.downloadAssets() ? "完整资源" : "仅核心文件");
        String java = launchJava == null ? null : launchJava.getText();
        if (java == null || java.isBlank()) {
            launchJava.setText("自动选择");
        }
    }

    private JPanel buildVersionDetailPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        Ui.AccentButton back = Ui.smallButton("返回列表", Ui.AccentButton.Style.GHOST);
        back.addActionListener(e -> showDownloadTab("versions"));
        page.add(pageHeader("安装选项", "先选加载器，再下载原版与加载器", back), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel version = section("版本", "下载客户端、运行库和资源");
        versionDetailTitle = Ui.label("未选择版本", Theme.TITLE, Theme.TEXT);
        versionDetailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.add(versionDetailTitle);
        version.add(Ui.vgap(5));
        versionDetailMeta = new JTextArea(3, 60);
        versionDetailMeta.setEditable(false);
        versionDetailMeta.setFocusable(false);
        versionDetailMeta.setOpaque(false);
        versionDetailMeta.setLineWrap(true);
        versionDetailMeta.setWrapStyleWord(true);
        versionDetailMeta.setFont(Theme.MONO);
        versionDetailMeta.setForeground(Theme.DIM);
        versionDetailMeta.setBorder(new EmptyBorder(0, 0, 0, 0));
        versionDetailMeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        versionDetailMeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        version.add(versionDetailMeta);
        form.add(lockedHeight(version));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel loader = section("模组启动器", "原版不需要加载器；Fabric / Quilt 可留空版本自动取最新");
        JPanel loaderChoices = new JPanel();
        loaderChoices.setOpaque(false);
        loaderChoices.setLayout(new BoxLayout(loaderChoices, BoxLayout.X_AXIS));
        ButtonGroup loaders = new ButtonGroup();
        for (String[] choice : new String[][]{{"原版", "vanilla"}, {"Fabric", "fabric"},
                {"Quilt", "quilt"}, {"Forge", "forge"}, {"NeoForge", "neoforge"}}) {
            Ui.SegmentButton button = new Ui.SegmentButton(choice[0]);
            button.setSelected("vanilla".equals(choice[1]));
            button.addActionListener(e -> {
                versionLoaderType = choice[1];
                if (versionInstallButton != null) {
                    versionInstallButton.setText("vanilla".equals(choice[1])
                            ? "下载原版" : "安装 " + choice[0] + " 并启动");
                }
            });
            loaders.add(button);
            loaderChoices.add(button);
        }
        loaderChoices.setAlignmentX(Component.LEFT_ALIGNMENT);
        loaderChoices.setMaximumSize(new Dimension(720, 28));
        loader.add(loaderChoices);
        loader.add(Ui.vgap(10));
        versionLoaderVersion = Ui.field("留空 = 最新（Fabric/Quilt）");
        versionLoaderVersion.setPreferredSize(new Dimension(300, 32));
        versionLoaderVersion.setMaximumSize(new Dimension(300, 32));
        versionLoaderVersion.setAlignmentX(Component.LEFT_ALIGNMENT);
        loader.add(versionLoaderVersion);
        loader.add(Ui.vgap(7));
        loader.add(sidebarNote("Forge / NeoForge 需要填写安装器版本，例如 47.2.0 / 20.4.237。"
                + "安装完成后会生成一个本地实例。"));
        form.add(lockedHeight(loader));
        form.add(Ui.vgap(30));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        versionInstallButton = Ui.button("下载原版", Ui.AccentButton.Style.PRIMARY);
        versionInstallButton.addActionListener(e -> installFromVersionDetail());
        actions.add(versionInstallButton);
        actions.add(Box.createHorizontalGlue());
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(actions);
        form.add(Box.createVerticalGlue());
        page.add(form, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildProjectDetailPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        Ui.AccentButton back = Ui.smallButton("返回内容", Ui.AccentButton.Style.GHOST);
        back.addActionListener(e -> {
            currentDownloadCard = "content";
            downloadCardsLayout.show(downloadCards, "content");
            updateBackButton();
            downloadSubtabs.setVisible(true);
            downloadTitle.setText(switch (contentType) {
                case "mod" -> "模组下载";
                case "resourcepack" -> "资源包";
                case "shader" -> "光影下载";
                default -> "整合包";
            });
            downloadSubtitle.setText("来自 Modrinth · 自动补齐必需前置 · SHA-1 校验");
        });
        page.add(pageHeader("文件版本", "选择具体文件版本后安装", back), BorderLayout.NORTH);

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBackground(Theme.CARD);
        top.setBorder(new EmptyBorder(12, 14, 12, 14));
        projectDetailIcon = new Ui.ProjectIcon(64);
        top.add(projectDetailIcon, BorderLayout.WEST);
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        projectDetailTitle = Ui.label("", Theme.H1, Theme.TEXT);
        projectDetailMeta = Ui.label("", Theme.MONO, Theme.DIM);
        projectDetailSummary = new JLabel();
        projectDetailSummary.setFont(Theme.TINY);
        projectDetailSummary.setForeground(Theme.MUTED);
        projectDetailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        projectDetailMeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        projectDetailSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(projectDetailTitle);
        info.add(Ui.vgap(2));
        info.add(projectDetailMeta);
        info.add(Ui.vgap(3));
        info.add(projectDetailSummary);
        top.add(info, BorderLayout.CENTER);
        Ui.AccentButton mcmod = Ui.smallButton("MC百科", Ui.AccentButton.Style.LINK);
        mcmod.addActionListener(e -> {
            if (detailProject != null && detailProject.mcmodUrl() != null) {
                openUrl(detailProject.mcmodUrl());
            }
        });
        top.add(mcmod, BorderLayout.EAST);
        page.add(top, BorderLayout.NORTH);

        projectVersionModel = new DefaultListModel<>();
        projectVersionList = new JList<>(projectVersionModel);
        projectVersionList.setBackground(Theme.BG);
        projectVersionList.setSelectionBackground(Theme.BG);
        projectVersionList.setFixedCellHeight(50);
        projectVersionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectVersionList.setCellRenderer(new ProjectVersionRenderer());
        projectVersionList.setBorder(new EmptyBorder(4, 8, 8, 8));
        projectVersionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && projectInstallButton != null) {
                projectInstallButton.setEnabled(projectVersionList.getSelectedValue() != null && !busy);
            }
        });
        projectVersionList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                projectVersionList.putClientProperty("hoverIndex",
                        projectVersionList.locationToIndex(e.getPoint()));
                projectVersionList.repaint();
            }
        });
        projectVersionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                projectVersionList.putClientProperty("hoverIndex", -1);
                projectVersionList.repaint();
            }
        });
        page.add(Ui.scrollDark(projectVersionList), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBackground(Theme.CARD);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(7, 14, 7, 14)));
        JLabel hint = Ui.label("选择一个文件版本", Theme.MONO, Theme.DIM);
        bottom.add(hint, BorderLayout.CENTER);
        JPanel projectActions = new JPanel();
        projectActions.setOpaque(false);
        projectActions.setLayout(new BoxLayout(projectActions, BoxLayout.X_AXIS));
        projectRefreshButton = Ui.smallButton("刷新版本", Ui.AccentButton.Style.GHOST);
        projectRefreshButton.addActionListener(e -> loadProjectVersions());
        projectInstallButton = Ui.smallButton("安装此版本", Ui.AccentButton.Style.PRIMARY);
        projectInstallButton.setEnabled(false);
        projectInstallButton.addActionListener(e -> installProjectVersion());
        projectActions.add(projectRefreshButton);
        projectActions.add(Ui.hgap(6));
        projectActions.add(projectInstallButton);
        bottom.add(projectActions, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);
        return page;
    }

    private JPanel buildInstancesPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        Ui.AccentButton refresh = Ui.smallButton("刷新", Ui.AccentButton.Style.SECONDARY);
        refresh.addActionListener(e -> {
            refreshAllVersions();
            refreshInstalledCache();
            refreshInstancesPage();
        });
        JPanel headerActions = new JPanel();
        headerActions.setOpaque(false);
        headerActions.setLayout(new BoxLayout(headerActions, BoxLayout.X_AXIS));
        instanceLaunchButton = Ui.smallButton("启动所选实例", Ui.AccentButton.Style.PRIMARY);
        instanceLaunchButton.addActionListener(e -> {
            VersionManifest.VersionEntry entry = instancesList == null
                    ? null : instancesList.getSelectedValue();
            if (entry != null) {
                useVersion(entry, true);
            }
        });
        headerActions.add(instanceLaunchButton);
        headerActions.add(Ui.hgap(8));
        headerActions.add(refresh);
        page.add(pageHeader("版本列表", "查看并启动游戏文件夹中的版本、模组和资源包", headerActions),
                BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 16, 16, 16));

        instancesListModel = new DefaultListModel<>();
        instancesList = new JList<>(instancesListModel);
        instancesList.setBackground(Theme.BG);
        instancesList.setSelectionBackground(Theme.BG);
        instancesList.setFixedCellHeight(62);
        instancesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        instancesList.setCellRenderer(new InstanceRenderer());
        instancesList.setBorder(new EmptyBorder(2, 2, 2, 2));
        instancesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateInstanceDetail(instancesList.getSelectedValue());
            }
        });
        instancesList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                instancesList.putClientProperty("hoverIndex",
                        instancesList.locationToIndex(e.getPoint()));
                instancesList.repaint();
            }
        });
        instancesList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                instancesList.putClientProperty("hoverIndex", -1);
                instancesList.repaint();
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(342, 0));
        left.setMinimumSize(new Dimension(300, 0));
        left.add(Ui.scrollDark(instancesList), BorderLayout.CENTER);
        body.add(left, BorderLayout.WEST);

        JPanel detail = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER, 16);
        detail.setLayout(new BorderLayout(0, 10));
        detail.setBorder(new EmptyBorder(16, 18, 16, 18));
        JPanel detailHead = new JPanel();
        detailHead.setOpaque(false);
        detailHead.setLayout(new BoxLayout(detailHead, BoxLayout.Y_AXIS));
        instancesDetailTitle = Ui.label("选择一个版本", Theme.H2, Theme.TEXT);
        instancesDetailMeta = Ui.label("左侧列表只读查看", Theme.MONO, Theme.DIM);
        instancesDetailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        instancesDetailMeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailHead.add(instancesDetailTitle);
        detailHead.add(Ui.vgap(3));
        detailHead.add(instancesDetailMeta);
        detail.add(detailHead, BorderLayout.NORTH);

        JPanel lists = new JPanel(new java.awt.GridLayout(2, 1, 0, 10));
        lists.setOpaque(false);
        lists.add(buildInstanceFileSection(true));
        lists.add(buildInstanceFileSection(false));
        detail.add(lists, BorderLayout.CENTER);
        body.add(detail, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildInstanceFileSection(boolean mods) {
        JPanel box = new Ui.RoundedPanel(Theme.BG, Theme.BORDER, 12);
        box.setLayout(new BorderLayout(0, 4));
        box.setBorder(new EmptyBorder(8, 10, 8, 10));
        JLabel header = Ui.label(mods ? "模组 · 0" : "资源包 · 0", Theme.TINY, Theme.DIM);
        DefaultListModel<String> model = new DefaultListModel<>();
        if (mods) {
            instanceModsHeader = header;
            instanceModsModel = model;
        } else {
            instancePacksHeader = header;
            instancePacksModel = model;
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

    private void refreshInstancesPage() {
        if (instancesListModel == null) {
            return;
        }
        instanceCounts.clear();
        List<VersionManifest.VersionEntry> entries = new ArrayList<>(allVersions);
        if (entries.isEmpty()) {
            entries.addAll(LocalVersions.scan(gamePaths));
        }
        entries.removeIf(entry -> !entry.local() && !installedCache.contains(entry.id()));
        entries.sort((a, b) -> Boolean.compare(b.local(), a.local()));
        VersionManifest.VersionEntry previous = instancesList.getSelectedValue();
        DefaultListModel<VersionManifest.VersionEntry> nextModel = new DefaultListModel<>();
        for (VersionManifest.VersionEntry entry : entries) {
            nextModel.addElement(entry);
        }
        instancesListModel = nextModel;
        instancesList.setModel(nextModel);
        if (!instancesListModel.isEmpty()) {
            int target = 0;
            if (previous != null) {
                for (int i = 0; i < instancesListModel.size(); i++) {
                    if (instancesListModel.get(i).id().equals(previous.id())) {
                        target = i;
                        break;
                    }
                }
            }
            instancesList.setSelectedIndex(target);
            updateInstanceDetail(instancesListModel.get(target));
        } else {
            instancesDetailTitle.setText("没有已安装的实例");
            instancesDetailMeta.setText("未安装的版本不会显示在这里");
            instanceModsHeader.setText("模组 · 0");
            instancePacksHeader.setText("资源包 · 0");
        }
    }

    private void updateInstanceDetail(VersionManifest.VersionEntry entry) {
        if (instanceModsModel == null || instancePacksModel == null) {
            return;
        }
        instanceModsModel.clear();
        instancePacksModel.clear();
        if (entry == null) {
            instancesDetailTitle.setText("选择一个版本");
            instancesDetailMeta.setText("左侧列表只读查看");
            instanceModsHeader.setText("模组 · 0");
            instancePacksHeader.setText("资源包 · 0");
            return;
        }
        instancesDetailTitle.setText(entry.id());
        boolean installed = Files.isRegularFile(gamePaths.versionJson(entry.id()));
        instancesDetailMeta.setText(entry.displayType() + "  ·  "
                + Formatting.date(entry.releaseTime())
                + (entry.local() ? "  ·  本地实例" : installed ? "  ·  已安装" : "  ·  未安装"));
        Path content = config.isolateVersions()
                ? config.effectiveGameDir(entry.id()) : config.gameDir();
        List<Path> mods = listFiles(content.resolve("mods"));
        List<Path> packs = listFiles(content.resolve("resourcepacks"));
        for (Path file : mods) {
            instanceModsModel.addElement(file.getFileName().toString());
        }
        for (Path file : packs) {
            instancePacksModel.addElement(file.getFileName().toString());
        }
        instanceModsHeader.setText("模组 · " + mods.size());
        instancePacksHeader.setText("资源包 · " + packs.size());
    }

    private int[] instanceCounts(VersionManifest.VersionEntry entry) {
        return instanceCounts.computeIfAbsent(entry.id(), key -> {
            Path content = config.isolateVersions()
                    ? config.effectiveGameDir(entry.id()) : config.gameDir();
            return new int[]{listFiles(content.resolve("mods")).size(),
                    listFiles(content.resolve("resourcepacks")).size()};
        });
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
            int[] counts = instanceCounts(entry);
            content.setText(counts[0] + " 个模组  ·  " + counts[1] + " 个资源包");
            local.setVisible(entry.local());
            Path content = config.isolateVersions()
                    ? config.effectiveGameDir(entry.id()) : config.gameDir();
            icon.setInstance(entry.id(), gamePaths.versionJson(entry.id()), content);
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

    private static List<Path> listFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private JPanel buildConfigPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(pageHeader("启动器配置", "账户、游戏目录和 Java 运行时", null), BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(buildConfigForm());
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildConfigForm() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel account = section("账户", "离线账户只用于单机 / 局域网，正版服务器仍需正版登录");
        JPanel accountRow = new JPanel(new BorderLayout(10, 0));
        accountRow.setOpaque(false);
        configSkin = new Ui.SkinHead(36);
        accountRow.add(configSkin, BorderLayout.WEST);
        JPanel accountText = new JPanel();
        accountText.setOpaque(false);
        accountText.setLayout(new BoxLayout(accountText, BoxLayout.Y_AXIS));
        configAccountLabel = Ui.label(config.username(), Theme.BODY_BOLD, Theme.TEXT);
        JLabel accountType = Ui.label("离线账户", Theme.TINY, Theme.DIM);
        accountText.add(configAccountLabel);
        accountText.add(Ui.vgap(1));
        accountText.add(accountType);
        accountRow.add(accountText, BorderLayout.CENTER);
        Ui.AccentButton manage = Ui.smallButton("登录 / 账户管理", Ui.AccentButton.Style.SECONDARY);
        manage.addActionListener(e -> openAccountsDialog());
        accountRow.add(manage, BorderLayout.EAST);
        accountRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        accountRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        account.add(accountRow);
        account.add(Ui.vgap(7));
        account.add(sidebarNote("账户保存在 " + config.accountsDir()
                + "，可在账户管理里修改保存目录；创建离线账户不需要密码。"));
        form.add(lockedHeight(account));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel directory = section("游戏目录", "版本、存档、资源和日志都会放在这个目录");
        JPanel dirRow = new JPanel(new BorderLayout(6, 0));
        dirRow.setOpaque(false);
        gameDirField = Ui.field(config.gameDir().toString(), "游戏目录");
        gameDirField.setCaretPosition(0);
        gameDirField.setToolTipText("Minecraft 的 .minecraft 目录。");
        gameDirField.addActionListener(e -> applyGameDirFromField());
        gameDirField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyGameDirFromField();
            }
        });
        dirRow.add(gameDirField, BorderLayout.CENTER);
        Ui.AccentButton browseDir = Ui.smallButton("选择", Ui.AccentButton.Style.SECONDARY);
        browseDir.setPreferredSize(new Dimension(62, 32));
        browseDir.setMaximumSize(new Dimension(62, 32));
        browseDir.addActionListener(e -> chooseGameDirectory());
        dirRow.add(browseDir, BorderLayout.EAST);
        dirRow.setMaximumSize(new Dimension(720, 32));
        dirRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        directory.add(dirRow);
        directory.add(Ui.vgap(7));
        JPanel dirLinks = new JPanel();
        dirLinks.setOpaque(false);
        dirLinks.setLayout(new BoxLayout(dirLinks, BoxLayout.X_AXIS));
        openDirButton = Ui.smallButton("打开游戏目录", Ui.AccentButton.Style.LINK);
        openDirButton.addActionListener(e -> openGameDirectory());
        dirLinks.add(openDirButton);
        dirLinks.add(Ui.hgap(16));
        Ui.AccentButton importInstance = Ui.smallButton("导入已有实例", Ui.AccentButton.Style.LINK);
        importInstance.addActionListener(e -> chooseInstanceFolder());
        dirLinks.add(importInstance);
        dirLinks.add(Ui.hgap(16));
        Ui.AccentButton importHmcl = Ui.smallButton("读取 HMCL 目录", Ui.AccentButton.Style.LINK);
        importHmcl.addActionListener(e -> importHmclDirectories());
        dirLinks.add(importHmcl);
        dirLinks.add(Box.createHorizontalGlue());
        dirLinks.setAlignmentX(Component.LEFT_ALIGNMENT);
        directory.add(dirLinks);
        directory.add(Ui.vgap(10));
        Ui.ToggleChip isolate = new Ui.ToggleChip("版本隔离（每个版本独立存档 / mods / 配置）");
        isolate.setSelected(config.isolateVersions());
        isolate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        isolate.setAlignmentX(Component.LEFT_ALIGNMENT);
        isolate.addActionListener(e -> {
            config.isolateVersions(isolate.isSelected());
            config.save();
        });
        directory.add(isolate);
        directory.add(Ui.vgap(6));
        directory.add(sidebarNote("开启后游戏目录使用 versions/<版本>/ 作为实例目录；"
                + "关闭时所有版本共用同一个 .minecraft。"));
        form.add(lockedHeight(directory));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel java = section("Java 运行时", "自动选择会优先匹配该版本需要的 Java 大版本");
        JPanel javaRow = new JPanel(new BorderLayout(6, 0));
        javaRow.setOpaque(false);
        javaCombo.setPreferredSize(new Dimension(100, 32));
        javaCombo.setEnabled(true);
        javaCombo.setOpaque(true);
        javaCombo.setBackground(Theme.CARD_ALT);
        javaCombo.addActionListener(e -> {
            if (refreshingJavaCombo) {
                return;
            }
            JavaChoice choice = (JavaChoice) javaCombo.getSelectedItem();
            config.javaPath(choice == null || choice.path() == null ? "" : choice.path().toString());
            config.save();
            updateJavaHint();
        });
        javaRow.add(javaCombo, BorderLayout.CENTER);
        JPanel javaButtons = new JPanel();
        javaButtons.setOpaque(false);
        javaButtons.setLayout(new BoxLayout(javaButtons, BoxLayout.X_AXIS));
        Ui.AccentButton manageJava = Ui.smallButton("管理列表", Ui.AccentButton.Style.SECONDARY);
        manageJava.setPreferredSize(new Dimension(78, 32));
        manageJava.setMaximumSize(new Dimension(78, 32));
        manageJava.addActionListener(e -> openJavaManager());
        Ui.AccentButton browseJava = Ui.smallButton("选择", Ui.AccentButton.Style.SECONDARY);
        browseJava.setPreferredSize(new Dimension(62, 32));
        browseJava.setMaximumSize(new Dimension(62, 32));
        browseJava.addActionListener(e -> chooseJava());
        javaButtons.add(manageJava);
        javaButtons.add(Ui.hgap(6));
        javaButtons.add(browseJava);
        javaRow.add(javaButtons, BorderLayout.EAST);
        javaRow.setMaximumSize(new Dimension(720, 32));
        javaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        java.add(javaRow);
        java.add(Ui.vgap(6));
        javaHint = new JTextArea(2, 40);
        javaHint.setEditable(false);
        javaHint.setFocusable(false);
        javaHint.setOpaque(false);
        javaHint.setLineWrap(true);
        javaHint.setWrapStyleWord(true);
        javaHint.setFont(Theme.TINY);
        javaHint.setForeground(Theme.DIM);
        javaHint.setBorder(new EmptyBorder(0, 0, 0, 0));
        javaHint.setText("正在检测 Java…");
        javaHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        javaHint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        java.add(javaHint);
        java.add(Ui.vgap(8));
        autoJavaToggle = new Ui.ToggleChip("缺少 Java 时自动从 Mojang 下载");
        autoJavaToggle.setSelected(config.autoDownloadJava());
        autoJavaToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        autoJavaToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoJavaToggle.addActionListener(e -> {
            config.autoDownloadJava(autoJavaToggle.isSelected());
            config.save();
        });
        java.add(autoJavaToggle);
        form.add(lockedHeight(java));
        form.add(Box.createVerticalGlue());
        return form;
    }

    private JPanel buildSettingsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        Ui.AccentButton openData = Ui.smallButton("打开数据目录", Ui.AccentButton.Style.GHOST);
        openData.addActionListener(e -> openConfigDirectory());
        Ui.AccentButton openLogs = Ui.smallButton("打开日志目录", Ui.AccentButton.Style.GHOST);
        openLogs.addActionListener(e -> openLogsDirectory());
        JPanel settingsActions = new JPanel();
        settingsActions.setOpaque(false);
        settingsActions.setLayout(new BoxLayout(settingsActions, BoxLayout.X_AXIS));
        settingsActions.add(openLogs);
        settingsActions.add(Ui.hgap(6));
        settingsActions.add(openData);
        page.add(pageHeader("设置", "性能、窗口和下载选项会自动保存", settingsActions), BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(buildSettingsForm());
        page.add(scroll, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> {
            scroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
            Timer settle = new Timer(120, e -> {
                scroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
                scroll.getVerticalScrollBar().setValue(0);
            });
            settle.setRepeats(false);
            settle.start();
        });
        return page;
    }

    private JPanel buildSettingsForm() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel performance = section("性能", "修改后会在下一次启动游戏时生效");
        JPanel memoryRow = new JPanel();
        memoryRow.setOpaque(false);
        memoryRow.setLayout(new BoxLayout(memoryRow, BoxLayout.X_AXIS));
        memoryRow.add(Ui.label("内存分配", Theme.TINY, Theme.DIM));
        memoryRow.add(Ui.hgap(12));
        memorySlider = new Ui.ValueSlider(512, 32768, 512, config.memoryMb(), " MB");
        memorySlider.addChangeListener(value -> {
            config.memoryMb(value);
            config.save();
            updateLaunchInfo();
        });
        memoryRow.add(memorySlider);
        memoryRow.add(Box.createHorizontalGlue());
        memoryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        memorySlider.setEnabled(!config.autoMemory());
        if (config.autoMemory()) {
            memorySlider.setValue(config.effectiveMemoryMb());
        }
        memoryRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        performance.add(memoryRow);
        performance.add(Ui.vgap(8));
        autoMemoryToggle = new Ui.ToggleChip("自动分配内存（推荐 "
                + config.effectiveMemoryMb() + " MB）");
        autoMemoryToggle.setSelected(config.autoMemory());
        autoMemoryToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        autoMemoryToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoMemoryToggle.addActionListener(e -> {
            config.autoMemory(autoMemoryToggle.isSelected());
            config.save();
            autoMemoryToggle.setText("自动分配内存（推荐 "
                    + config.effectiveMemoryMb() + " MB）");
            memorySlider.setEnabled(!config.autoMemory());
            if (config.autoMemory()) {
                memorySlider.setValue(config.effectiveMemoryMb());
            }
            updateLaunchInfo();
        });
        performance.add(autoMemoryToggle);
        performance.add(Ui.vgap(8));
        autoJvmToggle = new Ui.ToggleChip("自动 JVM 优化参数（G1，启动时验证）");
        autoJvmToggle.setSelected(config.autoJvmTuning());
        autoJvmToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        autoJvmToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoJvmToggle.addActionListener(e -> {
            config.autoJvmTuning(autoJvmToggle.isSelected());
            config.save();
            updateLaunchInfo();
        });
        performance.add(autoJvmToggle);
        performance.add(Ui.vgap(10));

        JLabel jvmLabel = Ui.label("额外 JVM 参数", Theme.TINY, Theme.DIM);
        jvmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        performance.add(jvmLabel);
        performance.add(Ui.vgap(4));
        jvmArgsField = Ui.field(config.jvmArgs(), "例如 -XX:+UseG1GC -Dfile.encoding=UTF-8");
        jvmArgsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        jvmArgsField.setPreferredSize(new Dimension(720, 32));
        jvmArgsField.setAlignmentX(Component.LEFT_ALIGNMENT);
        jvmArgsField.addActionListener(e -> saveJvmArgs());
        jvmArgsField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveJvmArgs();
            }
        });
        performance.add(jvmArgsField);
        performance.add(Ui.vgap(6));
        performance.add(sidebarNote("按空格分隔；引号可用于包含空格的参数。不要把 access token 等敏感信息写在这里。"));
        form.add(lockedHeight(performance));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel window = section("窗口", "首次启动使用该尺寸；游戏内设置可能覆盖它");
        JPanel windowRow = new JPanel();
        windowRow.setOpaque(false);
        windowRow.setLayout(new BoxLayout(windowRow, BoxLayout.X_AXIS));
        windowRow.add(Ui.label("窗口尺寸", Theme.TINY, Theme.DIM));
        windowRow.add(Ui.hgap(12));
        widthSlider = new Ui.ValueSlider(320, 3840, 10, config.windowWidth(), " px");
        widthSlider.setPreferredSize(new Dimension(205, 30));
        widthSlider.setMaximumSize(new Dimension(205, 30));
        heightSlider = new Ui.ValueSlider(240, 2160, 10, config.windowHeight(), " px");
        heightSlider.setPreferredSize(new Dimension(205, 30));
        heightSlider.setMaximumSize(new Dimension(205, 30));
        widthSlider.addChangeListener(value -> {
            config.windowWidth(value);
            config.save();
        });
        heightSlider.addChangeListener(value -> {
            config.windowHeight(value);
            config.save();
        });
        windowRow.add(widthSlider);
        windowRow.add(Ui.hgap(10));
        windowRow.add(Ui.label("×", Theme.BODY, Theme.DIM));
        windowRow.add(Ui.hgap(10));
        windowRow.add(heightSlider);
        windowRow.add(Box.createHorizontalGlue());
        windowRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        windowRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        window.add(windowRow);
        form.add(lockedHeight(window));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel download = section("下载", "完整资源包含声音、语言和图标");
        assetsToggle = new Ui.ToggleChip("下载完整资源");
        assetsToggle.setSelected(config.downloadAssets());
        assetsToggle.setToolTipText("首次下载约 300–500 MB；关闭后现代版本可能无法启动。");
        assetsToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        assetsToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        assetsToggle.addActionListener(e -> {
            config.downloadAssets(assetsToggle.isSelected());
            config.save();
            updateDetailForSelection();
            updateLaunchInfo();
        });
        download.add(assetsToggle);
        download.add(Ui.vgap(10));
        mirrorToggle = new Ui.ToggleChip("自动测试官方源 / 镜像源");
        mirrorToggle.setSelected(config.autoMirror());
        mirrorToggle.setToolTipText("同时探测 Mojang 官方源和 BMCLAPI 镜像，选择更快的那个。");
        mirrorToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        mirrorToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        mirrorToggle.addActionListener(e -> {
            config.autoMirror(mirrorToggle.isSelected());
            config.save();
        });
        download.add(mirrorToggle);
        download.add(Ui.vgap(8));
        forceTranslationToggle = new Ui.ToggleChip("强制显示中文模组 / 整合包名称（MC百科）");
        forceTranslationToggle.setSelected(config.forceModTranslation());
        forceTranslationToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        forceTranslationToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        forceTranslationToggle.addActionListener(e -> {
            config.forceModTranslation(forceTranslationToggle.isSelected());
            config.save();
            if (contentList != null) {
                contentList.repaint();
            }
        });
        download.add(forceTranslationToggle);
        download.add(Ui.vgap(12));
        JLabel keyLabel = Ui.label("CurseForge API Key", Theme.TINY, Theme.DIM);
        keyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        download.add(keyLabel);
        download.add(Ui.vgap(4));
        curseForgeKeyField = Ui.field(config.curseForgeApiKey(),
                "在 CurseForge 控制台申请的个人 API Key");
        curseForgeKeyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        curseForgeKeyField.setPreferredSize(new Dimension(720, 32));
        curseForgeKeyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        curseForgeKeyField.addActionListener(e -> saveCurseForgeKey());
        curseForgeKeyField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveCurseForgeKey();
            }
        });
        download.add(curseForgeKeyField);
        download.add(Ui.vgap(6));
        download.add(sidebarNote("CurseForge 官方 API 需要个人 Key；Modrinth 不需要 Key。"
                + "HMCL 内置了它自己申请的 Key，本项目不能复用。"
                + "资源对象在多个版本之间共享；删除后可通过「修复 / 更新」补全。"));
        form.add(lockedHeight(download));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel style = section("界面样式", "支持简单 CSS：background / color / card / accent / border / font-size");
        JLabel presetLabel = Ui.label("样式样例（横向滚动）", Theme.TINY, Theme.DIM);
        presetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        style.add(presetLabel);
        style.add(Ui.vgap(6));
        style.add(buildStyleStrip());
        style.add(Ui.vgap(10));
        JPanel fontRow = new JPanel();
        fontRow.setOpaque(false);
        fontRow.setLayout(new BoxLayout(fontRow, BoxLayout.X_AXIS));
        fontRow.add(Ui.label("字体", Theme.TINY, Theme.DIM));
        fontRow.add(Ui.hgap(8));
        fontCombo = new Ui.DarkCombo<>();
        java.util.List<FontCatalog.Choice> fontChoices = FontCatalog.choices();
        FontCatalog.Choice selectedFont = fontChoices.get(0);
        for (FontCatalog.Choice choice : fontChoices) {
            fontCombo.addItem(choice);
            if (!config.uiFontFamily().isBlank()
                    && config.uiFontFamily().equals(choice.family())) {
                selectedFont = choice;
            }
        }
        fontCombo.setSelectedItem(selectedFont);
        fontCombo.setPreferredSize(new Dimension(280, 32));
        fontCombo.setMaximumSize(new Dimension(280, 32));
        fontRow.add(fontCombo);
        fontRow.add(Ui.hgap(8));
        Ui.AccentButton applyFont = Ui.smallButton("应用字体", Ui.AccentButton.Style.SECONDARY);
        applyFont.addActionListener(e -> applyUiFont());
        fontRow.add(applyFont);
        fontRow.add(Box.createHorizontalGlue());
        fontRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fontRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        style.add(fontRow);
        style.add(Ui.vgap(8));
        JLabel cssHint = Ui.label("修改后点「应用界面样式」，界面会立即重建。字体大小也在这里配置。",
                Theme.TINY, Theme.DIM);
        cssHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        style.add(cssHint);
        style.add(Ui.vgap(6));
        uiCssArea = new JTextArea(loadUiCssText());
        uiCssArea.setFont(Theme.MONO);
        uiCssArea.setBackground(Theme.FIELD);
        uiCssArea.setForeground(Theme.TEXT);
        uiCssArea.setCaretColor(Theme.ACCENT);
        uiCssArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        uiCssArea.setLineWrap(true);
        uiCssArea.setWrapStyleWord(true);
        JScrollPane cssScroll = Ui.scrollDark(uiCssArea);
        cssScroll.setPreferredSize(new Dimension(720, 170));
        cssScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        cssScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        style.add(cssScroll);
        style.add(Ui.vgap(8));
        JPanel cssActions = new JPanel();
        cssActions.setOpaque(false);
        cssActions.setLayout(new BoxLayout(cssActions, BoxLayout.X_AXIS));
        Ui.AccentButton applyCss = Ui.smallButton("应用界面样式", Ui.AccentButton.Style.SECONDARY);
        applyCss.addActionListener(e -> applyUiCss());
        Ui.AccentButton resetCss = Ui.smallButton("恢复默认样式", Ui.AccentButton.Style.GHOST);
        resetCss.addActionListener(e -> {
            uiCssArea.setText(Theme.defaultCss());
            applyUiCss();
        });
        cssActions.add(applyCss);
        cssActions.add(Ui.hgap(8));
        cssActions.add(resetCss);
        cssActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        cssActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        style.add(cssActions);
        form.add(lockedHeight(style));
        form.add(Ui.vgap(30));
        form.add(divider());
        form.add(Ui.vgap(30));

        JPanel data = section("数据目录", "启动器配置、缓存、账户和官方 Java 运行时");
        JLabel dataPath = Ui.label(config.appHome().toString(), Theme.MONO, Theme.MUTED);
        dataPath.setAlignmentX(Component.LEFT_ALIGNMENT);
        data.add(dataPath);
        data.add(Ui.vgap(12));
        JPanel accountsRow = new JPanel(new BorderLayout(8, 0));
        accountsRow.setOpaque(false);
        accountsDirLabel = Ui.label(config.accountsDir().toString(), Theme.MONO, Theme.MUTED);
        accountsDirLabel.setToolTipText(config.accountsDir().toString());
        accountsRow.add(accountsDirLabel, BorderLayout.CENTER);
        Ui.AccentButton changeAccounts = Ui.smallButton("修改账户目录",
                Ui.AccentButton.Style.GHOST);
        changeAccounts.addActionListener(e -> chooseAccountsDirectory());
        accountsRow.add(changeAccounts, BorderLayout.EAST);
        accountsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        accountsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        data.add(accountsRow);
        data.add(Ui.vgap(5));
        data.add(sidebarNote("游戏文件不会放在这里，而是放在「启动器配置」中选择的游戏目录。"));
        form.add(lockedHeight(data));
        form.add(Box.createVerticalGlue());
        return form;
    }

    private JScrollPane buildStyleStrip() {
        JPanel strip = new JPanel();
        strip.setOpaque(false);
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setBorder(new EmptyBorder(2, 0, 6, 0));
        for (ThemePreset preset : ThemePreset.ALL) {
            JPanel tile = new JPanel(new BorderLayout(5, 2));
            tile.setOpaque(false);
            tile.setPreferredSize(new Dimension(126, 62));
            tile.setMinimumSize(new Dimension(126, 62));
            tile.setMaximumSize(new Dimension(126, 62));
            tile.setBorder(new EmptyBorder(8, 9, 7, 9));
            tile.setToolTipText("应用样式：" + preset.name());
            JLabel name = new JLabel(preset.name());
            name.setFont(Theme.TINY);
            name.setForeground(preset.text());
            JPanel dots = new JPanel();
            dots.setOpaque(false);
            dots.setLayout(new BoxLayout(dots, BoxLayout.X_AXIS));
            for (Color color : new Color[]{preset.background(), preset.accent(), preset.card()}) {
                JPanel dot = new JPanel();
                dot.setOpaque(true);
                dot.setBackground(color);
                dot.setPreferredSize(new Dimension(12, 12));
                dot.setMaximumSize(new Dimension(12, 12));
                dot.setBorder(BorderFactory.createLineBorder(preset.text(), 1));
                dots.add(dot);
                dots.add(Ui.hgap(4));
            }
            tile.add(name, BorderLayout.NORTH);
            tile.add(dots, BorderLayout.SOUTH);
            tile.putClientProperty("preset", preset);
            java.awt.event.MouseAdapter click = new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (uiCssArea != null) {
                        uiCssArea.setText(preset.css());
                        applyUiCss();
                    }
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    tile.repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    tile.repaint();
                }
            };
            tile.addMouseListener(click);
            name.addMouseListener(click);
            dots.addMouseListener(click);
            final ThemePreset painted = preset;
            JPanel card = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Ui.prepare(g);
                    g2.setColor(painted.background());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(painted.card());
                    g2.fillRoundRect(4, 4, getWidth() - 8, getHeight() - 8, 8, 8);
                    g2.setColor(painted.accent());
                    g2.fillRoundRect(4, getHeight() - 8, getWidth() - 8, 4, 2, 2);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            card.setOpaque(false);
            card.setPreferredSize(new Dimension(126, 62));
            card.setMaximumSize(new Dimension(126, 62));
            card.setBorder(new EmptyBorder(1, 1, 1, 1));
            card.add(tile);
            card.addMouseListener(click);
            strip.add(card);
            strip.add(Ui.hgap(8));
        }
        JScrollPane scroll = new JScrollPane(strip, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.setOpaque(true);
        scroll.setBackground(Theme.BG);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getHorizontalScrollBar().setUI(new Ui.ThinScrollBarUI());
        scroll.getHorizontalScrollBar().setOpaque(true);
        scroll.getHorizontalScrollBar().setBackground(Theme.BG);
        scroll.getHorizontalScrollBar().setUnitIncrement(32);
        scroll.setPreferredSize(new Dimension(720, 84));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        return scroll;
    }

    private String loadUiCssText() {
        try {
            Path file = config.uiCssFile();
            if (Files.isRegularFile(file)) {
                return Files.readString(file);
            }
        } catch (IOException ex) {
            Log.warn("读取界面样式失败: " + ex.getMessage());
        }
        return Theme.defaultCss();
    }

    private void applyUiFont() {
        Object selected = fontCombo == null ? null : fontCombo.getSelectedItem();
        String family = selected instanceof FontCatalog.Choice choice ? choice.family() : "";
        config.uiFontFamily(family);
        config.save();
        Theme.setFontFamily(family);
        Theme.install();
        restartFrame();
    }

    private void restartFrame() {
        dispose();
        SwingUtilities.invokeLater(() -> {
            LauncherFrame frame = new LauncherFrame(config);
            frame.setVisible(true);
            frame.selectPage("settings");
        });
    }

    private void applyUiCss() {
        try {
            Path file = config.uiCssFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, uiCssArea.getText());
            Theme.applyCss(file);
            if (!config.uiFontFamily().isBlank()) {
                Theme.setFontFamily(config.uiFontFamily());
            }
            Theme.install();
            restartFrame();
        } catch (IOException ex) {
            showError("保存界面样式失败", ex.getMessage());
        }
    }

    private JPanel buildStatusbar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(Theme.CARD);
        status.setBorder(new MatteBorder(1, 0, 0, 0, Theme.BORDER));

        progressBar = new Ui.ThinProgress();
        progressBar.setValue(0);
        status.add(progressBar, BorderLayout.NORTH);

        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(0, 14, 0, 14));
        row.setPreferredSize(new Dimension(0, 28));
        statusLabel = Ui.label("就绪", Theme.MONO, Theme.DIM);
        row.add(statusLabel, BorderLayout.WEST);

        JPanel meta = new JPanel();
        meta.setOpaque(false);
        meta.setLayout(new BoxLayout(meta, BoxLayout.X_AXIS));
        tasksButton = Ui.smallButton("任务", Ui.AccentButton.Style.GHOST);
        tasksButton.setPreferredSize(new Dimension(64, 24));
        tasksButton.setMaximumSize(new Dimension(64, 24));
        tasksButton.setToolTipText("查看下载任务");
        tasksButton.addActionListener(e -> openTasksDialog());
        meta.add(tasksButton);
        meta.add(Ui.hgap(10));
        sourceLabel = Ui.label("正在连接官方源…", Theme.MONO, Theme.DIM);
        countLabel = Ui.label("—", Theme.MONO, Theme.DIM);
        meta.add(sourceLabel);
        meta.add(Ui.hgap(7));
        meta.add(Ui.label("·", Theme.MONO, Theme.DIM));
        meta.add(Ui.hgap(7));
        meta.add(countLabel);
        row.add(meta, BorderLayout.EAST);
        status.add(row, BorderLayout.CENTER);
        return status;
    }

    private JPanel pageHeader(String title, String subtitle, JComponent right) {
        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setBackground(Theme.CARD);
        top.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(9, 14, 9, 14)));
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel titleLabel = Ui.label(title, Theme.H2, Theme.TEXT);
        JLabel subtitleLabel = Ui.label(subtitle, Theme.MONO, Theme.DIM);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(titleLabel);
        info.add(Ui.vgap(1));
        info.add(subtitleLabel);
        top.add(info, BorderLayout.CENTER);
        if (right != null) {
            top.add(right, BorderLayout.EAST);
        }
        return top;
    }

    private JPanel section(String title, String subtitle) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel heading = Ui.label(title, Theme.H1, Theme.TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel sub = Ui.label(subtitle, Theme.TINY, Theme.DIM);
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(Ui.vgap(3));
            panel.add(sub);
        }
        panel.add(Ui.vgap(10));
        return panel;
    }

    private static JComponent lockedHeight(JComponent component) {
        int height = Math.max(1, component.getPreferredSize().height);
        component.setMinimumSize(new Dimension(0, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return component;
    }

    private static JLabel sidebarNote(String text) {
        JLabel label = new JLabel("<html><body style='width:600px'>" + Formatting.html(text) + "</body></html>");
        label.setFont(Theme.TINY);
        label.setForeground(Theme.DIM);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JPanel divider() {
        JPanel line = new JPanel();
        line.setBackground(Theme.BORDER);
        line.setPreferredSize(new Dimension(0, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    private static JComponent infoRow(String text) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        JLabel dot = Ui.label("•", Theme.TINY, Theme.ACCENT);
        row.add(dot);
        row.add(Ui.hgap(7));
        row.add(Ui.label(text, Theme.TINY, Theme.DIM));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return row;
    }

    private void openVersionDetail() {
        if (selectedEntry == null) {
            return;
        }
        versionDetailId = selectedEntry.id();
        versionLoaderType = "vanilla";
        if (versionInstallButton != null) {
            versionInstallButton.setText("下载原版");
        }
        versionLoaderVersion.setEnabled(true);
        versionDetailTitle.setText(selectedEntry.id());
        versionDetailMeta.setText(detailMeta == null ? ""
                : detailMeta.getText());
        currentDownloadCard = "versionDetail";
        downloadCardsLayout.show(downloadCards, "versionDetail");
        if (downloadCards instanceof Ui.FadePanel fade) {
            fade.fadeIn();
        }
        updateBackButton();
        downloadSubtabs.setVisible(false);
        downloadTitle.setText("版本安装");
        downloadSubtitle.setText("选择加载器并下载原版");
        downloadTools.setVisible(false);
    }

    private void installFromVersionDetail() {
        if (busy || selectedEntry == null) {
            return;
        }
        if (selectedEntry.local()) {
            showError("本地实例", "本地实例已经存在，不需要再安装版本。请直接到「启动游戏」启动。");
            return;
        }
        if (RegionGate.restricted() && !isInstalled(selectedEntry)) {
            RegionGate.showGuide(this);
            return;
        }
        String loaderType = versionLoaderType;
        if ("vanilla".equals(loaderType)) {
            showDownloadTab("versions");
            startInstall(false);
            return;
        }
        String loaderVersion = versionLoaderVersion.getText().trim();
        if (("forge".equals(loaderType) || "neoforge".equals(loaderType))
                && loaderVersion.isBlank()) {
            showError("缺少加载器版本", "Forge / NeoForge 需要填写具体版本，例如 47.2.0。");
            return;
        }
        VersionManifest.VersionEntry entry = selectedEntry;
        VersionMeta meta = selectedMeta;
        JavaManager.JavaInfo resolvedJava = resolveJava(meta);
        beginTask("安装 " + entry.id() + " / " + versionLoaderType);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                if (!isInstalled(entry)) {
                    new Installer(config, taskToken, taskProgress)
                            .install(entry, config.downloadAssets());
                }
                String version = loaderVersion;
                LoaderInstaller installer = new LoaderInstaller(config, taskProgress, taskToken);
                if (version.isBlank()) {
                    version = installer.latestVersion(loaderType);
                }
                JavaManager.JavaInfo java = resolvedJava;
                if (java == null) {
                    java = new JavaManager(config, taskProgress, taskToken)
                            .detectAll().stream().findFirst().orElse(null);
                }
                if (java == null) {
                    throw new IOException("安装加载器需要 Java");
                }
                return installer.install(loaderType, entry.id(), version, java);
            }

            @Override
            protected void done() {
                try {
                    String loaderId = get();
                    finishTask();
                    refreshAllVersions();
                    refreshInstalledCache();
                    for (int i = 0; i < listModel.size(); i++) {
                        if (listModel.get(i).id().equals(loaderId)) {
                            versionList.setSelectedIndex(i);
                            break;
                        }
                    }
                    selectPage("launch");
                    statusLabel.setText("加载器安装完成 · " + loaderId);
                    statusLabel.setForeground(Theme.ACCENT);
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    if (!LauncherFrame.isCancelled(cause) && activeTask != null) {
                        taskCenter.fail(activeTask, messageOf(cause, "失败"));
                        refreshTasksButton();
                    }
                    finishTask();
                    if (LauncherFrame.isCancelled(cause)) {
                        statusLabel.setText("已取消");
                    } else {
                        Log.error("加载器安装失败", cause);
                        statusLabel.setText("加载器安装失败");
                        statusLabel.setForeground(Theme.DANGER);
                        showError("加载器安装失败", messageOf(cause, "请检查 Java 和加载器版本。"));
                    }
                }
            }
        }.execute();
    }

    private void openProjectDetail() {
        if (contentList == null || contentList.getSelectedValue() == null) {
            return;
        }
        detailProject = contentList.getSelectedValue();
        projectDetailTitle.setText(config.forceModTranslation()
                ? contentChineseTitles.getOrDefault(detailProject.id(), detailProject.title())
                : detailProject.title());
        projectDetailMeta.setText(detailProject.author() + "  ·  "
                + Formatting.bytes(detailProject.downloads()) + " 下载  ·  "
                + detailProject.projectType());
        String summary = detailProject.summaryZh() != null
                ? detailProject.summaryZh() : detailProject.description();
        projectDetailSummary.setText("<html><body style='width:720px'>"
                + Formatting.html(summary) + "</body></html>");
        projectDetailIcon.setUrl(detailProject.iconUrl());
        currentDownloadCard = "projectDetail";
        downloadCardsLayout.show(downloadCards, "projectDetail");
        if (downloadCards instanceof Ui.FadePanel fade) {
            fade.fadeIn();
        }
        updateBackButton();
        downloadSubtabs.setVisible(false);
        downloadTitle.setText("项目详情");
        downloadSubtitle.setText("选择具体文件版本");
        downloadTools.setVisible(false);
        loadProjectVersions();
    }

    private void loadProjectVersions() {
        if (detailProject == null) {
            return;
        }
        projectVersionModel.clear();
        projectInstallButton.setEnabled(false);
        statusLabel.setText("正在读取文件版本…");
        statusLabel.setForeground(Theme.DIM);
        String gameVersion = contentVersionField.getText().trim();
        String loader = "mod".equals(detailProject.projectType())
                ? contentLoaderValue() : null;
        if ("mod".equals(detailProject.projectType()) && loader == null) {
            loader = detectLoader();
        }
        String finalLoader = loader;
        new SwingWorker<List<Modrinth.Version>, Void>() {
            @Override
            protected List<Modrinth.Version> doInBackground() throws Exception {
                List<Modrinth.Version> versions = detailProject.curseForge()
                        ? new CurseForge(config.curseForgeApiKey(), ProgressSink.NOOP,
                        new CancellationToken()).versions(detailProject.id(), gameVersion, finalLoader)
                        : new Modrinth(ProgressSink.NOOP, new CancellationToken())
                        .versions(detailProject.id(), gameVersion, finalLoader);
                if (versions.isEmpty()) {
                    versions = detailProject.curseForge()
                            ? new CurseForge(config.curseForgeApiKey(), ProgressSink.NOOP,
                            new CancellationToken()).versions(detailProject.id(), null, finalLoader)
                            : new Modrinth(ProgressSink.NOOP, new CancellationToken())
                            .versions(detailProject.id(), null, finalLoader);
                }
                return versions;
            }

            @Override
            protected void done() {
                try {
                    List<Modrinth.Version> versions = get();
                    Log.info("项目文件版本数: " + versions.size());
                    for (Modrinth.Version version : versions) {
                        projectVersionModel.addElement(version);
                    }
                    if (!versions.isEmpty()) {
                        projectVersionList.setSelectedIndex(0);
                    }
                    statusLabel.setText(versions.isEmpty() ? "没有找到可用文件版本"
                            : "共 " + versions.size() + " 个文件版本");
                    statusLabel.setForeground(versions.isEmpty() ? Theme.WARNING : Theme.DIM);
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    Log.warn("读取项目版本失败: " + messageOf(cause, ""));
                    statusLabel.setText("读取版本失败");
                    statusLabel.setForeground(Theme.DANGER);
                }
            }
        }.execute();
    }

    private void installProjectVersion() {
        if (busy || detailProject == null) {
            return;
        }
        Modrinth.Version version = projectVersionList.getSelectedValue();
        if (version == null) {
            return;
        }
        if (RegionGate.restricted() && "modpack".equals(detailProject.projectType())) {
            RegionGate.showGuide(this);
            return;
        }
        String gameVersion = contentVersionField.getText().trim();
        String loader = "mod".equals(detailProject.projectType()) ? contentLoaderValue() : null;
        String finalLoader = loader;
        Config installConfig = config.copyForGameDir(config.effectiveGameDir(
                selectedEntry == null ? "" : selectedEntry.id()));
        beginTask("安装 " + detailProject.title() + " " + version.versionNumber());
        new SwingWorker<ContentInstaller.Result, Void>() {
            @Override
            protected ContentInstaller.Result doInBackground() throws Exception {
                return new ContentInstaller(installConfig, taskProgress, taskToken)
                        .installVersion(detailProject, version, gameVersion, finalLoader);
            }

            @Override
            protected void done() {
                try {
                    ContentInstaller.Result result = get();
                    finishTask();
                    applyContentResult(detailProject, result);
                    statusLabel.setText("安装完成 · " + result.summary());
                    statusLabel.setForeground(Theme.ACCENT);
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    if (!LauncherFrame.isCancelled(cause) && activeTask != null) {
                        taskCenter.fail(activeTask, messageOf(cause, "失败"));
                        refreshTasksButton();
                    }
                    finishTask();
                    if (LauncherFrame.isCancelled(cause)) {
                        statusLabel.setText("已取消");
                    } else {
                        Log.error("内容安装失败", cause);
                        statusLabel.setText("内容安装失败");
                        statusLabel.setForeground(Theme.DANGER);
                        showError("安装失败", messageOf(cause, "请检查网络和游戏版本。"));
                    }
                }
            }
        }.execute();
    }

    private void applyContentResult(Modrinth.Project project, ContentInstaller.Result result) {
        if ("modpack".equals(project.projectType())) {
            Path instance = result.gameDir();
            config.gameDir(instance);
            config.save();
            gamePaths = new GamePaths(instance);
            gameDirField.setText(instance.toString());
            refreshAllVersions();
            refreshInstalledCache();
            applyFilter();
            if (result.versionId() != null) {
                for (int i = 0; i < listModel.size(); i++) {
                    if (listModel.get(i).id().equals(result.versionId())) {
                        versionList.setSelectedIndex(i);
                        versionList.ensureIndexIsVisible(i);
                        break;
                    }
                }
            }
        } else {
            refreshInstalledCache();
        }
        updateContentSelection();
    }

    private void showDownloadTab(String type) {
        boolean versions = "versions".equals(type);
        if (downloadSubtabs != null) {
            downloadSubtabs.setVisible(true);
        }
        String previousType = contentType;
        contentType = versions ? "versions" : type;
        boolean contentChanged = !versions && !type.equals(previousType);
        if (contentChanged) {
            contentModel.clear();
            contentPage = 0;
            lastSearchedContentVersion = "";
        }
        currentDownloadCard = versions ? "versions" : "content";
        downloadCardsLayout.show(downloadCards, versions ? "versions" : "content");
        if (downloadCards instanceof Ui.FadePanel fade) {
            fade.fadeIn();
        }
        updateBackButton();
        downloadTools.setVisible(versions);
        if (versionsTab != null) {
            versionsTab.setSelected(versions);
            modsTab.setSelected("mod".equals(type));
            packsTab.setSelected("resourcepack".equals(type));
            modpacksTab.setSelected("modpack".equals(type));
            shadersTab.setSelected("shader".equals(type));
        }
        contentLoaderCombo.setVisible("mod".equals(type));
        contentLoaderCombo.setEnabled("mod".equals(type));
        contentInstallButton.setText(switch (type) {
            case "mod" -> "安装模组";
            case "resourcepack" -> "安装资源包";
            case "modpack" -> "安装整合包";
            case "shader" -> "安装光影";
            default -> "安装所选";
        });
        if (versions) {
            downloadTitle.setText("下载中心");
            downloadSubtitle.setText("Mojang 官方清单 · 选择版本下载或启动");
            applyFilter();
            SwingUtilities.invokeLater(() -> {
                int index = versionList.getSelectedIndex();
                if (index >= 0) {
                    versionList.ensureIndexIsVisible(index);
                    java.awt.Rectangle bounds = versionList.getCellBounds(index, index);
                    if (bounds != null) {
                        versionList.scrollRectToVisible(bounds);
                    }
                }
            });
            return;
        }
        downloadTitle.setText(switch (type) {
            case "mod" -> "模组下载";
            case "resourcepack" -> "资源包";
            case "shader" -> "光影下载";
            default -> "整合包";
        });
        downloadSubtitle.setText("来自 Modrinth · 自动补齐必需前置 · SHA-1 校验");
        String contentVersionBefore = contentVersionField.getText().trim();
        updateContentVersionIfNeeded();
        String contentVersionAfter = contentVersionField.getText().trim();
        if ((contentChanged || contentModel.isEmpty()
                || !contentVersionBefore.equals(contentVersionAfter)
                || !contentVersionAfter.equals(lastSearchedContentVersion))
                && !contentSearching) {
            searchContent();
        }
    }

    private void updateContentVersionIfNeeded() {
        if (contentVersionField == null || !contentVersionAuto
                || contentVersionField.hasFocus()) {
            return;
        }
        String version = baseGameVersion();
        if (version == null || version.isBlank()
                || version.equals(contentVersionField.getText().trim())) {
            return;
        }
        contentVersionUpdating = true;
        try {
            contentVersionField.setText(version);
        } finally {
            contentVersionUpdating = false;
        }
    }

    private String baseGameVersion() {
        if (selectedMeta != null) {
            String version = versionFromId(selectedMeta.id());
            if (version != null) {
                return version;
            }
        }
        if (selectedEntry != null) {
            String version = versionFromId(selectedEntry.id());
            if (version != null) {
                return version;
            }
            String detected = detectPackGameVersion(selectedEntry);
            if (detected != null) {
                return detected;
            }
        }
        return manifest == null ? "1.20.1" : manifest.latestRelease();
    }

    private String detectPackGameVersion(VersionManifest.VersionEntry entry) {
        Path content = config.effectiveGameDir(entry.id());
        for (String name : new String[]{"manifest.json", "modpack.cfg"}) {
            Path file = content.resolve(name);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                String text = Files.readString(file);
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "(?s)\"minecraft\"\\s*:\\s*\\{[^}]*?\"version\"\\s*:\\s*\"(\\d+\\.\\d+(?:\\.\\d+)?)\"" )
                        .matcher(text);
                if (matcher.find()) {
                    return matcher.group(1);
                }
                matcher = java.util.regex.Pattern.compile(
                        "(?m)^\\s*version\\s*[=:]\\s*(\\d+\\.\\d+(?:\\.\\d+)?)\\s*$")
                        .matcher(text);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception ignored) {
            }
        }
        String fromMods = detectGameVersionFromMods(content.resolve("mods"));
        if (fromMods != null) {
            return fromMods;
        }
        return null;
    }

    private String detectGameVersionFromMods(Path mods) {
        if (mods == null || !Files.isDirectory(mods)) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)(?:minecraft|versionRange)[^\\n]{0,80}?(1\\.\\d{1,2}(?:\\.\\d{1,2})?)");
        java.util.Map<String, Integer> candidates = new java.util.HashMap<>();
        try (var stream = Files.list(mods)) {
            int scanned = 0;
            for (Path jar : stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .limit(12).toList()) {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements() && scanned < 24) {
                        var entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.endsWith("mods.toml") && !name.endsWith("fabric.mod.json")) {
                            continue;
                        }
                        scanned++;
                        try (var in = zip.getInputStream(entry)) {
                            String text = new String(in.readNBytes(256 * 1024),
                                    java.nio.charset.StandardCharsets.UTF_8);
                            var matcher = pattern.matcher(text);
                            while (matcher.find() && candidates.size() < 40) {
                                candidates.merge(matcher.group(1), 1, Integer::sum);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return candidates.entrySet().stream()
                .max(java.util.Comparator
                        .comparingInt((java.util.Map.Entry<String, Integer> e) -> e.getValue())
                        .thenComparingInt(e -> e.getKey().length()))
                .map(java.util.Map.Entry::getKey)
                .orElse(null);
    }

    private static String versionFromId(String id) {
        if (id == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(id);
        String found = null;
        while (matcher.find()) {
            found = matcher.group(1);
        }
        return found;
    }

    private static boolean containsCjk(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesMatch(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String left = a.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String right = b.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return left.length() >= 4 && right.length() >= 4
                && (left.contains(right) || right.contains(left));
    }

    private String contentSource() {
        Object selected = contentSourceCombo == null ? null : contentSourceCombo.getSelectedItem();
        return "CurseForge".equals(selected) ? "curseforge" : "modrinth";
    }

    private List<Modrinth.Project> searchProjects(String source, String query, String type,
                                                  String gameVersion, String loader)
            throws IOException {
        return searchProjects(source, query, type, gameVersion, loader, 0);
    }

    private List<Modrinth.Project> searchProjects(String source, String query, String type,
                                                  String gameVersion, String loader, int offset)
            throws IOException {
        if ("curseforge".equals(source)) {
            return new CurseForge(config.curseForgeApiKey(), ProgressSink.NOOP,
                    new CancellationToken()).search(query, type, gameVersion, loader, offset, 20);
        }
        return new Modrinth(ProgressSink.NOOP, new CancellationToken())
                .search(query, type, gameVersion, loader, offset, 20);
    }

    private String contentLoaderValue() {
        Object selected = contentLoaderCombo.getSelectedItem();
        if (selected == null || "自动".equals(selected)) {
            return null;
        }
        return selected.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private String detectLoader() {
        String id = selectedEntry == null ? "" : selectedEntry.id().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("neoforge")) {
            return "neoforge";
        }
        if (id.contains("fabric")) {
            return "fabric";
        }
        if (id.contains("quilt")) {
            return "quilt";
        }
        if (id.contains("forge")) {
            return "forge";
        }
        return null;
    }

    private void searchContent() {
        contentPage = 0;
        runContentSearch();
    }

    private void runContentSearch() {
        if (contentModel == null) {
            return;
        }
        contentSearching = true;
        int generation = ++contentGeneration;
        if (contentPrevPage != null) {
            contentPrevPage.setEnabled(false);
        }
        if (contentNextPage != null) {
            contentNextPage.setEnabled(false);
        }
        contentChineseTitles.clear();
        contentModel.clear();
        contentSelectionLabel.setText("正在搜索 " + ("curseforge".equals(contentSource())
                ? "CurseForge" : "Modrinth") + "…");
        contentInstallButton.setEnabled(false);
        String query = contentSearchField.getText().trim();
        String gameVersion = contentVersionField.getText().trim();
        lastSearchedContentVersion = gameVersion;
        String loader = "mod".equals(contentType) ? contentLoaderValue() : null;
        saveCurseForgeKey();
        String source = contentSource();
        new SwingWorker<List<Modrinth.Project>, Void>() {
            @Override
            protected List<Modrinth.Project> doInBackground() throws Exception {
                boolean cjkQuery = containsCjk(query);
                List<Modrinth.Project> projects = cjkQuery ? new ArrayList<>()
                        : new ArrayList<>(searchProjects(source, query, contentType,
                        gameVersion, loader, contentPage * 20));
                List<McMod.Entry> entries;
                try {
                    entries = new McMod().search(query, "modpack".equals(contentType) ? 2 : 0);
                } catch (IOException ex) {
                    Log.warn("MC百科搜索失败: " + ex.getMessage());
                    entries = List.of();
                }
                if (cjkQuery && entries.isEmpty()) {
                    projects.addAll(searchProjects(source, query, contentType, gameVersion, loader,
                            contentPage * 20));
                }
                if (cjkQuery) {
                    java.util.Set<String> ids = new java.util.HashSet<>();
                    for (Modrinth.Project project : projects) {
                        ids.add(project.id());
                    }
                    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors
                            .newFixedThreadPool(4, runnable -> {
                                Thread thread = new Thread(runnable, "mclite-mcmod");
                                thread.setDaemon(true);
                                return thread;
                            });
                    try {
                        java.util.List<java.util.concurrent.Future<java.util.List<Modrinth.Project>>> futures =
                                new ArrayList<>();
                        for (McMod.Entry entry : entries.subList(0, Math.min(12, entries.size()))) {
                            String english = entry.englishName();
                            if (english.isBlank()) {
                                continue;
                            }
                            futures.add(pool.submit(() -> searchProjects(source, english,
                                    contentType, gameVersion, loader, contentPage * 20)));
                        }
                        int index = 0;
                        for (McMod.Entry entry : entries.subList(0, Math.min(12, entries.size()))) {
                            if (entry.englishName().isBlank()) {
                                continue;
                            }
                            try {
                                for (Modrinth.Project project : futures.get(index).get()) {
                                    if (ids.add(project.id())) {
                                        projects.add(project.withMcmod(entry.summary(), entry.url()));
                                    }
                                }
                            } catch (Exception ex) {
                                Log.warn("英文名回查失败: " + ex.getMessage());
                            }
                            index++;
                        }
                    } finally {
                        pool.shutdownNow();
                    }
                }
                List<Modrinth.Project> enriched = new ArrayList<>();
                for (Modrinth.Project project : projects) {
                    McMod.Entry match = null;
                    for (McMod.Entry entry : entries) {
                        if (namesMatch(project.title(), entry.englishName())
                                || namesMatch(project.slug(), entry.englishName())) {
                            match = entry;
                            break;
                        }
                    }
                    if (match != null) {
                        contentChineseTitles.put(project.id(), match.title());
                        enriched.add(project.withMcmod(match.summary(), match.url()));
                    } else {
                        enriched.add(project);
                    }
                }
                return enriched;
            }

            @Override
            protected void done() {
                if (generation != contentGeneration) {
                    return;
                }
                contentSearching = false;
                try {
                    List<Modrinth.Project> projects = get();
                    for (Modrinth.Project project : projects) {
                        contentModel.addElement(project);
                    }
                    if (contentListFade != null) {
                        contentListFade.fadeIn();
                    }
                    contentSelectionLabel.setText(projects.isEmpty()
                            ? "没有找到内容" : projects.size() + " 个结果 · 选择后安装");
                    if (contentPageLabel != null) {
                        contentPageLabel.setText(Integer.toString(contentPage + 1));
                    }
                    if (contentPrevPage != null) {
                        contentPrevPage.setEnabled(contentPage > 0 && !contentSearching);
                    }
                    if (contentNextPage != null) {
                        contentNextPage.setEnabled(projects.size() >= 20 && !contentSearching);
                    }
                    if (!projects.isEmpty()) {
                        contentList.setSelectedIndex(0);
                    }
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    Log.warn("Modrinth 搜索失败: " + messageOf(cause, ""));
                    contentSelectionLabel.setText("搜索失败 · " + messageOf(cause, ""));
                }
            }
        }.execute();
    }

    private void updateContentSelection() {
        if (contentList == null || contentInstallButton == null) {
            return;
        }
        Modrinth.Project project = contentList.getSelectedValue();
        boolean selected = project != null;
        contentInstallButton.setEnabled(selected && !busy);
        contentMcmodButton.setVisible(selected && project.mcmodUrl() != null);
        if (selected) {
            String summary = project.summaryZh() == null ? project.description() : project.summaryZh();
            if (summary != null && summary.length() > 100) {
                summary = summary.substring(0, 98) + "…";
            }
            contentSelectionLabel.setText(summary == null || summary.isBlank()
                    ? project.title() : "MC百科：" + summary);
            contentSelectionLabel.setToolTipText(project.mcmodUrl() == null
                    ? project.description() : project.summaryZh() + "\n" + project.mcmodUrl());
        } else {
            contentSelectionLabel.setText("选择一个项目");
        }
    }

    private void startContentInstall() {
        if (busy) {
            return;
        }
        Modrinth.Project project = contentList == null ? null : contentList.getSelectedValue();
        if (project == null) {
            return;
        }
        if (RegionGate.restricted() && "modpack".equals(project.projectType())) {
            RegionGate.showGuide(this);
            return;
        }
        String gameVersion = contentVersionField.getText().trim();
        if (gameVersion.isEmpty()) {
            showError("缺少游戏版本", "请填写该内容对应的 Minecraft 版本，例如 1.20.1。");
            return;
        }
        String loader = "mod".equals(contentType) ? contentLoaderValue() : null;
        if ("mod".equals(contentType) && loader == null) {
            loader = detectLoader();
        }
        if ("mod".equals(contentType) && loader == null) {
            showError("需要模组加载器", "安装模组前，请先在「启动器配置」中导入带 Fabric/Forge 的实例，"
                    + "或在上方选择加载器。");
            return;
        }
        String chosenLoader = loader;
        Config installConfig = config.copyForGameDir(config.effectiveGameDir(
                selectedEntry == null ? "" : selectedEntry.id()));
        beginTask("安装 " + project.title());
        new SwingWorker<ContentInstaller.Result, Void>() {
            @Override
            protected ContentInstaller.Result doInBackground() throws Exception {
                return new ContentInstaller(installConfig, taskProgress, taskToken)
                        .install(project, gameVersion, chosenLoader);
            }

            @Override
            protected void done() {
                try {
                    ContentInstaller.Result result = get();
                    finishTask();
                    if ("modpack".equals(project.projectType())) {
                        Path instance = result.gameDir();
                        config.gameDir(instance);
                        config.save();
                        gamePaths = new GamePaths(instance);
                        gameDirField.setText(instance.toString());
                        refreshAllVersions();
                        refreshInstalledCache();
                        applyFilter();
                        if (result.versionId() != null) {
                            for (int i = 0; i < listModel.size(); i++) {
                                if (listModel.get(i).id().equals(result.versionId())) {
                                    versionList.setSelectedIndex(i);
                                    versionList.ensureIndexIsVisible(i);
                                    break;
                                }
                            }
                        }
                    } else {
                        refreshInstalledCache();
                    }
                    statusLabel.setText("安装完成 · " + result.summary());
                    statusLabel.setForeground(Theme.ACCENT);
                    updateContentSelection();
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    if (!LauncherFrame.isCancelled(cause) && activeTask != null) {
                        taskCenter.fail(activeTask, messageOf(cause, "失败"));
                        refreshTasksButton();
                    }
                    finishTask();
                    if (LauncherFrame.isCancelled(cause)) {
                        statusLabel.setText("已取消");
                        statusLabel.setForeground(Theme.MUTED);
                    } else {
                        Log.error("内容安装失败", cause);
                        statusLabel.setText("内容安装失败");
                        statusLabel.setForeground(Theme.DANGER);
                        showError("安装失败", messageOf(cause, "请检查网络和游戏版本。"));
                    }
                }
            }
        }.execute();
    }

    private void showPage(String page) {
        if (contentCards == null) {
            return;
        }
        if ("instances".equals(page)) {
            refreshInstancesPage();
        }
        currentTop = page;
        contentCards.show(contentCardsPanel, page);
        if (contentCardsPanel instanceof Ui.FadePanel fade) {
            fade.fadeIn();
        }
        updateBackButton();
    }

    private void toggleSidebar() {
        boolean currentlyCollapsed = config.sidebarCollapsed() || !sidebar.isVisible();
        boolean collapse = !currentlyCollapsed;
        config.sidebarCollapsed(collapse);
        config.save();

        if (!collapse) {

            sidebar.setVisible(true);
            sidebar.setPreferredSize(new Dimension(286, 0));
            sidebar.setMinimumSize(new Dimension(286, 0));
            sidebar.setMaximumSize(new Dimension(286, Integer.MAX_VALUE));
            sidebarToggle.setText("‹");
            getContentPane().revalidate();
            getContentPane().repaint();
            return;
        }

        int from = sidebar.getWidth() > 0 ? sidebar.getWidth() : 286;
        long started = System.currentTimeMillis();
        Timer timer = new Timer(16, null);
        timer.addActionListener(e -> {
            float t = Math.min(1f, (System.currentTimeMillis() - started) / 180f);
            float eased = 1f - (1f - t) * (1f - t);
            int width = Math.round(from * (1f - eased));
            sidebar.setPreferredSize(new Dimension(width, 0));
            sidebar.setMinimumSize(new Dimension(width, 0));
            getContentPane().revalidate();
            getContentPane().repaint();
            if (t >= 1f) {
                timer.stop();
                sidebar.setVisible(false);
                sidebarToggle.setText("☰");
                sidebarToggle.revalidate();
                sidebarToggle.repaint();
            }
        });
        timer.start();
    }

    private void goBack() {
        if (!"download".equals(currentTop)) {
            downloadTab.setSelected(true);
            showPage("download");
            showDownloadTab("versions");
            return;
        }
        if ("versionDetail".equals(currentDownloadCard)) {
            showDownloadTab("versions");
            return;
        }
        if ("projectDetail".equals(currentDownloadCard)) {
            showDownloadTab(contentType);
            return;
        }
        if ("content".equals(currentDownloadCard)) {
            showDownloadTab("versions");
        }
    }

    private void updateBackButton() {
        if (headerBackButton == null) {
            return;
        }
        boolean detail = "versionDetail".equals(currentDownloadCard)
                || "projectDetail".equals(currentDownloadCard);
        boolean content = "content".equals(currentDownloadCard);
        headerBackButton.setEnabled(detail || content || !"download".equals(currentTop));
    }

    public void openFirstProjectForScreenshot() {
        if (contentModel != null && !contentModel.isEmpty()) {
            contentList.setSelectedIndex(0);
            openProjectDetail();
        }
    }

    public void openFirstVersionDetailForScreenshot() {
        if (versionList.getSelectedIndex() < 0 && !listModel.isEmpty()) {
            versionList.setSelectedIndex(0);
        }
        openVersionDetail();
    }

    public void selectPage(String page) {
        if ("launch".equals(page)) {
            selectTab(launchTab, "launch");
        } else if ("config".equals(page)) {
            selectTab(configTab, "config");
        } else if ("settings".equals(page)) {
            selectTab(settingsTab, "settings");
        } else if ("instances".equals(page)) {
            selectTab(downloadTab, "instances");
        } else if ("project".equals(page)) {
            selectTab(downloadTab, "download");
            showDownloadTab("mod");
        } else if ("version-detail".equals(page)) {
            selectTab(downloadTab, "download");
            showDownloadTab("versions");
        } else if ("mod".equals(page) || "resourcepack".equals(page)
                || "modpack".equals(page)) {
            selectTab(downloadTab, "download");
            showDownloadTab(page);
        } else {
            selectTab(downloadTab, "download");
            showDownloadTab("versions");
        }
    }

    private void selectTab(Ui.NavTab tab, String page) {
        tab.setSelected(true);
        showPage(page);
    }

    private void reloadAccounts() {
        AccountStore.Data data = AccountStore.load(config.accountsDir());
        AccountStore.Account selected = null;
        for (AccountStore.Account account : data.accounts()) {
            if (account.id().equals(config.accountId())) {
                selected = account;
                break;
            }
        }
        if (selected == null) {
            for (AccountStore.Account account : data.accounts()) {
                if (account.id().equals(data.selectedId())) {
                    selected = account;
                    break;
                }
            }
        }
        if (selected == null && !data.accounts().isEmpty()) {
            selected = data.accounts().get(0);
        }
        if (selected != null) {
            config.accountId(selected.id());
            config.username(selected.name());
            AccountStore.save(config.accountsDir(),
                    new AccountStore.Data(data.accounts(), selected.id()));
            config.save();
        }
        updateAccountLabels();
    }

    private void updateAccountLabels() {
        if (sidebarAccount != null) {
            sidebarAccount.setText(config.username());
        }
        if (launchAccount != null) {
            launchAccount.setText(config.username() + "  ·  离线账户");
        }
        if (configAccountLabel != null) {
            configAccountLabel.setText(config.username());
        }
        java.awt.Image skin = SkinStore.load(config, config.accountId());
        if (sidebarSkin != null) {
            sidebarSkin.setSkin(skin);
        }
        if (launchSkin != null) {
            launchSkin.setSkin(skin);
        }
        if (configSkin != null) {
            configSkin.setSkin(skin);
        }
        SkinStore.copyForGame(config, config.accountId());
        if (accountsDirLabel != null) {
            accountsDirLabel.setText(config.accountsDir().toString());
            accountsDirLabel.setToolTipText(config.accountsDir().toString());
        }
        updateLaunchInfo();
    }

    private void openAccountsDialog() {
        new AccountsDialog(this, config).setVisible(true);
        reloadAccounts();
    }

    private void saveCurseForgeKey() {
        if (curseForgeKeyField != null) {
            config.curseForgeApiKey(curseForgeKeyField.getText());
            config.save();
        }
    }

    private void saveJvmArgs() {
        if (jvmArgsField == null) {
            return;
        }
        config.jvmArgs(jvmArgsField.getText());
        config.save();
        updateLaunchInfo();
    }

    private void openLogsDirectory() {
        try {
            Files.createDirectories(config.logsDir());
            openPath(config.logsDir());
        } catch (IOException ex) {
            showError("无法打开日志目录", ex.getMessage());
        }
    }

    private void openConfigDirectory() {
        try {
            Files.createDirectories(config.appHome());
            openPath(config.appHome());
        } catch (IOException ex) {
            showError("无法打开数据目录", ex.getMessage());
        }
    }

    private void startJavaDetection() {
        Path current = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java");
        JavaManager.Probe probe = JavaManager.probe(current);
        if (probe.major() > 0) {
            javaInfos.add(new JavaManager.JavaInfo(current, probe.major(),
                    "Java " + probe.major() + " · " + probe.vendor(), null, false));
            refreshJavaChoices(null);
        }
        new SwingWorker<List<JavaManager.JavaInfo>, Void>() {
            @Override
            protected List<JavaManager.JavaInfo> doInBackground() {
                return new JavaManager(config, ProgressSink.NOOP, new CancellationToken()).detectAll();
            }

            @Override
            protected void done() {
                try {
                    List<JavaManager.JavaInfo> detected = get();
                    mergeManualJava(detected);
                    refreshJavaChoices(null);
                } catch (Exception ex) {
                    Log.warn("检测 Java 失败: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void mergeManualJava(List<JavaManager.JavaInfo> detected) {
        List<JavaManager.JavaInfo> merged = new ArrayList<>(detected);
        List<String> javaPaths = new ArrayList<>(config.javaPaths());
        if (!config.javaPath().isBlank() && !javaPaths.contains(config.javaPath())) {
            javaPaths.add(config.javaPath());
        }
        for (String value : javaPaths) {
            try {
                Path path = Path.of(value).toAbsolutePath().normalize();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                boolean exists = merged.stream().anyMatch(info -> info.path().toAbsolutePath()
                        .normalize().equals(path));
                if (exists) {
                    continue;
                }
                JavaManager.Probe probe = JavaManager.probe(path);
                if (probe.major() > 0) {
                    merged.add(new JavaManager.JavaInfo(path, probe.major(),
                            "Java " + probe.major() + " · " + probe.vendor() + " · 手动添加",
                            null, false));
                }
            } catch (RuntimeException ignored) {
            }
        }
        javaInfos.clear();
        javaInfos.addAll(merged);
    }

    private void openJavaManager() {
        new JavaDialog(this, config, javaInfos, (runtimes, selected) -> {
            javaInfos.clear();
            javaInfos.addAll(runtimes);
            if (selected != null) {
                config.javaPath(selected.path().toString());
                config.save();
                refreshJavaChoices(selected.path());
            } else {
                refreshJavaChoices(null);
            }
        }).setVisible(true);
    }

    private void refreshJavaChoices(Path selectPath) {
        refreshingJavaCombo = true;
        try {
            javaCombo.removeAllItems();
            javaCombo.addItem(new JavaChoice("自动选择（按版本匹配）", null));
            for (JavaManager.JavaInfo info : javaInfos) {
                javaCombo.addItem(new JavaChoice(info.label(), info.path()));
            }
            Path wanted = selectPath;
            if (wanted == null && !config.javaPath().isBlank()) {
                try {
                    wanted = Path.of(config.javaPath()).toAbsolutePath().normalize();
                } catch (RuntimeException ignored) {
                    wanted = null;
                }
            }
            if (wanted == null) {
                javaCombo.setSelectedIndex(0);
            } else {
                int index = 0;
                for (int i = 0; i < javaCombo.getItemCount(); i++) {
                    JavaChoice choice = javaCombo.getItemAt(i);
                    if (choice.path() != null && choice.path().toAbsolutePath().normalize()
                            .equals(wanted.toAbsolutePath().normalize())) {
                        index = i;
                        break;
                    }
                }
                javaCombo.setSelectedIndex(index);
            }
        } finally {
            refreshingJavaCombo = false;
        }
        updateJavaHint();
    }

    private void updateJavaHint() {
        if (javaHint == null || javaCombo == null) {
            return;
        }
        if (selectedMeta == null) {
            if (javaInfos.isEmpty()) {
                setJavaHint("未检测到 Java；启动时会提示下载官方运行时", Theme.WARNING);
                if (launchJava != null) {
                    launchJava.setText("未检测到");
                }
            } else {
                setJavaHint("自动选择；当前可用 Java " + javaInfos.get(0).major(), Theme.DIM);
                if (launchJava != null) {
                    launchJava.setText("Java " + javaInfos.get(0).major());
                }
            }
            return;
        }
        JavaManager.JavaInfo java = resolveJava(selectedMeta);
        int required = selectedMeta.javaMajor();
        if (java == null) {
            setJavaHint("需要 Java " + required + "，未检测到可用运行时", Theme.WARNING);
        } else if (java.major() < required) {
            setJavaHint("需要 Java " + required + "，当前 Java " + java.major()
                    + "；启动时可下载官方运行时", Theme.WARNING);
        } else if (java.major() > required + 4) {
            setJavaHint("版本要求 Java " + required + "，当前 Java " + java.major()
                    + "；如遇兼容问题请切换", Theme.ORANGE);
        } else {
            setJavaHint("将使用 Java " + java.major()
                    + (java.official() ? "（Mojang 官方）" : "（本机）"), Theme.DIM);
        }
        javaHint.setToolTipText(java == null ? null : java.path().toString());
        if (launchJava != null) {
            launchJava.setText(java == null ? "未检测到"
                    : "Java " + java.major() + (java.official() ? " · 官方" : ""));
        }
    }

    private void setJavaHint(String text, Color color) {
        javaHint.setText(text);
        javaHint.setForeground(color);
    }

    private JavaManager.JavaInfo resolveJava(VersionMeta meta) {
        JavaChoice choice = (JavaChoice) javaCombo.getSelectedItem();
        if (choice != null && choice.path() != null) {
            for (JavaManager.JavaInfo info : javaInfos) {
                if (info.path().toAbsolutePath().normalize()
                        .equals(choice.path().toAbsolutePath().normalize())) {
                    return info;
                }
            }
            JavaManager.Probe probe = JavaManager.probe(choice.path());
            if (probe.major() > 0) {
                return new JavaManager.JavaInfo(choice.path(), probe.major(),
                        "Java " + probe.major() + " · " + probe.vendor(), null, false);
            }
        }
        if (meta == null) {
            return javaInfos.isEmpty() ? null : javaInfos.get(0);
        }
        JavaManager.JavaInfo chosen = JavaManager.choose(javaInfos, meta.javaMajor(), meta.javaComponent());
        if (chosen != null && chosen.major() >= meta.javaMajor()) {
            return chosen;
        }
        JavaManager.JavaInfo installed = new JavaManager(config, ProgressSink.NOOP, new CancellationToken())
                .findInstalled(meta.javaComponent(), meta.javaMajor());
        return installed != null ? installed : chosen;
    }

    private void loadManifest() {
        if (busy || manifestLoading) {
            return;
        }
        manifestLoading = true;
        refreshButton.setEnabled(false);
        sourceLabel.setText("正在更新官方版本列表…");
        sourceLabel.setForeground(Theme.MUTED);
        if (allVersions.isEmpty()) {
            statusLabel.setText("正在连接 Mojang 官方源…");
            statusLabel.setForeground(Theme.DIM);
        }
        TaskProgress localProgress = new TaskProgress();
        CancellationToken localToken = new CancellationToken();
        new SwingWorker<Versions.FetchResult, Void>() {
            @Override
            protected Versions.FetchResult doInBackground() throws Exception {
                return Versions.fetch(config, localProgress, localToken);
            }

            @Override
            protected void done() {
                manifestLoading = false;
                refreshButton.setEnabled(true);
                try {
                    Versions.FetchResult result = get();
                    manifest = result.manifest();
                    refreshAllVersions();
                    metaCache.clear();
                    refreshInstalledCache();
                    updateContentVersionIfNeeded();
                    sourceLabel.setText(result.fromCache()
                            ? "官方缓存 · " + CACHE_TIME.format(result.fetchedAt())
                            : "官方源 · 刚刚更新");
                    sourceLabel.setForeground(result.fromCache() ? Theme.WARNING : Theme.MUTED);
                    applyFilter();
                    statusLabel.setText("就绪 · 共 " + allVersions.size() + " 个官方版本");
                    statusLabel.setForeground(Theme.DIM);
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    Log.error("获取版本列表失败", cause);
                    sourceLabel.setText("官方源 · 获取失败");
                    sourceLabel.setForeground(Theme.DANGER);
                    statusLabel.setText("无法获取版本列表");
                    statusLabel.setForeground(Theme.DANGER);
                    showError("获取版本列表失败", messageOf(cause, "请检查网络连接后重试。"));
                }
            }
        }.execute();
    }

    private void scheduleSearch() {
        if (searchTimer == null) {
            searchTimer = new Timer(180, e -> applyFilter());
            searchTimer.setRepeats(false);
        }
        searchTimer.restart();
    }

    private void setFilter(String filter) {
        config.filter(filter);
        config.save();
        applyFilter();
    }

    private void applyFilter() {
        if (manifest == null) {
            return;
        }
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        String filter = config.filter();
        String previous = selectedEntry == null ? null : selectedEntry.id();
        List<VersionManifest.VersionEntry> filtered = new ArrayList<>();
        for (VersionManifest.VersionEntry entry : allVersions) {
            boolean typeMatch = entry.local() || switch (filter) {
                case "snapshot" -> entry.type().equals("snapshot");
                case "all" -> true;
                default -> entry.type().equals("release");
            };
            if (!typeMatch) {
                continue;
            }
            if (!query.isEmpty() && !entry.id().toLowerCase().contains(query)) {
                continue;
            }
            filtered.add(entry);
        }

        refreshingList = true;
        try {
            DefaultListModel<VersionManifest.VersionEntry> nextModel = new DefaultListModel<>();
            for (VersionManifest.VersionEntry entry : filtered) {
                nextModel.addElement(entry);
            }
            listModel = nextModel;
            versionList.setModel(listModel);
            if (releaseFilter != null) {
                releaseFilter.setSelected(filter.equals("release"));
                snapshotFilter.setSelected(filter.equals("snapshot"));
                allFilter.setSelected(filter.equals("all"));
            }
            countLabel.setText(filtered.size() + " / " + allVersions.size() + " 个版本");
            listCardLayout.show(listCards, filtered.isEmpty() ? "empty" : "list");
            if (filtered.isEmpty()) {
                selectedEntry = null;
                selectedMeta = null;
                detailTitle.setText("没有匹配的版本");
                detailMeta.setText("尝试清空搜索，或切换到「全部」");
                statusLabel.setText("就绪");
                updateButtons();
                return;
            }
            int target = -1;
            if (previous != null) {
                for (int i = 0; i < filtered.size(); i++) {
                    if (filtered.get(i).id().equals(previous)) {
                        target = i;
                        break;
                    }
                }
            }
            if (target < 0 && !config.lastVersion().isBlank()) {
                for (int i = 0; i < filtered.size(); i++) {
                    if (filtered.get(i).id().equals(config.lastVersion())) {
                        target = i;
                        break;
                    }
                }
            }
            if (target < 0 && manifest.latestRelease() != null) {
                for (int i = 0; i < filtered.size(); i++) {
                    if (filtered.get(i).id().equals(manifest.latestRelease())) {
                        target = i;
                        break;
                    }
                }
            }
            if (target < 0) {
                target = 0;
            }
            versionList.setSelectedIndex(target);
            versionList.ensureIndexIsVisible(target);
        } finally {
            refreshingList = false;
        }
        onVersionSelected();
        final int targetIndex = versionList.getSelectedIndex();
        SwingUtilities.invokeLater(() -> {
            if (targetIndex >= 0) {
                versionList.ensureIndexIsVisible(targetIndex);
                java.awt.Rectangle bounds = versionList.getCellBounds(targetIndex, targetIndex);
                if (bounds != null) {
                    versionList.scrollRectToVisible(bounds);
                }
            }
        });
    }

    private void onVersionSelected() {
        VersionManifest.VersionEntry entry = versionList.getSelectedValue();
        if (entry == null) {
            return;
        }
        selectedEntry = entry;
        selectedMeta = null;
        updateVersionPicker();
        updateContentVersionIfNeeded();
        if ("content".equals(currentDownloadCard) && !contentSearching) {
            SwingUtilities.invokeLater(this::searchContent);
        }
        int generation = ++selectionGeneration;
        detailTitle.setText(entry.id());
        detailMeta.setText(entry.displayType() + " · " + Formatting.date(entry.releaseTime())
                + " · 正在读取版本信息…");
        statusLabel.setText(isInstalled(entry) ? "已安装" : "未安装");
        statusLabel.setForeground(Theme.DIM);
        updateButtons();
        updateJavaHint();

        VersionMeta cached = metaCache.get(entry.id());
        if (cached != null) {
            applyMeta(entry, cached, generation);
            return;
        }

        new SwingWorker<VersionMeta, Void>() {
            @Override
            protected VersionMeta doInBackground() throws Exception {
                if (entry.local()) {
                    return LocalVersions.load(gamePaths, entry.id());
                }
                Path local = gamePaths.versionJson(entry.id());
                if (Files.isRegularFile(local)) {
                    return VersionMeta.parse(Files.readString(local), entry.id());
                }
                Net net = new Net(ProgressSink.NOOP, new CancellationToken());
                byte[] bytes = net.getBytes(entry.url(), entry.sha1(), -1, "版本信息 " + entry.id());
                return VersionMeta.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
                        entry.id());
            }

            @Override
            protected void done() {
                if (generation != selectionGeneration) {
                    return;
                }
                try {
                    VersionMeta meta = get();
                    metaCache.put(entry.id(), meta);
                    applyMeta(entry, meta, generation);
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    Log.warn("读取版本信息失败: " + messageOf(cause, ""));
                    if (generation == selectionGeneration) {
                        detailMeta.setText(entry.displayType() + " · " + Formatting.date(entry.releaseTime())
                                + " · 无法读取版本信息");
                        statusLabel.setText("版本信息读取失败，可尝试下载");
                        statusLabel.setForeground(Theme.WARNING);
                        pendingLaunch = false;
                        updateButtons();
                    }
                }
            }
        }.execute();
    }

    private void applyMeta(VersionManifest.VersionEntry entry, VersionMeta meta, int generation) {
        if (generation != selectionGeneration || selectedEntry == null
                || !selectedEntry.id().equals(entry.id())) {
            return;
        }
        selectedMeta = meta;
        updateContentVersionIfNeeded();
        updateDetailForSelection();
        updateJavaHint();
        updateButtons();
        if (pendingLaunch) {
            pendingLaunch = false;
            SwingUtilities.invokeLater(this::startLaunch);
        }
    }

    private void updateDetailForSelection() {
        if (selectedEntry == null) {
            detailTitle.setText("选择一个版本");
            detailMeta.setText(manifest == null ? "共 0 个官方版本" : "共 " + allVersions.size() + " 个官方版本，支持正式版 / 快照 / 旧版");
            updateButtons();
            return;
        }
        boolean installed = isInstalled(selectedEntry);
        String base = selectedEntry.displayType() + " · " + Formatting.date(selectedEntry.releaseTime());
        if (selectedMeta == null) {
            detailTitle.setText(selectedEntry.id());
            detailMeta.setText(base + (installed ? " · 已安装" : " · 正在读取版本信息…"));
            return;
        }
        long estimate = Installer.estimate(selectedMeta, config.downloadAssets());
        detailTitle.setText(selectedEntry.id());
        detailMeta.setText(base + " · Java " + selectedMeta.javaMajor()
                + " · " + (config.downloadAssets() ? "完整安装约 " : "核心文件约 ")
                + Formatting.bytes(estimate)
                + (installed ? " · 已安装" : ""));
        if (installed) {
            boolean complete = selectedEntry.local() || !gamePaths.hasMarker(selectedEntry.id())
                    || gamePaths.assetsComplete(selectedEntry.id());
            statusLabel.setText(complete ? "已安装，可以启动" : "已安装，但资源未完整下载");
            statusLabel.setForeground(complete ? Theme.DIM : Theme.WARNING);
        } else {
            statusLabel.setText("未安装 · 点击「下载并启动」自动安装");
            statusLabel.setForeground(Theme.DIM);
        }
        updateLaunchInfo();
        updateButtons();
    }

    private void updateButtons() {
        boolean hasSelection = selectedEntry != null;
        boolean installed = hasSelection && isInstalled(selectedEntry);
        boolean local = hasSelection && selectedEntry.local();
        boolean assetsIncomplete = installed && gamePaths.hasMarker(selectedEntry.id())
                && !gamePaths.assetsComplete(selectedEntry.id());
        if (installButton != null) {
            installButton.setText(local ? "本地实例" : installed ? "修复 / 更新" : "下载");
            installButton.setEnabled(hasSelection && !busy && !local);
        }
        if (launchButton != null) {
            launchButton.setText(!installed ? "下载并启动"
                    : assetsIncomplete && config.downloadAssets() ? "补全资源并启动"
                    : "启动游戏");
            launchButton.setEnabled(hasSelection && !busy && manifest != null);
        }
        if (cancelButton != null) {
            cancelButton.setVisible(busy);
            cancelButton.setEnabled(busy);
        }
        if (logButton != null) {
            logButton.setEnabled(!busy);
        }
        if (downloadDetailButton != null) {
            downloadDetailButton.setEnabled(hasSelection && !busy);
        }
        if (contentInstallButton != null) {
            contentInstallButton.setEnabled(!busy && contentList != null
                    && contentList.getSelectedValue() != null);
        }
    }

    private void startInstall(boolean thenLaunch) {
        if (busy || selectedEntry == null) {
            return;
        }
        VersionManifest.VersionEntry entry = selectedEntry;
        if (entry.local()) {
            statusLabel.setText("本地实例已存在，直接启动即可");
            statusLabel.setForeground(Theme.MUTED);
            return;
        }
        if (RegionGate.restricted()) {
            RegionGate.showGuide(this);
            return;
        }
        beginTask("安装 Minecraft " + entry.id());
        new SwingWorker<Installer.InstallResult, Void>() {
            @Override
            protected Installer.InstallResult doInBackground() throws Exception {
                return new Installer(config, taskToken, taskProgress)
                        .install(entry, config.downloadAssets());
            }

            @Override
            protected void done() {
                try {
                    Installer.InstallResult result = get();
                    metaCache.put(entry.id(), result.meta());
                    refreshInstalledCache();
                    versionList.repaint();
                    if (selectedEntry != null && selectedEntry.id().equals(entry.id())) {
                        selectedMeta = result.meta();
                        updateDetailForSelection();
                        updateJavaHint();
                    }
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(1);
                    finishTask();
                    statusLabel.setText("安装完成 · " + Formatting.bytes(result.estimatedBytes()));
                    statusLabel.setForeground(Theme.ACCENT);
                    if (thenLaunch) {
                        launchWithMeta(entry, result.meta());
                    }
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    if (!LauncherFrame.isCancelled(cause) && activeTask != null) {
                        taskCenter.fail(activeTask, messageOf(cause, "失败"));
                        refreshTasksButton();
                    }
                    finishTask();
                    if (LauncherFrame.isCancelled(cause)) {
                        statusLabel.setText("已取消");
                        statusLabel.setForeground(Theme.MUTED);
                    } else {
                        Log.error("安装失败", cause);
                        statusLabel.setText("安装失败");
                        statusLabel.setForeground(Theme.DANGER);
                        showError("安装失败", messageOf(cause, "请检查网络连接后重试。"));
                    }
                }
            }
        }.execute();
    }

    private void startLaunch() {
        if (busy || selectedEntry == null) {
            return;
        }
        String username = config.username();
        if (!Config.isValidUsername(username)) {
            openAccountsDialog();
            username = config.username();
            if (!Config.isValidUsername(username)) {
                return;
            }
        }
        if (!applyGameDirFromField()) {
            return;
        }
        config.username(username);
        config.memoryMb(memorySlider.getValue());
        config.downloadAssets(assetsToggle.isSelected());
        config.autoMirror(mirrorToggle.isSelected());
        config.jvmArgs(jvmArgsField.getText());
        config.curseForgeApiKey(curseForgeKeyField.getText());
        config.windowWidth(widthSlider.getValue());
        config.windowHeight(heightSlider.getValue());
        JavaChoice javaChoice = (JavaChoice) javaCombo.getSelectedItem();
        config.javaPath(javaChoice == null || javaChoice.path() == null ? "" : javaChoice.path().toString());
        config.save();

        VersionManifest.VersionEntry entry = selectedEntry;
        if (!isInstalled(entry)) {
            if (RegionGate.restricted()) {
                RegionGate.showGuide(this);
            } else {
                startInstall(true);
            }
            return;
        }
        if (selectedMeta == null) {
            statusLabel.setText("正在读取版本信息，请稍后再次点击启动…");
            statusLabel.setForeground(Theme.WARNING);
            return;
        }
        launchWithMeta(entry, selectedMeta);
    }

    private void launchWithMeta(VersionManifest.VersionEntry entry, VersionMeta meta) {
        if (busy || launching) {
            return;
        }
        if (gameProcess != null && gameProcess.isAlive()) {
            if (!askYesNo("游戏已在运行", "检测到 Minecraft 仍在运行。仍要启动另一个实例吗？")) {
                return;
            }
        }
        JavaManager.JavaInfo java = resolveJava(meta);
        int required = meta.javaMajor();
        if (java == null || java.major() < required) {
            String component = meta.javaComponent() == null || meta.javaComponent().isBlank()
                    ? JavaManager.componentForMajor(required) : meta.javaComponent();
            String current = java == null ? "未检测到 Java" : "当前 Java " + java.major();
            if (!config.autoDownloadJava()) {
                String message = "此版本需要 Java " + required + "，" + current + "。\n\n"
                        + "是否从 Mojang 官方下载 Java " + required
                        + " 运行时？（约 100–120 MB，仅从官方源下载）";
                if (!askYesNo("需要 Java " + required, message)) {
                    return;
                }
            } else {
                statusLabel.setText("缺少 Java " + required + "，正在自动下载…");
                statusLabel.setForeground(Theme.WARNING);
            }
            if (required <= 8) {
                try {
                    if ("mac-os-arm64".equals(JavaManager.platformKey())) {
                        showError("无法自动安装 Java 8",
                                "Mojang 官方没有为 Apple Silicon 提供 Java 8 运行时。\n"
                                        + "请手动安装 Java 8，并在「Java 运行时」中选择它。");
                        return;
                    }
                } catch (IOException ignored) {
                }
            }
            startJavaInstall(entry, meta, component, required);
            return;
        }
        if (required <= 8 && java.major() > 8) {
            if (!askYesNo("兼容性提示", "此版本官方要求 Java 8，当前选择的是 Java " + java.major()
                    + "。\n旧版本可能无法正常启动。仍要继续吗？")) {
                return;
            }
        }

        if (selectedEntry != null && selectedEntry.id().equals(entry.id())
                && isInstalled(entry)
                && gamePaths.hasMarker(entry.id()) && !gamePaths.assetsComplete(entry.id())) {
            if (RegionGate.restricted()) {
                RegionGate.showGuide(this);
                return;
            }
            if (config.downloadAssets()) {
                if (askYesNo("资源不完整", "这个版本尚未下载完整资源（声音、语言、图标等）。\n"
                        + "现代版本缺少资源会直接崩溃。现在下载完整资源并启动吗？")) {
                    startInstall(true);
                }
                return;
            }
            if (!askYesNo("资源不完整", "你关闭了「完整资源」下载。\n"
                    + "缺少资源文件时，现代版本可能无法启动或崩溃。仍要继续吗？")) {
                return;
            }
        }

        Path gameContentDir = config.effectiveGameDir(entry.id());
        lastGameContentDir = gameContentDir;
        SkinStore.copyForGame(config, config.accountId(), gameContentDir);
        launching = true;
        LaunchProgress launchProgress = new LaunchProgress(this, entry.id());
        launchProgress.setVisible(true);
        launchProgress.stage("检查游戏文件…");
        new SwingWorker<GameLauncher.LaunchResult, Void>() {
            @Override
            protected GameLauncher.LaunchResult doInBackground() throws Exception {
                SwingUtilities.invokeLater(() -> launchProgress.stage("构建启动命令…"));
                GameLauncher.LaunchResult result = GameLauncher.launch(config, meta, entry, java);
                return result;
            }

            @Override
            protected void done() {
                launching = false;
                launchProgress.dispose();
                try {
                    GameLauncher.LaunchResult result = get();
                    Process process = result.process();
                    gameProcess = process;
                    lastGameLog = result.logFile();
                    final Path crashLog = result.logFile();
                    config.lastVersion(entry.id());
                    config.save();
                    statusLabel.setText("游戏已启动 · PID " + process.pid() + " · 日志已保存");
                    statusLabel.setForeground(Theme.ACCENT);
                    process.onExit().thenAccept(exited -> SwingUtilities.invokeLater(() -> {
                        if (gameProcess != exited) {
                            return;
                        }
                        if (exited.exitValue() == 0) {
                            statusLabel.setText("游戏已退出");
                            statusLabel.setForeground(Theme.DIM);
                        } else {
                            statusLabel.setText("游戏异常退出（代码 " + exited.exitValue() + "）· 正在分析崩溃");
                            statusLabel.setForeground(Theme.DANGER);
                            CrashAnalyzer.Result crash = CrashAnalyzer.analyze(crashLog, gameContentDir);
                            new CrashDialog(LauncherFrame.this, crash, exited.exitValue()).setVisible(true);
                        }
                    }));
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    if (cause instanceof GameLauncher.MissingFilesException) {
                        if (RegionGate.restricted()) {
                            RegionGate.showGuide(LauncherFrame.this);
                        } else if (askYesNo("缺少游戏文件", cause.getMessage()
                                + "\n\n是否现在下载 / 修复并启动？")) {
                            startInstall(true);
                        }
                        return;
                    }
                    Log.error("启动失败", cause);
                    statusLabel.setText("启动失败");
                    statusLabel.setForeground(Theme.DANGER);
                    showError("启动失败", messageOf(cause, "请检查 Java 与游戏文件。"));
                }
            }
        }.execute();
    }

    private void startJavaInstall(VersionManifest.VersionEntry entry, VersionMeta meta,
                                  String component, int major) {
        if (busy) {
            return;
        }
        beginTask("安装 Java " + major);
        new SwingWorker<JavaManager.JavaInfo, Void>() {
            @Override
            protected JavaManager.JavaInfo doInBackground() throws Exception {
                return new JavaManager(config, taskProgress, taskToken).install(component, major);
            }

            @Override
            protected void done() {
                try {
                    JavaManager.JavaInfo info = get();
                    javaInfos.removeIf(existing -> existing.path().toAbsolutePath().normalize()
                            .equals(info.path().toAbsolutePath().normalize()));
                    javaInfos.add(0, info);
                    config.javaPath("");
                    config.save();
                    refreshJavaChoices(null);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(1);
                    statusLabel.setText("Java " + info.major() + " 安装完成");
                    statusLabel.setForeground(Theme.ACCENT);
                    finishTask();
                    launchWithMeta(entry, meta);
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    if (!LauncherFrame.isCancelled(cause) && activeTask != null) {
                        taskCenter.fail(activeTask, messageOf(cause, "失败"));
                        refreshTasksButton();
                    }
                    finishTask();
                    if (LauncherFrame.isCancelled(cause)) {
                        statusLabel.setText("已取消");
                        statusLabel.setForeground(Theme.MUTED);
                    } else {
                        Log.error("Java 运行时安装失败", cause);
                        statusLabel.setText("Java 安装失败");
                        statusLabel.setForeground(Theme.DANGER);
                        showError("Java 安装失败", messageOf(cause,
                                "可以手动安装 Java " + major + "，然后在设置中选择。"));
                    }
                }
            }
        }.execute();
    }

    private void beginTask() {
        beginTask("下载 / 安装任务");
    }

    private void beginTask(String title) {
        busy = true;
        activeTask = taskCenter.start(title, taskSource());
        refreshTasksButton();
        taskToken = new CancellationToken();
        taskProgress = new TaskProgress();
        taskProgress.indeterminate(true);
        taskProgress.stage("准备中…");
        progressBar.setIndeterminate(true);
        progressBar.setValue(0);
        if (progressTimer != null) {
            progressTimer.stop();
        }
        progressTimer = new Timer(90, e -> updateProgress());
        progressTimer.start();
        setControlsEnabled(false);
        updateButtons();
        cancelButton.setVisible(true);
        cancelButton.setEnabled(true);
    }

    private void updateProgress() {
        if (taskProgress == null) {
            return;
        }
        taskCenter.update(activeTask, taskProgress);
        progressBar.setIndeterminate(taskProgress.isIndeterminate());
        progressBar.setValue(taskProgress.total() > 0
                ? (double) taskProgress.done() / taskProgress.total() : 0);
        String text = taskProgress.stage();
        int percent = taskProgress.percent();
        if (percent >= 0 && !taskProgress.isIndeterminate()) {
            text += " · " + percent + "%";
        }
        long remaining = taskProgress.remaining();
        if (remaining >= 0 && !taskProgress.isIndeterminate()) {
            text += " · 剩余 " + Formatting.bytes(remaining);
            if (taskProgress.bytesPerSecond() > 1) {
                text += " · " + Formatting.bytes(Math.round(taskProgress.bytesPerSecond())) + "/s";
            }
        }
        statusLabel.setText(text);
        statusLabel.setForeground(Theme.ACCENT);
    }

    private void finishTask() {
        busy = false;
        taskCenter.finish(activeTask);
        activeTask = null;
        refreshTasksButton();
        if (progressTimer != null) {
            progressTimer.stop();
            progressTimer = null;
        }
        setControlsEnabled(true);
        updateButtons();
        cancelButton.setVisible(false);
        updateDetailForSelection();
        updateJavaHint();
        updateContentSelection();
    }

    private void cancelTask() {
        if (taskToken != null) {
            taskToken.cancel();
            taskCenter.cancel(activeTask);
            refreshTasksButton();
            statusLabel.setText("正在取消…");
            statusLabel.setForeground(Theme.WARNING);
            cancelButton.setEnabled(false);
        }
    }

    private String taskSource() {
        if ("download".equals(currentTop)
                && ("content".equals(currentDownloadCard)
                || "projectDetail".equals(currentDownloadCard))) {
            String source = "curseforge".equals(contentSource()) ? "CurseForge" : "Modrinth";
            String version = contentVersionField == null ? "" : contentVersionField.getText().trim();
            return source + (version.isEmpty() ? "" : " · MC " + version);
        }
        if ("versionDetail".equals(currentDownloadCard)) {
            if (!"vanilla".equals(versionLoaderType)) {
                return "Mojang 官方 + " + switch (versionLoaderType) {
                    case "fabric" -> "Fabric Meta";
                    case "quilt" -> "Quilt Meta";
                    case "forge" -> "Forge Maven";
                    case "neoforge" -> "NeoForge Maven";
                    default -> "加载器源";
                } + (config.autoMirror() ? " + BMCLAPI 镜像" : "");
            }
            return "Mojang 官方" + (config.autoMirror() ? " + BMCLAPI 镜像" : "");
        }
        return "Mojang 官方" + (config.autoMirror() ? " + BMCLAPI 镜像" : "");
    }

    private void openVersionPicker() {
        List<VersionManifest.VersionEntry> entries = allVersions.isEmpty()
                ? manifest == null ? List.of() : manifest.versions() : allVersions;
        if (entries.isEmpty()) {
            showError("版本列表尚未加载", "请稍后再试。");
            return;
        }
        new VersionsDialog(this, "选择游戏版本", entries, this::useVersion).setVisible(true);
    }

    private void openInstancePicker() {
        List<VersionManifest.VersionEntry> entries = allVersions.stream()
                .filter(entry -> entry.local() || installedCache.contains(entry.id()))
                .toList();
        if (entries.isEmpty()) {
            showError("没有找到游戏实例", "当前游戏文件夹的 versions 目录里没有可用实例。");
            return;
        }
        new VersionsDialog(this, "游戏实例 · " + entries.size(), entries,
                this::useVersion).setVisible(true);
    }

    private void useVersion(VersionManifest.VersionEntry entry) {
        useVersion(entry, false);
    }

    private void useVersion(VersionManifest.VersionEntry entry, boolean launch) {
        if (entry == null) {
            return;
        }
        pendingLaunch = launch;
        if (searchField != null) {
            searchField.setText("");
        }
        config.filter("all");
        config.save();
        applyFilter();
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).id().equals(entry.id())) {
                versionList.setSelectedIndex(i);
                versionList.ensureIndexIsVisible(i);
                break;
            }
        }
        selectPage("launch");
    }

    private void updateVersionPicker() {
        VersionManifest.VersionEntry entry = selectedEntry;
        if (versionPickerButton != null) {
            versionPickerButton.setText("版本列表");
        }
        if (instanceButton != null) {
            List<VersionManifest.VersionEntry> versions = allVersions.isEmpty()
                    ? LocalVersions.scan(gamePaths) : allVersions;
            int count = 0;
            for (VersionManifest.VersionEntry version : versions) {
                if (version.local() || installedCache.contains(version.id())) {
                    count++;
                }
            }
            instanceButton.setText(count == 0 ? "游戏实例 · 0" : "游戏实例 · " + count);
        }
    }

    private void openTasksDialog() {
        new TasksDialog(this, taskCenter, this::cancelTask).setVisible(true);
    }

    void cancelCurrentTask() {
        cancelTask();
    }

    private void refreshTasksButton() {
        if (tasksButton == null) {
            return;
        }
        int running = taskCenter.running();
        tasksButton.setText(running > 0 ? "任务 " + running : "任务");
        tasksButton.setPreferredSize(new Dimension(running > 0 ? 74 : 64, 24));
        tasksButton.setMaximumSize(new Dimension(running > 0 ? 74 : 64, 24));
        tasksButton.revalidate();
        tasksButton.repaint();
    }

    private void setControlsEnabled(boolean enabled) {
        if (searchField != null) {
            searchField.setEnabled(enabled);
        }
        versionList.setEnabled(enabled);
        refreshButton.setEnabled(enabled && !manifestLoading);
        gameDirField.setEnabled(enabled);
        javaCombo.setEnabled(enabled);
        memorySlider.setEnabled(enabled && !config.autoMemory());
        assetsToggle.setEnabled(enabled);
        mirrorToggle.setEnabled(enabled);
        jvmArgsField.setEnabled(enabled);
        curseForgeKeyField.setEnabled(enabled);
        if (autoJvmToggle != null) {
            autoJvmToggle.setEnabled(enabled);
        }
        widthSlider.setEnabled(enabled);
        heightSlider.setEnabled(enabled);
        releaseFilter.setEnabled(enabled);
        snapshotFilter.setEnabled(enabled);
        allFilter.setEnabled(enabled);
        openDirButton.setEnabled(enabled);
        if (contentSearchField != null) {
            contentSearchField.setEnabled(enabled);
            contentVersionField.setEnabled(enabled);
            contentSourceCombo.setEnabled(enabled);
            contentLoaderCombo.setVisible("mod".equals(contentType));
            contentLoaderCombo.setEnabled(enabled && "mod".equals(contentType));
            contentSearchButton.setEnabled(enabled);
            contentRefreshButton.setEnabled(enabled);
            contentList.setEnabled(enabled);
            contentDetailButton.setEnabled(enabled && contentList.getSelectedValue() != null);
            if (contentInstallButton != null) {
                contentInstallButton.setEnabled(enabled && contentList.getSelectedValue() != null);
            }
        }
    }

    private boolean applyGameDirFromField() {
        if (gameDirField == null) {
            return false;
        }
        String value = gameDirField.getText().trim();
        if (value.isEmpty()) {
            gameDirField.setText(gamePaths.gameDir().toString());
            return true;
        }
        if (value.equals("~") || value.startsWith("~/")) {
            value = System.getProperty("user.home") + value.substring(1);
        }
        Path path;
        try {
            path = Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            showError("游戏目录无效", "请输入有效的文件夹路径。");
            return false;
        }
        path = Config.normalizeGameDir(path);
        if (!path.equals(gamePaths.gameDir())) {
            gamePaths = new GamePaths(path);
            config.gameDir(path);
            config.save();
            refreshAllVersions();
            refreshInstalledCache();
            versionList.repaint();
            updateDetailForSelection();
        }
        gameDirField.setText(path.toString());
        gameDirField.setCaretPosition(0);
        return true;
    }

    private void chooseAccountsDirectory() {
        Path selected = Ui.chooseDirectory(this, "选择账户保存目录", config.accountsDir());
        if (selected == null) {
            return;
        }
        config.accountsDir(selected);
        config.save();
        reloadAccounts();
        statusLabel.setText("账户目录已修改");
        statusLabel.setForeground(Theme.ACCENT);
    }

    private void importHmclDirectories() {
        List<HmclImporter.Entry> entries = HmclImporter.detect();
        if (entries.isEmpty()) {
            showError("未找到 HMCL 游戏目录", "没有在常见 HMCL 配置位置找到游戏目录。\n"
                    + "也可以点「选择」直接选择 HMCL 使用的 .minecraft 文件夹。");
            return;
        }
        Object selected;
        if (entries.size() == 1) {
            selected = entries.get(0);
        } else {
            selected = JOptionPane.showInputDialog(this,
                    "选择要使用的 HMCL 游戏目录：", "读取 HMCL 目录",
                    JOptionPane.PLAIN_MESSAGE, null, entries.toArray(), entries.get(0));
        }
        if (!(selected instanceof HmclImporter.Entry entry)) {
            return;
        }
        if (!Files.isDirectory(entry.path().resolve("versions"))) {
            showError("目录不可用", "这个 HMCL 游戏目录里没有 versions 文件夹：\n" + entry.path());
            return;
        }
        gameDirField.setText(entry.path().toString());
        applyGameDirFromField();
        statusLabel.setText("已读取 HMCL 游戏目录 · " + entry.path().getFileName());
        statusLabel.setForeground(Theme.ACCENT);
    }

    private void adoptHmclGameDirIfEmpty() {
        if (!LocalVersions.scan(gamePaths).isEmpty()) {
            return;
        }
        for (HmclImporter.Entry entry : HmclImporter.detect()) {
            if (!Files.isDirectory(entry.path().resolve("versions"))) {
                continue;
            }
            gamePaths = new GamePaths(entry.path());
            config.gameDir(entry.path());
            config.save();
            if (gameDirField != null) {
                gameDirField.setText(entry.path().toString());
            }
            Log.info("自动使用 HMCL 游戏目录: " + entry.path());
            return;
        }
    }

    private void chooseInstanceFolder() {
        Path chosen = Ui.chooseDirectory(this, "选择已有的 Minecraft 实例目录",
                gamePaths.gameDir());
        if (chosen == null) {
            return;
        }
        Path candidate = chosen;
        if (!Files.isDirectory(candidate.resolve("versions"))
                && Files.isDirectory(candidate.resolve(".minecraft/versions"))) {
            candidate = candidate.resolve(".minecraft");
        }
        if (!Files.isDirectory(candidate.resolve("versions"))) {
            showError("不是有效的实例目录", "这个文件夹里没有 versions 目录。\n"
                    + "请选择包含 versions/<版本>/<版本>.json 的 .minecraft 或整合包实例目录。");
            return;
        }
        gameDirField.setText(candidate.toString());
        if (applyGameDirFromField()) {
            statusLabel.setText("已导入实例 · " + candidate.getFileName());
            statusLabel.setForeground(Theme.ACCENT);
            refreshAllVersions();
            refreshInstalledCache();
            applyFilter();
        }
    }

    private void chooseGameDirectory() {
        Path selected = Ui.chooseDirectory(this, "选择 Minecraft 游戏目录", gamePaths.gameDir());
        if (selected != null) {
            gameDirField.setText(selected.toString());
            applyGameDirFromField();
        }
    }

    private void chooseJava() {
        Path path = Ui.chooseFile(this, "选择 java 可执行文件", null, null);
        if (path == null) {
            return;
        }
        JavaManager.Probe probe = JavaManager.probe(path);
        if (probe.major() <= 0) {
            showError("无效的 Java", "无法运行这个文件，或它不是 Java 可执行文件。");
            return;
        }
        JavaManager.JavaInfo info = new JavaManager.JavaInfo(path, probe.major(),
                "Java " + probe.major() + " · " + probe.vendor(), null, false);
        javaInfos.removeIf(existing -> existing.path().toAbsolutePath().normalize()
                .equals(path.toAbsolutePath().normalize()));
        javaInfos.add(0, info);
        config.addJavaPath(path);
        config.javaPath(path.toString());
        config.save();
        refreshJavaChoices(path);
    }

    private void openGameDirectory() {
        try {
            Files.createDirectories(gamePaths.gameDir());
            openPath(gamePaths.gameDir());
        } catch (IOException ex) {
            showError("无法打开目录", ex.getMessage());
        }
    }

    private void openRecentLog() {
        Path target = lastGameLog;
        if (target == null || !Files.isRegularFile(target)) {
            try (Stream<Path> stream = Files.list(gamePaths.gameLogsDir())) {
                target = stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".log"))
                        .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                        .orElse(null);
            } catch (IOException ignored) {
            }
        }
        if (target == null) {
            target = Log.file();
        }
        if (target != null && Files.isRegularFile(target)) {
            openPath(target);
        } else {
            statusLabel.setText("还没有可用的日志");
            statusLabel.setForeground(Theme.MUTED);
        }
    }

    public void showCrashDemo() {
        new CrashDialog(this, new CrashAnalyzer.Result(
                "Description: Exception in server tick loop",
                java.util.List.of("examplemod-1.2.jar", "sodium-0.5.jar",
                        "create-0.5.1.jar"),
                "java.lang.NullPointerException: Cannot invoke \"BlockState.getBlock()\" because \"state\" is null\n"
                        + "\tat com.example.examplemod.Foo.tick(Foo.java:42)\n"
                        + "\tat net.minecraft.client.Minecraft.tick(Minecraft.java:1)\n",
                null), 1).setVisible(true);
    }

    private void openUrl(String url) {
        Browser.open(url);
    }

    private void openPath(Path path) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                showError("无法打开", path.toString());
            }
        } catch (IOException | UnsupportedOperationException ex) {
            showError("无法打开", path + "\n" + ex.getMessage());
        }
    }

    private void refreshAllVersions() {
        if (manifest == null) {
            return;
        }
        Map<String, VersionManifest.VersionEntry> byId = new LinkedHashMap<>();
        java.util.Set<String> officialIds = new java.util.HashSet<>();
        for (VersionManifest.VersionEntry official : manifest.versions()) {
            officialIds.add(official.id());
        }
        for (VersionManifest.VersionEntry local : LocalVersions.scan(gamePaths)) {
            if (!officialIds.contains(local.id())) {
                byId.put(local.id(), local);
            }
        }
        for (VersionManifest.VersionEntry official : manifest.versions()) {
            byId.put(official.id(), official);
        }
        allVersions = new ArrayList<>(byId.values());
        updateVersionPicker();
    }

    private boolean isInstalled(VersionManifest.VersionEntry entry) {
        if (entry == null) {
            return false;
        }
        if (entry.local()) {
            return Files.isRegularFile(gamePaths.versionJson(entry.id()));
        }
        return gamePaths.isInstalled(entry.id());
    }

    private void refreshInstalledCache() {
        installedCache.clear();
        for (VersionManifest.VersionEntry entry : allVersions) {
            if (isInstalled(entry)) {
                installedCache.add(entry.id());
            }
        }
        updateVersionPicker();
    }

    private void closeApplication() {
        if (busy && taskToken != null) {
            if (!askYesNo("任务正在进行", "下载或安装仍在进行。确定退出吗？")) {
                return;
            }
            taskToken.cancel();
        }
        if (memorySlider != null) {
            config.memoryMb(memorySlider.getValue());
        }
        if (assetsToggle != null) {
            config.downloadAssets(assetsToggle.isSelected());
        }
        if (mirrorToggle != null) {
            config.autoMirror(mirrorToggle.isSelected());
        }
        if (jvmArgsField != null) {
            config.jvmArgs(jvmArgsField.getText());
        }
        if (curseForgeKeyField != null) {
            config.curseForgeApiKey(curseForgeKeyField.getText());
        }
        if (widthSlider != null) {
            config.windowWidth(widthSlider.getValue());
        }
        if (heightSlider != null) {
            config.windowHeight(heightSlider.getValue());
        }
        if (gameDirField != null) {
            applyGameDirFromField();
        }
        config.save();
        dispose();
        System.exit(0);
    }

    private void showError(String title, String message) {
        ModernDialogs.message(this, title, message);
    }

    private boolean askYesNo(String title, String message) {
        return ModernDialogs.confirm(this, title, message);
    }

    public void saveScreenshot(Path path) throws IOException {
        JPanel content = (JPanel) getContentPane();
        int width = Math.max(1, content.getWidth());
        int height = Math.max(1, content.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        content.paint(g);
        g.dispose();
        Files.createDirectories(path.toAbsolutePath().getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    private static Throwable unwrap(Exception ex) {
        if (ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
    }

    private static boolean isCancelled(Throwable cause) {
        return cause instanceof CancellationToken.CancelledException
                || cause instanceof java.util.concurrent.CancellationException;
    }

    private static String messageOf(Throwable cause, String fallback) {
        if (cause == null) {
            return fallback;
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message + (fallback == null || fallback.isBlank() ? "" : "\n\n" + fallback);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private record JavaChoice(String label, Path path) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final class VersionRenderer extends JPanel implements javax.swing.ListCellRenderer<VersionManifest.VersionEntry> {
        private final JLabel idLabel = new JLabel();
        private final JLabel metaLabel = new JLabel();
        private final Ui.Pill latest = new Ui.Pill("最新", Theme.alpha(Theme.ACCENT, 28),
                Theme.ACCENT, Theme.alpha(Theme.ACCENT, 90));
        private final Ui.Pill installed = new Ui.Pill("已安装", Theme.CARD_ALT,
                Theme.DIM, Theme.BORDER);
        private final Ui.Pill localPill = new Ui.Pill("本地", Theme.alpha(Theme.ACCENT, 28),
                Theme.ACCENT, Theme.alpha(Theme.ACCENT, 90));
        private final InstanceIcon icon = new InstanceIcon(30);
        private final JPanel badges = new JPanel();
        private final JPanel text = new JPanel();

        private VersionRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(9, 0));
            setBorder(new EmptyBorder(5, 10, 5, 10));

            add(icon, BorderLayout.WEST);

            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            idLabel.setFont(Theme.BODY_BOLD);
            idLabel.setForeground(Theme.TEXT);
            metaLabel.setFont(Theme.MONO);
            metaLabel.setForeground(Theme.DIM);
            idLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(idLabel);
            text.add(Ui.vgap(2));
            text.add(metaLabel);
            add(text, BorderLayout.CENTER);

            badges.setOpaque(false);
            badges.setLayout(new BoxLayout(badges, BoxLayout.X_AXIS));
            badges.add(latest);
            badges.add(Ui.hgap(5));
            badges.add(installed);
            badges.add(Ui.hgap(5));
            badges.add(localPill);
            add(badges, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends VersionManifest.VersionEntry> list,
                                                      VersionManifest.VersionEntry value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            idLabel.setText(value.id());
            metaLabel.setText(value.displayType() + "  ·  " + Formatting.date(value.releaseTime()));
            latest.setVisible(manifest != null && !value.local()
                    && value.id().equals(manifest.latestRelease()));
            installed.setVisible(!value.local() && installedCache.contains(value.id()));
            localPill.setVisible(value.local());
            Path content = config.isolateVersions()
                    ? config.effectiveGameDir(value.id()) : config.gameDir();
            icon.setInstance(value.id(), gamePaths.versionJson(value.id()), content);
            if (isSelected) {
                idLabel.setForeground(Theme.TEXT);
                metaLabel.setForeground(Theme.MUTED);
            } else {
                idLabel.setForeground(Theme.TEXT);
                metaLabel.setForeground(value.type().equals("snapshot") ? Theme.ORANGE : Theme.DIM);
            }
            putClientProperty("selected", isSelected);
            Integer hoverIndex = (Integer) list.getClientProperty("hoverIndex");
            putClientProperty("hover", hoverIndex != null && hoverIndex == index);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            int width = getWidth();
            int height = getHeight();
            boolean selected = Boolean.TRUE.equals(getClientProperty("selected"));
            if (!selected && Boolean.TRUE.equals(getClientProperty("hover"))) {
                g2.setColor(Theme.HOVER);
                g2.fillRoundRect(2, 2, width - 4, height - 4, 9, 9);
            }
            if (selected) {
                g2.setColor(Theme.SEL);
                g2.fillRoundRect(2, 2, width - 4, height - 4, 9, 9);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(2, 8, 3, height - 16, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class TypeIcon extends JComponent {
        private String type = "release";
        private boolean selected;

        private TypeIcon() {
            setPreferredSize(new Dimension(28, 28));
            setMinimumSize(new Dimension(28, 28));
            setMaximumSize(new Dimension(28, 28));
        }

        private void setType(String type, boolean selected) {
            this.type = type == null ? "release" : type;
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            int size = Math.min(getWidth(), getHeight());
            g2.setColor(selected ? Theme.ACCENT : Theme.CARD_ALT);
            g2.fillRoundRect(0, 0, size, size, 9, 9);
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, size, size, 9, 9));
            if ("release".equals(type)) {
                int cell = Math.max(2, size / 12);
                int block = cell * 10;
                int ox = (size - block) / 2;
                int oy = (size - block) / 2;
                Color[] palette = {
                        new Color(0xD8D8D8), new Color(0xAFAFAF), new Color(0x5A5A5A),
                        new Color(0x3F3F3F)
                };
                int[][] pixels = {
                        {0, 1, 0, 1, 1, 0, 1, 0, 1, 0},
                        {1, 0, 1, 0, 1, 1, 0, 1, 0, 1},
                        {0, 1, 1, 1, 0, 1, 1, 0, 1, 1},
                        {2, 2, 3, 2, 2, 3, 2, 2, 3, 2},
                        {3, 2, 2, 3, 2, 2, 2, 3, 2, 3},
                        {2, 3, 2, 2, 3, 2, 3, 2, 2, 2},
                        {3, 2, 3, 2, 2, 3, 2, 3, 2, 3},
                        {2, 2, 3, 2, 3, 2, 2, 2, 3, 2},
                        {3, 2, 2, 3, 2, 2, 3, 2, 2, 3},
                        {2, 3, 2, 2, 3, 2, 2, 3, 2, 2}
                };
                for (int row = 0; row < 10; row++) {
                    for (int col = 0; col < 10; col++) {
                        g2.setColor(palette[pixels[row][col]]);
                        g2.fillRect(ox + col * cell, oy + row * cell, cell, cell);
                    }
                }
                g2.dispose();
                return;
            }
            Color ink = selected ? Theme.ACCENT_TEXT
                    : type.equals("snapshot") ? Theme.ORANGE : Theme.MUTED;
            int cx = size / 2;
            int cy = size / 2;
            int s = Math.max(5, size / 3);
            java.awt.Polygon top = new java.awt.Polygon(
                    new int[]{cx, cx + s, cx, cx - s},
                    new int[]{cy - s, cy - s / 2, cy, cy - s / 2}, 4);
            java.awt.Polygon left = new java.awt.Polygon(
                    new int[]{cx - s, cx, cx, cx - s},
                    new int[]{cy - s / 2, cy, cy + s, cy + s / 2}, 4);
            java.awt.Polygon right = new java.awt.Polygon(
                    new int[]{cx + s, cx, cx, cx + s},
                    new int[]{cy - s / 2, cy, cy + s, cy + s / 2}, 4);
            g2.setColor(ink);
            g2.fill(top);
            g2.setColor(Theme.alpha(ink, 205));
            g2.fill(left);
            g2.setColor(Theme.alpha(ink, 155));
            g2.fill(right);
            g2.dispose();
        }
    }
    private final class ContentRenderer extends JPanel implements ListCellRenderer<Modrinth.Project> {
        private final JLabel title = new JLabel();
        private final JLabel description = new JLabel();
        private final JLabel meta = new JLabel();
        private final Ui.Pill type = new Ui.Pill("MOD", Theme.CARD_ALT, Theme.DIM, Theme.BORDER);
        private final JPanel top = new JPanel(new BorderLayout(8, 0));
        private final Ui.ProjectIcon icon = new Ui.ProjectIcon(46);
        private final JPanel body = new JPanel(new BorderLayout());

        private ContentRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(6, 10, 6, 10));
            add(icon, BorderLayout.WEST);
            top.setOpaque(false);
            title.setFont(Theme.BODY_BOLD);
            title.setForeground(Theme.TEXT);
            top.add(title, BorderLayout.WEST);
            top.add(type, BorderLayout.EAST);
            body.setOpaque(false);
            body.add(top, BorderLayout.NORTH);
            description.setFont(Theme.TINY);
            description.setForeground(Theme.MUTED);
            body.add(description, BorderLayout.CENTER);
            meta.setFont(Theme.MONO);
            meta.setForeground(Theme.DIM);
            body.add(meta, BorderLayout.SOUTH);
            add(body, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Modrinth.Project> list,
                                                      Modrinth.Project value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            title.setText(config.forceModTranslation()
                    ? contentChineseTitles.getOrDefault(value.id(), value.title())
                    : value.title());
            icon.setUrl(value.iconUrl());
            String text = value.summaryZh() != null ? value.summaryZh() : value.description();
            if (text == null) {
                text = "";
            }
            description.setText(ellipsize(text, description.getFont(), 560));
            description.setToolTipText(text);
            meta.setText(value.author() + "  ·  " + Formatting.bytes(value.downloads()) + " 下载"
                    + (value.summaryZh() != null ? "  ·  简介来自 MC百科" : ""));
            type.setText(switch (value.projectType()) {
                case "resourcepack" -> "RESOURCEPACK";
                case "modpack" -> "MODPACK";
                case "shader" -> "SHADER";
                default -> "MOD";
            });
            putClientProperty("selected", isSelected);
            Integer hoverIndex = (Integer) list.getClientProperty("hoverIndex");
            putClientProperty("hover", hoverIndex != null && hoverIndex == index);
            return this;
        }

        private String ellipsize(String text, java.awt.Font font, int maxWidth) {
            java.awt.FontMetrics metrics = getFontMetrics(font);
            if (metrics.stringWidth(text) <= maxWidth) {
                return text;
            }
            String suffix = "…";
            int end = text.length();
            while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > maxWidth) {
                end--;
            }
            return end == 0 ? suffix : text.substring(0, end) + suffix;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            int width = getWidth();
            int height = getHeight();
            if (Boolean.TRUE.equals(getClientProperty("hover"))
                    && !Boolean.TRUE.equals(getClientProperty("selected"))) {
                g2.setColor(Theme.HOVER);
                g2.fillRoundRect(2, 2, width - 4, height - 4, 9, 9);
            }
            if (Boolean.TRUE.equals(getClientProperty("selected"))) {
                g2.setColor(Theme.SEL);
                g2.fillRoundRect(2, 2, width - 4, height - 4, 7, 7);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(2, 9, 3, height - 18, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private final class ProjectVersionRenderer extends JPanel implements ListCellRenderer<Modrinth.Version> {
        private final JLabel title = new JLabel();
        private final JLabel meta = new JLabel();

        private ProjectVersionRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(5, 10, 5, 10));
            title.setFont(Theme.BODY_BOLD);
            title.setForeground(Theme.TEXT);
            meta.setFont(Theme.MONO);
            meta.setForeground(Theme.DIM);
            add(title, BorderLayout.NORTH);
            add(meta, BorderLayout.CENTER);
            putClientProperty("selected", false);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Modrinth.Version> list,
                                                      Modrinth.Version value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            title.setText(value.versionNumber().isBlank() ? value.name() : value.versionNumber());
            String games = String.join(", ", value.gameVersions());
            String loaders = String.join(", ", value.loaders());
            meta.setText(games + (loaders.isBlank() ? "" : "  ·  " + loaders)
                    + "  ·  " + (value.primaryFile() == null ? "无文件" : value.primaryFile().filename()));
            putClientProperty("selected", isSelected);
            Integer hoverIndex = (Integer) list.getClientProperty("hoverIndex");
            putClientProperty("hover", hoverIndex != null && hoverIndex == index);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.prepare(g);
            boolean selected = Boolean.TRUE.equals(getClientProperty("selected"));
            if (!selected && Boolean.TRUE.equals(getClientProperty("hover"))) {
                g2.setColor(Theme.HOVER);
                g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 9, 9);
            }
            if (selected) {
                g2.setColor(Theme.SEL);
                g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 9, 9);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(2, 8, 3, getHeight() - 16, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

}
