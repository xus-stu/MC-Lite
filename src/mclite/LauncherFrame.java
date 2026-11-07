package mclite;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
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
import javax.swing.event.HyperlinkEvent;
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
import java.util.Collections;
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

    final Config config;
    GamePaths gamePaths;
    private VersionManifest manifest;
    List<VersionManifest.VersionEntry> allVersions = List.of();
    private VersionListCard versionListCard;
    private final Map<String, VersionMeta> metaCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, VersionMeta> eldest) {
                    return size() > 24;
                }
            });
    final Set<String> installedCache = new HashSet<>();
    private final List<JavaManager.JavaInfo> javaInfos = new ArrayList<>();
    private ConfigPage configPage;
    private LaunchPage launchPage;

    private Ui.AccentButton tasksButton;
    private JLabel detailTitle;
    private JTextArea detailMeta;
    private JLabel statusLabel;
    private JLabel sourceLabel;
    private JLabel countLabel;
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
    private String pendingLaunchId = "";
    private InstancesPage instancesPage;
    private SettingsPage settingsPage;

    private final java.util.ArrayDeque<Runnable> installQueue = new java.util.ArrayDeque<>();
    private Ui.AccentButton headerBackButton;
    private String currentTop = "download";
    private String currentDownloadCard = "versions";
    private DownloadPage downloadPage;
    private LauncherPluginsPanel launcherPluginsPanel;
    private JPanel sidebar;
    private Ui.AccentButton sidebarToggle;
    private ContentPage contentPage;
    private final ContentCache contentCache = new ContentCache();
    private final java.util.Map<String, Boolean> installedProjectCache = new java.util.HashMap<>();
    private boolean contentSearching;
    private int contentGeneration;
    private final java.util.Map<String, String> contentChineseTitles = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, java.util.Optional<McMod.Entry>> mcmodLookupCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private String versionDetailId;
    private VersionDetailPage versionDetailPage;
    private final java.util.List<RecommendationRow> recommendationRows = new java.util.ArrayList<>();

    private record RecommendationRow(Recommendations.Item item, Ui.ToggleChip toggle) {
    }
    private Modrinth.Project detailProject;
    private ProjectDetailPage projectDetailPage;
    private int projectDependencyGeneration;
    private int projectVersionGeneration;
    private final Map<String, DependencyLink> projectDependencyLinks = new HashMap<>();

    private record DependencyLink(String projectId, String source, String title) {
    }
    private JLabel sidebarAccount;
    private Ui.SkinHead sidebarSkin;

    VersionManifest.VersionEntry selectedEntry;
    VersionMeta selectedMeta;
    private int selectionGeneration;
    private boolean refreshingList;
    private boolean busy;
    private boolean manifestLoading;
    private CancellationToken taskToken;
    private TaskProgress taskProgress;
    final TaskCenter taskCenter = new TaskCenter();
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
        ImageCache.configure(config.cacheDir().resolve("icons"));
        this.gamePaths = new GamePaths(config.gameDir());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        setSize(Math.min(Math.max(1100, config.launcherWidth()), Math.max(1100, screen.width)),
                Math.min(Math.max(720, config.launcherHeight()), Math.max(720, screen.height)));
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

    private long lastWakeRepaint;

    private void repaintAfterWake() {
        long now = System.currentTimeMillis();
        if (now - lastWakeRepaint < 700) {
            return;
        }
        lastWakeRepaint = now;
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
        headerBackButton = Ui.smallButton("< 返回", Ui.AccentButton.Style.SECONDARY);
        headerBackButton.setPreferredSize(new Dimension(64, 26));
        headerBackButton.setMaximumSize(new Dimension(64, 26));
        headerBackButton.setEnabled(false);
        headerBackButton.setToolTipText("返回上一级");
        headerBackButton.addActionListener(e -> goBack());
        identity.add(headerBackButton);
        identity.add(Ui.hgap(6));
        sidebarToggle = Ui.smallButton(config.sidebarCollapsed() ? "栏" : "<",
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
        form.add(LauncherLayout.lockedHeight(buildSidebarAccount()));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.lockedHeight(buildSidebarVersion()));
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
        downloadPage = new DownloadPage(this::scheduleSearch, this::setFilter, this::loadManifest,
                this::openVersionDetail, this::showDownloadTab, () -> RegionGate.showGuide(this));
        versionDetailPage = new VersionDetailPage(() -> showDownloadTab("versions"),
                this::onVersionLoaderChosen, this::installFromVersionDetail);
        projectDetailPage = new ProjectDetailPage(this::backToContent, this::loadProjectVersions,
                this::installProjectVersion, () -> openMcmod(detailProject),
                this::openDependencyLink, this::updateProjectVersionDetails, () -> busy);
        contentPage = new ContentPage(this::searchContent, this::refreshContent,
                this::openProjectDetail, this::startContentInstall, this::importLocalModpack,
                this::openMcmod, this::updateContentSelection, this::runContentSearch,
                this::isProjectInstalled);
        versionListCard = new VersionListCard(this::onVersionSelected, this::openVersionDetail);
        launcherPluginsPanel = new LauncherPluginsPanel(config, gamePaths);
        contentCardsPanel.add(downloadPage.build(versionListCard.build(new VersionRenderer()),
                contentPage.build(new ContentRenderer()), versionDetailPage.build(),
                projectDetailPage.build(new ProjectVersionRenderer()),
                launcherPluginsPanel), "download");
        launchPage = new LaunchPage(this, config);
        contentCardsPanel.add(launchPage.build(), "launch");
        instancesPage = new InstancesPage(this, this::copySelectedInstance,
                this::exportSelectedInstance, this::importInstanceZip, this::editInstanceMeta,
                config::gameDir);
        contentCardsPanel.add(instancesPage.build(), "instances");
        configPage = new ConfigPage(this, config, this::applyGameDirFromField, this::updateJavaHint);
        contentCardsPanel.add(configPage.build(), "config");
        settingsPage = new SettingsPage(this, config, taskCenter, this::updateLaunchInfo,
                this::canRestartUi);
        contentCardsPanel.add(settingsPage.build(), "settings");
        updateButtons();

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(true);
        content.setBackground(Theme.BG);
        // 页面只允许在 CENTER 区域内绘制，避免列表/图标越过状态栏。
        JPanel pageClip = new JPanel(new BorderLayout()) {
            @Override
            protected void paintChildren(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.clipRect(0, 0, getWidth(), getHeight());
                super.paintChildren(g2);
                g2.dispose();
            }
        };
        pageClip.setOpaque(false);
        pageClip.add(contentCardsPanel, BorderLayout.CENTER);
        content.add(pageClip, BorderLayout.CENTER);
        JPanel status = buildStatusbar();
        content.add(status, BorderLayout.SOUTH);
        content.setComponentZOrder(status, 0);
        return content;
    }

    int selectedMemoryMb() {
        return selectedMeta != null ? config.effectiveMemoryMb(selectedMeta)
                : config.effectiveMemoryMb(selectedEntry == null ? null : selectedEntry.id());
    }

    void refreshContentVisuals() {
        if (contentPage != null && contentPage.list() != null) {
            contentPage.list().repaint();
        }
    }

    void refreshSelectionUi() {
        updateDetailForSelection();
        updateRecommendations(versionDetailPage == null ? "vanilla" : versionDetailPage.loaderType());
    }

    boolean busyOrLaunching() {
        return busy || launching;
    }

    private boolean canRestartUi() {
        return !busyOrLaunching() && (gameProcess == null || !gameProcess.isAlive());
    }

    void updateAutoMemoryControls() {
        settingsPage.updateAutoMemoryControls();
    }

    void updateLaunchInfo() {
        if (launchPage == null) {
            return;
        }
        launchPage.setVersion(selectedEntry == null ? "未选择版本" : selectedEntry.id());
        updateVersionPicker();
        launchPage.setMeta(detailMeta == null ? "" : detailMeta.getText());
        launchPage.setAccountName(config.username());
        launchPage.setMemory((selectedMeta != null ? config.effectiveMemoryMb(selectedMeta)
                : config.effectiveMemoryMb(selectedEntry == null ? null : selectedEntry.id()))
                + " MB" + (config.autoMemory() ? "（自动）" : ""));
        String jvm = config.jvmArgs().isBlank() ? "" : config.jvmArgs();
        if (config.autoJvmTuning()) {
            jvm = jvm.isBlank() ? "自动优化（按 Java/版本选择 GC）"
                    : jvm + "  ·  自动优化";
        }
        launchPage.setJvm(jvm.isBlank() ? "无额外参数" : jvm);
        launchPage.setAssets(config.downloadAssets() ? "完整资源" : "仅核心文件");
        String java = launchPage.javaText();
        if (java == null || java.isBlank()) {
            launchPage.setJava("自动选择");
        }
    }

    void installDatapack() {
        if (busyOrLaunching()) {
            showError("任务进行中", "请等当前下载、安装或启动结束后再安装数据包。");
            return;
        }
        if (busy) {
            showError("暂时不能安装", "下载或安装任务正在进行，请稍后再试。");
            return;
        }
        VersionManifest.VersionEntry entry = instancesPage == null
                ? null : instancesPage.selected();
        if (entry == null) {
            showError("安装数据包", "请先在左侧选择一个实例。");
            return;
        }
        Path gameDir = config.effectiveGameDir(entry.id());
        String[] modes = {"仅当前存档", "全局数据包（应用到所有存档）"};
        String mode = ModernDialogs.choice(this, "安装数据包",
                "要把数据包安装到哪里？\n\n全局数据包会保存到实例的 datapacks 文件夹，"
                        + "并在每次启动前同步到所有存档。", modes, modes[0]);
        if (mode == null) {
            return;
        }
        boolean global = modes[1].equals(mode);
        java.util.List<Path> worlds = java.util.List.of();
        Path world = null;
        if (!global) {
            try {
                worlds = GlobalDatapacks.worlds(gameDir);
            } catch (IOException ex) {
                showError("读取存档失败", ex.getMessage());
                return;
            }
            if (worlds.isEmpty()) {
                showError("没有存档", "这个实例还没有创建世界。\n\n"
                        + "请先进入游戏创建一个世界；或选择「全局数据包」。");
                return;
            }
            if (worlds.size() == 1) {
                world = worlds.get(0);
            } else {
                String[] names = worlds.stream().map(path -> path.getFileName().toString())
                        .toArray(String[]::new);
                String selected = ModernDialogs.choice(this, "安装数据包",
                        "选择要安装数据包的存档：", names, names[0]);
                if (selected == null) {
                    return;
                }
                world = gameDir.resolve("saves").resolve(selected);
            }
        }
        Path archive = Ui.chooseFile(this, "选择数据包 ZIP", null, "zip");
        if (archive == null) {
            return;
        }
        try {
            GlobalDatapacks.validate(archive);
        } catch (IOException ex) {
            showError("不是有效的数据包", ex.getMessage());
            return;
        }
        if (global) {
            Path target = GlobalDatapacks.folder(gameDir).resolve(archive.getFileName().toString());
            if (Files.exists(target) && !askYesNo("覆盖全局数据包",
                    target.getFileName() + " 已存在，是否覆盖？")) {
                return;
            }
            try {
                int count = GlobalDatapacks.worlds(gameDir).size();
                GlobalDatapacks.install(gameDir, archive);
                statusLabel.setText("全局数据包已安装 · " + target.getFileName()
                        + " · 已同步到 " + count + " 个存档（新存档下次启动时同步）");
                statusLabel.setForeground(Theme.ACCENT);
                Log.info("安装全局数据包 " + target);
            } catch (IOException ex) {
                showError("安装全局数据包失败", ex.getMessage());
            }
            return;
        }
        Path datapacks = world.resolve("datapacks");
        Path target = datapacks.resolve(archive.getFileName().toString());
        try {
            Files.createDirectories(datapacks);
            if (Files.exists(target) && !askYesNo("覆盖数据包",
                    target.getFileName() + " 已存在，是否覆盖？")) {
                return;
            }
            Files.copy(archive, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            statusLabel.setText("数据包已安装 · " + world.getFileName() + " / "
                    + target.getFileName() + "（进入世界后 /reload 或重进世界）");
            statusLabel.setForeground(Theme.ACCENT);
            Log.info("安装数据包 " + target);
        } catch (IOException ex) {
            showError("安装数据包失败", ex.getMessage());
        }
    }

    void updateRecommendedMods() {
        if (busyOrLaunching()) {
            showError("任务进行中", "请等当前下载、安装或启动结束后再更新模组。");
            return;
        }
        if (busy) {
            showError("暂时不能更新", "下载或安装任务正在进行，请稍后再试。");
            return;
        }
        VersionManifest.VersionEntry entry = instancesPage == null
                ? null : instancesPage.selected();
        if (entry == null) {
            showError("更新推荐模组", "请先在左侧选择一个实例。");
            return;
        }
        Path dir = gamePaths.versionDir(entry.id());
        Path json = dir.resolve(entry.id() + ".json");
        if (!Files.isRegularFile(json)) {
            showError("更新推荐模组", "找不到版本 JSON：\n" + json);
            return;
        }
        VersionMeta meta;
        try {
            meta = LocalVersions.load(gamePaths, entry.id());
        } catch (Exception ex) {
            showError("更新推荐模组", "读取版本信息失败：" + ex.getMessage());
            return;
        }
        String loader = InstanceService.detectLoader(entry.id(), dir, meta);
        if (loader == null) {
            showError("没有模组加载器", "这个实例没有检测到 Fabric / Quilt / Forge / NeoForge，"
                    + "推荐模组不会加载。");
            return;
        }
        String gameVersion = InstanceService.detectBaseVersion(entry.id(), dir, json);
        java.util.List<Recommendations.Item> items = Recommendations
                .forLoader(loader, gameVersion).stream()
                .filter(Recommendations.Item::selected)
                .toList();
        if (items.isEmpty()) {
            showError("没有可更新的模组", "当前加载器没有推荐模组。");
            return;
        }
        if (!askYesNo("更新推荐模组", "将为 " + entry.id() + " 检查并更新 "
                + items.size() + " 个推荐优化模组。\n\n旧版本的同名模组会被替换，"
                + "mods 里的其他文件不会动。")) {
            return;
        }
        startRecommendations(items, entry.id(), loader, gameVersion);
    }

    /** Runs a heavy instance file operation off the EDT with the normal busy gates. */
    private void runInstanceJob(String startMessage, java.util.concurrent.Callable<String> job,
                                boolean selectResult) {
        if (busyOrLaunching() || (gameProcess != null && gameProcess.isAlive())) {
            showError("暂时不能操作", "下载、安装、启动或游戏运行中，请稍后再试。");
            return;
        }
        busy = true;
        setControlsEnabled(false);
        statusLabel.setText(startMessage);
        statusLabel.setForeground(Theme.DIM);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return job.call();
            }

            @Override
            protected void done() {
                busy = false;
                setControlsEnabled(true);
                try {
                    String result = get();
                    if (instancesPage != null) {
                        instancesPage.resetGroupFilter();
                    }
                    refreshAllVersions();
                    refreshInstalledCache();
                    refreshInstancesPage();
                    if (selectResult && result != null) {
                        VersionManifest.VersionEntry created = findLoadedVersion(result);
                        if (created != null) {
                            useVersion(created, false);
                        } else if (instancesPage != null) {
                            instancesPage.selectId(result);
                        }
                    }
                    statusLabel.setText(result == null ? "完成" : result);
                    statusLabel.setForeground(Theme.ACCENT);
                } catch (Exception ex) {
                    showError("操作失败", messageOf(unwrap(ex), "请检查磁盘空间和文件夹权限。"));
                }
                updateButtons();
            }
        }.execute();
    }

    private VersionManifest.VersionEntry findLoadedVersion(String id) {
        for (VersionManifest.VersionEntry entry : allVersions) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        for (VersionManifest.VersionEntry entry : LocalVersions.scan(gamePaths)) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private boolean isOfficialVersionId(String id) {
        for (VersionManifest.VersionEntry entry : allVersions) {
            if (entry.id().equals(id) && !entry.local()) {
                return true;
            }
        }
        return false;
    }

    void copySelectedInstance() {
        VersionManifest.VersionEntry entry = instancesPage == null ? null : instancesPage.selected();
        if (entry == null) {
            showError("复制实例", "请先在左侧选择一个实例。");
            return;
        }
        String value = ModernDialogs.input(this, "复制实例",
                "新实例名称（字母、数字、点、下划线、短横线）", entry.id() + "-copy");
        if (value == null || value.isBlank()) {
            return;
        }
        String newId = value.trim();
        if (!VersionIds.valid(newId) || !newId.matches("(?i)[a-z0-9._-]+")) {
            showError("名称无效", "实例名称只能包含字母、数字、点、下划线和短横线。");
            return;
        }
        if (isOfficialVersionId(newId)) {
            showError("名称被占用", "这个名字和官方版本相同，请换一个实例名称。");
            return;
        }
        InstanceMeta.Entry sourceMeta = InstanceMeta.load(config.gameDir()).get(entry.id());
        runInstanceJob("正在复制实例 " + entry.id() + "…", () -> {
            InstanceService.copy(gamePaths.versionsDir(), entry.id(), newId);
            if (sourceMeta != null) {
                InstanceMeta.put(config.gameDir(), newId, sourceMeta.group(), sourceMeta.note());
            }
            return "已复制实例 · " + newId;
        }, true);
    }

    void exportSelectedInstance() {
        VersionManifest.VersionEntry entry = instancesPage == null ? null : instancesPage.selected();
        if (entry == null) {
            showError("导出实例", "请先在左侧选择一个实例。");
            return;
        }
        Path target = Ui.chooseSaveFile(this, "导出实例 ZIP",
                Path.of(System.getProperty("user.home"), entry.id() + ".zip"));
        if (target == null) {
            return;
        }
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            target = target.resolveSibling(target.getFileName() + ".zip");
        }
        if (Files.exists(target) && !askYesNo("覆盖文件", "文件已存在：\n" + target + "\n\n要覆盖吗？")) {
            return;
        }
        Path finalTarget = target;
        InstanceMeta.Entry meta = InstanceMeta.load(config.gameDir()).get(entry.id());
        runInstanceJob("正在导出实例 " + entry.id() + "…", () -> {
            InstanceService.exportZip(gamePaths.versionsDir(), entry.id(), finalTarget,
                    meta == null ? "" : meta.group(), meta == null ? "" : meta.note());
            return "已导出实例 · " + finalTarget.getFileName();
        }, false);
    }

    void importInstanceZip() {
        Path archive = Ui.chooseFile(this, "导入实例 ZIP",
                Path.of(System.getProperty("user.home")), "zip");
        if (archive == null) {
            return;
        }
        String base = archive.getFileName().toString().replaceFirst("(?i)\\.zip$", "");
        String value = ModernDialogs.input(this, "导入实例", "导入后的实例名称", base);
        if (value == null || value.isBlank()) {
            return;
        }
        String newId = value.trim();
        if (isOfficialVersionId(newId)) {
            showError("名称被占用", "这个名字和官方版本相同，请换一个实例名称。");
            return;
        }
        runInstanceJob("正在导入实例 " + archive.getFileName() + "…", () -> {
            InstanceService.ImportedInstance imported = InstanceService.importZipWithMeta(
                    gamePaths.versionsDir(), archive, newId);
            if (!imported.group().isBlank() || !imported.note().isBlank()) {
                InstanceMeta.put(config.gameDir(), imported.id(), imported.group(), imported.note());
            }
            return "已导入实例 · " + imported.id();
        }, true);
    }

    void editInstanceMeta() {
        VersionManifest.VersionEntry entry = instancesPage == null ? null : instancesPage.selected();
        if (entry == null) {
            showError("实例备注", "请先在左侧选择一个实例。");
            return;
        }
        InstanceMeta.Entry current = InstanceMeta.load(config.gameDir()).get(entry.id());
        String group = ModernDialogs.input(this, "实例分组", "分组名称（留空表示不分组）",
                current == null ? "" : current.group());
        if (group == null) {
            return;
        }
        if ("全部分组".equals(group.trim())) {
            showError("分组名称无效", "「全部分组」是筛选器的保留名称，请换一个。");
            return;
        }
        String note = ModernDialogs.input(this, "实例备注", "备注（留空表示清除）",
                current == null ? "" : current.note());
        if (note == null) {
            return;
        }
        if (!InstanceMeta.put(config.gameDir(), entry.id(), group, note)) {
            showError("备注未保存", "实例备注写入失败，请检查游戏目录是否可写。");
            return;
        }
        refreshInstancesPage();
        statusLabel.setText("已更新实例备注 · " + entry.id());
        statusLabel.setForeground(Theme.ACCENT);
    }

    void renameSelectedInstance() {
        if (busy || launching) {
            showError("暂时不能重命名", "下载、安装或启动任务正在进行，请稍后再试。");
            return;
        }
        VersionManifest.VersionEntry entry = instancesPage == null
                ? null : instancesPage.selected();
        if (entry == null) {
            showError("重命名实例", "请先在左侧选择一个实例。");
            return;
        }
        Path versions = gamePaths.versionsDir().toAbsolutePath().normalize();
        Path oldDir = gamePaths.versionDir(entry.id()).toAbsolutePath().normalize();
        if (!oldDir.startsWith(versions) || !Files.isDirectory(oldDir)
                || !Files.isRegularFile(oldDir.resolve(entry.id() + ".json"))) {
            showError("重命名实例", "找不到实例文件夹或版本 JSON：\n" + oldDir);
            return;
        }
        if (gameProcess != null && gameProcess.isAlive()
                && config.effectiveGameDir(entry.id()).equals(lastGameContentDir)) {
            showError("游戏正在运行", "这个实例的 Minecraft 还在运行，请先退出游戏再重命名。");
            return;
        }
        String input = ModernDialogs.input(this, "重命名实例",
                "新的实例名称：", entry.id());
        if (input == null) {
            return;
        }
        String newId = input.trim();
        if (!VersionIds.valid(newId) || newId.equals(entry.id())) {
            showError("名称无效", "实例名称不能为空、不能包含路径分隔符，也不能和原名相同。");
            return;
        }
        if (isOfficialVersionId(newId)) {
            showError("名称被占用", "这个名字和官方版本相同，请换一个实例名称。");
            return;
        }
        Path newDir = gamePaths.versionDir(newId).toAbsolutePath().normalize();
        if (Files.exists(newDir)) {
            showError("名称已存在", "已经有同名实例或版本文件夹：\n" + newDir);
            return;
        }
        if (!askYesNo("重命名实例", "将 " + entry.id() + " 重命名为 " + newId
                + "？\n\n版本文件、mods、saves 和 config 都会一起改名。")) {
            return;
        }
        try {
            InstanceService.rename(gamePaths.gameDir(), entry.id(), newId, oldDir, newDir);
            metaCache.remove(entry.id());
            metaCache.remove(newId);
            installedCache.remove(entry.id());
            if (entry.id().equals(config.lastVersion())) {
                config.lastVersion(newId);
                config.save();
            }
            if (entry.id().equals(versionDetailId)) {
                versionDetailId = newId;
            }
            InstanceMeta.rename(config.gameDir(), entry.id(), newId);
            selectedEntry = new VersionManifest.VersionEntry(newId, "local",
                    null, null, "", 0, true);
            selectedMeta = null;
            refreshAllVersions();
            refreshInstalledCache();
            refreshInstancesPage();
            instancesPage.selectId(newId);
            updateDetailForSelection();
            statusLabel.setText("实例已重命名 · " + newId);
            statusLabel.setForeground(Theme.ACCENT);
        } catch (Exception ex) {
            Log.error("重命名实例失败", ex);
            showError("重命名失败", ex.getMessage() == null ? "无法重命名实例" : ex.getMessage());
        }
    }

    void deleteSelectedInstance() {
        if (busy || launching) {
            showError("暂时不能删除", "下载、安装或启动任务正在进行，请稍后再试。");
            return;
        }
        VersionManifest.VersionEntry entry = instancesPage == null
                ? null : instancesPage.selected();
        if (entry == null) {
            showError("删除实例", "请先在左侧选择一个实例。");
            return;
        }
        Path versions = gamePaths.versionsDir().toAbsolutePath().normalize();
        Path dir = gamePaths.versionDir(entry.id()).toAbsolutePath().normalize();
        if (!dir.startsWith(versions) || dir.equals(versions) || !Files.isDirectory(dir)) {
            showError("删除实例", "找不到实例文件夹：\n" + dir);
            return;
        }
        if (gameProcess != null && gameProcess.isAlive()
                && config.effectiveGameDir(entry.id()).equals(lastGameContentDir)) {
            showError("游戏正在运行", "这个实例的 Minecraft 还在运行，请先退出游戏再删除。");
            return;
        }
        String scope = config.isolateVersions()
                ? "包括该实例的版本文件、mods、saves、config。"
                : "只会删除版本文件；当前关闭了版本隔离，mods/saves 是共享的，不会被删除。";
        if (!askYesNo("删除实例 " + entry.id(),
                "将永久删除：\n" + dir + "\n\n" + scope
                        + "\n\n此操作不可恢复，确定继续吗？")) {
            return;
        }
        try {
            InstanceService.deleteTree(dir);
            InstanceMeta.remove(config.gameDir(), entry.id());
            metaCache.remove(entry.id());
            installedCache.remove(entry.id());
            if (entry.id().equals(config.lastVersion())) {
                config.lastVersion("");
                config.save();
            }
            if (selectedEntry != null && selectedEntry.id().equals(entry.id())) {
                selectedEntry = null;
                selectedMeta = null;
            }
            refreshAllVersions();
            refreshInstalledCache();
            refreshInstancesPage();
            if (selectedEntry == null && !instancesPage.isEmpty()) {
                selectedEntry = instancesPage.first();
                selectedMeta = null;
            }
            updateDetailForSelection();
            statusLabel.setText("已删除实例 · " + entry.id());
            statusLabel.setForeground(Theme.ACCENT);
        } catch (IOException ex) {
            Log.error("删除实例失败", ex);
            showError("删除实例失败", ex.getMessage() == null ? "无法删除实例文件夹" : ex.getMessage());
        }
    }

    private void refreshInstancesPage() {
        if (instancesPage != null) {
            instancesPage.refresh();
        }
    }

    private boolean verifyModsBeforeLaunch(VersionMeta meta, Path gameContentDir) {
        List<Path> jars = listFiles(gameContentDir.resolve("mods")).stream()
                .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".jar"))
                .toList();
        if (jars.isEmpty()) {
            return true;
        }
        String mainClass = meta.mainClass() == null ? "" : meta.mainClass();
        boolean loader = !mainClass.contains("net.minecraft.client.main.Main");
        if (!loader) {
            return askYesNo("模组不会加载",
                    "当前实例没有模组加载器，但 mods 文件夹里有 " + jars.size()
                            + " 个 jar。\n\n这些模组不会在游戏里加载。仍要启动吗？");
        }
        List<String> invalid = new ArrayList<>();
        for (Path jar : jars) {
            if (!looksLikeModJar(jar, mainClass)) {
                invalid.add(jar.getFileName().toString());
            }
        }
        if (!invalid.isEmpty()) {
            StringBuilder message = new StringBuilder("以下文件没有检测到当前加载器需要的模组信息：\n\n");
            int count = Math.min(6, invalid.size());
            for (int i = 0; i < count; i++) {
                message.append("· ").append(invalid.get(i)).append('\n');
            }
            if (invalid.size() > count) {
                message.append("· 还有 ").append(invalid.size() - count).append(" 个\n");
            }
            message.append("\n它们可能不是模组，或者版本/加载器不匹配，游戏里可能不会加载。仍要启动吗？");
            Log.warn("启动前模组检查发现 " + invalid.size() + " 个可疑文件: " + invalid);
            return askYesNo("模组可能不会加载", message.toString());
        }
        Log.info("启动前模组检查通过：" + jars.size() + " 个模组，加载器 " + mainClass);
        return true;
    }

    private static boolean looksLikeModJar(Path jar, String mainClass) {
        try (java.util.jar.JarFile file = new java.util.jar.JarFile(jar.toFile())) {
            String lower = mainClass.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("fabric")) {
                return file.getEntry("fabric.mod.json") != null;
            }
            if (lower.contains("quilt")) {
                return file.getEntry("quilt.mod.json") != null
                        || file.getEntry("fabric.mod.json") != null;
            }
            if (lower.contains("forge") || lower.contains("neoforge")
                    || lower.contains("bootstraplauncher")) {
                return file.getEntry("META-INF/mods.toml") != null
                        || file.getEntry("META-INF/neoforge.mods.toml") != null
                        || file.getEntry("mcmod.info") != null;
            }
            return true;
        } catch (Exception ex) {
            return false;
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

    private void fetchLatestLoaderVersion(String type) {
        if (selectedEntry == null || "vanilla".equals(type)) {
            return;
        }
        String versionId = selectedEntry.id();
        statusLabel.setText("正在获取最新 " + type + " 版本…");
        statusLabel.setForeground(Theme.DIM);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return new LoaderInstaller(config, ProgressSink.NOOP, new CancellationToken())
                        .latestVersion(type, versionId);
            }

            @Override
            protected void done() {
                try {
                    String version = get();
                    if (versionDetailPage != null && versionDetailPage.loaderType().equals(type)
                            && versionDetailPage.loaderVersionText().isBlank()) {
                        versionDetailPage.setLoaderVersionText(version);
                    }
                    statusLabel.setText("最新 " + type + " 版本：" + version);
                    statusLabel.setForeground(Theme.DIM);
                } catch (Exception ex) {
                    if (versionDetailPage != null && versionDetailPage.loaderType().equals(type)
                            && versionDetailPage.loaderVersionText().isBlank()) {
                        statusLabel.setText("无法获取最新版本，可直接安装重试，或手动填写");
                        statusLabel.setForeground(Theme.WARNING);
                    }
                }
            }
        }.execute();
    }

    void runGameBenchmark(JLabel status) {
        if (selectedEntry == null || selectedMeta == null) {
            status.setText("请先在启动页选择一个已安装版本");
            return;
        }
        if (busy || launching) {
            status.setText("当前有任务运行，稍后再试");
            return;
        }
        VersionManifest.VersionEntry entry = selectedEntry;
        VersionMeta meta = selectedMeta;
        JavaManager.JavaInfo java = resolveJava(meta);
        if (java == null || java.major() < meta.javaMajor()) {
            status.setText("Java 不满足版本要求");
            return;
        }
        status.setText("实际游戏跑分中，约 12 秒…");
        new Thread(() -> {
            try {
                GameBenchmark.Result result = GameBenchmark.run(config, meta, entry, java);
                SwingUtilities.invokeLater(() -> status.setText("实际游戏分数 "
                        + result.score() + " · 启动 "
                        + result.startupMs() + " ms · CPU "
                        + result.cpuMs() + " ms · 平均帧 "
                        + (result.fpsMeasured()
                        ? String.format("%.1f", result.averageFps()) : "不可用")
                        + (result.exited() ? " · 游戏提前退出" : "")));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> status.setText("跑分失败：" + ex.getMessage()));
            }
        }, "mclite-game-benchmark").start();
    }

    void updateRecommendations(String loader) {
        JPanel recommendationPanel = versionDetailPage == null ? null : versionDetailPage.recommendations();
        if (recommendationPanel == null) {
            return;
        }
        recommendationPanel.removeAll();
        recommendationRows.clear();
        if (!config.recommendMods() || "vanilla".equals(loader)) {
            recommendationPanel.setVisible(false);
            recommendationPanel.revalidate();
            recommendationPanel.repaint();
            return;
        }
        recommendationPanel.add(Ui.vgap(8));
        recommendationPanel.add(Ui.label("推荐优化模组 · 可自行勾选（8 区块目标 300+ 帧）", Theme.TINY, Theme.DIM));
        recommendationPanel.add(Ui.vgap(4));
        String gameVersion = selectedEntry == null ? null : selectedEntry.id();
        for (Recommendations.Item item : Recommendations.forLoader(loader, gameVersion)) {
            Ui.ToggleChip chip = new Ui.ToggleChip(item.title() + "  ·  " + item.note());
            chip.setSelected(item.selected());
            chip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            chip.setAlignmentX(Component.LEFT_ALIGNMENT);
            recommendationPanel.add(chip);
            recommendationPanel.add(Ui.vgap(3));
            recommendationRows.add(new RecommendationRow(item, chip));
        }
        recommendationPanel.setVisible(true);
        recommendationPanel.revalidate();
        recommendationPanel.repaint();
    }

    private java.util.List<Recommendations.Item> selectedRecommendations() {
        java.util.List<Recommendations.Item> result = new java.util.ArrayList<>();
        for (RecommendationRow row : recommendationRows) {
            if (row.toggle().isSelected()) {
                result.add(row.item());
            }
        }
        return result;
    }

    private void installRecommendations(java.util.List<Recommendations.Item> items,
                                        String instanceId, String loader, String gameVersion) {
        if (items.isEmpty()) {
            return;
        }
        Config installConfig = config.copyForGameDir(config.effectiveGameDir(instanceId));
        Modrinth modrinth = new Modrinth(taskProgress, taskToken);
        String loaderName = loaderName(loader);
        int threads = Math.max(1, Math.min(3, items.size()));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                threads, runnable -> {
                    Thread thread = new Thread(runnable, "mclite-recommend");
                    thread.setDaemon(true);
                    return thread;
                });
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        try {
            for (Recommendations.Item item : items) {
                taskToken.check();
                String prefix = loaderName + " / " + item.title() + " / " + gameVersion;
                futures.add(pool.submit(() -> {
                    ProgressSink sink = new ProgressSink() {
                        @Override
                        public void total(long totalBytes) {
                            taskProgress.total(totalBytes);
                        }

                        @Override
                        public void addTotal(long bytes) {
                            taskProgress.addTotal(bytes);
                        }

                        @Override
                        public void add(long bytes) {
                            taskProgress.add(bytes);
                        }

                        @Override
                        public void rewind(long bytes) {
                            taskProgress.rewind(bytes);
                        }

                        @Override
                        public void stage(String text) {
                            // 进度统一显示为“加载器 / 模组 / 游戏版本”。
                            taskProgress.stage(prefix);
                        }

                        @Override
                        public void indeterminate(boolean value) {
                            taskProgress.indeterminate(value);
                        }
                    };
                    try {
                        taskProgress.stage(prefix);
                        Modrinth.Project project = modrinth.project(item.slug());
                        new ContentInstaller(installConfig, sink, taskToken)
                                .install(project, gameVersion, loader);
                    } catch (CancellationToken.CancelledException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        Log.warn("推荐模组安装失败 " + item.slug() + ": " + ex.getMessage());
                    }
                    return null;
                }));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                taskToken.check();
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new CancellationToken.CancelledException();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof CancellationToken.CancelledException cancelled) {
                        throw cancelled;
                    }
                    Log.warn("推荐模组下载失败: " + (cause == null ? "未知错误" : cause.getMessage()));
                }
            }
        } finally {
            pool.shutdownNow();
        }
        taskProgress.stage(loaderName + " / 完成 / " + gameVersion);
    }

    private void startRecommendations(java.util.List<Recommendations.Item> items,
                                      String instanceId, String loader, String gameVersion) {
        if (items.isEmpty()) {
            return;
        }
        String loaderName = loaderName(loader);
        beginTask(loaderName + " / 推荐模组 / " + gameVersion, "Modrinth");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                installRecommendations(items, instanceId, loader, gameVersion);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    finishTask();
                    refreshInstancesPage();
                    statusLabel.setText("推荐模组安装完成 · " + items.size() + " 个");
                    statusLabel.setForeground(Theme.ACCENT);
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    finishTask();
                    if (LauncherFrame.isCancelled(cause)) {
                        statusLabel.setText("推荐模组下载已取消");
                        statusLabel.setForeground(Theme.MUTED);
                    } else {
                        Log.error("推荐模组安装失败", cause);
                        statusLabel.setText("推荐模组安装失败");
                        statusLabel.setForeground(Theme.DANGER);
                    }
                }
            }
        }.execute();
    }

    private static String loaderName(String loader) {
        return switch (loader == null ? "" : loader) {
            case "fabric" -> "Fabric";
            case "quilt" -> "Quilt";
            case "forge" -> "Forge";
            case "neoforge" -> "NeoForge";
            default -> loader == null || loader.isBlank() ? "加载器" : loader;
        };
    }

    private void onVersionLoaderChosen(String type) {
        versionDetailPage.setInstallText("vanilla".equals(type)
                ? "下载原版" : "安装 " + switch (type) {
                    case "fabric" -> "Fabric";
                    case "quilt" -> "Quilt";
                    case "forge" -> "Forge";
                    case "neoforge" -> "NeoForge";
                    default -> "加载器";
                } + " 并启动");
        updateRecommendations(type);
        fetchLatestLoaderVersion(type);
    }

    private void openVersionDetail() {
        if (selectedEntry == null) {
            return;
        }
        versionDetailId = selectedEntry.id();
        versionDetailPage.reset(selectedEntry.id(), detailMeta == null ? "" : detailMeta.getText());
        updateRecommendations("vanilla");
        currentDownloadCard = "versionDetail";
        downloadPage.showCard("versionDetail");
        updateBackButton();
        downloadPage.setSubtabsVisible(false);
        downloadPage.setTitle("版本安装");
        downloadPage.setSubtitle("选择加载器并下载原版");
        downloadPage.setToolsVisible(false);
    }

    private void installFromVersionDetail() {
        VersionManifest.VersionEntry entry = selectedEntry;
        if (entry == null) {
            return;
        }
        if (versionDetailId != null && !versionDetailId.equals(entry.id())) {
            // 侧栏切换过版本，页面还是旧版本的加载器选择：刷新为当前选中版本再让用户确认。
            openVersionDetail();
            return;
        }
        if (entry.local()) {
            showError("本地实例", "本地实例已经存在，不需要再安装版本。请直接到「启动游戏」启动。");
            return;
        }
        if (RegionGate.restricted() && !isInstalled(entry)) {
            RegionGate.showGuide(this);
            return;
        }
        String loaderType = versionDetailPage.loaderType();
        if ("vanilla".equals(loaderType)) {
            showDownloadTab("versions");
            startInstall(entry, false);
            return;
        }
        String loaderVersion = versionDetailPage.loaderVersionText();
        VersionMeta meta = selectedMeta;
        JavaManager.JavaInfo resolvedJava = resolveJava(meta);
        java.util.List<Recommendations.Item> recommended = selectedRecommendations();
        if (busy) {
            enqueueInstall(() -> doInstallFromVersionDetail(
                    entry, loaderType, loaderVersion, resolvedJava, recommended));
            return;
        }
        doInstallFromVersionDetail(entry, loaderType, loaderVersion, resolvedJava, recommended);
    }

    private void doInstallFromVersionDetail(VersionManifest.VersionEntry entry,
                                            String loaderType, String loaderVersion,
                                            JavaManager.JavaInfo resolvedJava,
                                            java.util.List<Recommendations.Item> recommended) {
        if (busy) {
            enqueueInstall(() -> doInstallFromVersionDetail(
                    entry, loaderType, loaderVersion, resolvedJava, recommended));
            return;
        }
        beginTask("安装 " + entry.id() + " / " + loaderType);
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
                    version = installer.latestVersion(loaderType, entry.id());
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
                    metaCache.remove(loaderId);
                    selectedMeta = null;
                    finishTask(recommended.isEmpty());
                    refreshAllVersions();
                    refreshInstalledCache();
                    for (int i = 0; i < versionListCard.size(); i++) {
                        if (versionListCard.entryAt(i).id().equals(loaderId)) {
                            versionListCard.selectIndex(i);
                            break;
                        }
                    }
                    selectPage("launch");
                    onVersionSelected();
                    statusLabel.setText("加载器安装完成 · " + loaderId);
                    statusLabel.setForeground(Theme.ACCENT);
                    if (!recommended.isEmpty()) {
                        startRecommendations(recommended, loaderId, loaderType, entry.id());
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

    private boolean isProjectInstalled(Modrinth.Project project) {
        return installedProjectCache.computeIfAbsent(project.id(),
                id -> installedContent(project) != null);
    }

    private String installedContent(Modrinth.Project project) {
        if (project == null || selectedEntry == null) {
            return null;
        }
        Path dir = config.effectiveGameDir(selectedEntry.id());
        Path index = dir.resolve(".mclite/content.properties");
        if (!Files.isRegularFile(index)) {
            return null;
        }
        java.util.Properties properties = new java.util.Properties();
        try (var in = Files.newInputStream(index)) {
            properties.load(in);
        } catch (IOException ex) {
            return null;
        }
        String filename = properties.getProperty("project." + project.id());
        if (filename == null || filename.isBlank()) {
            return null;
        }
        for (String sub : new String[]{"mods", "resourcepacks", "shaderpacks"}) {
            Path file = dir.resolve(sub).resolve(filename);
            if (Files.isRegularFile(file)) {
                return filename;
            }
        }
        return null;
    }

    private void openProjectDetail() {
        if (contentPage.list() == null || contentPage.list().getSelectedValue() == null) {
            return;
        }
        showProjectDetail(contentPage.list().getSelectedValue());
    }

    private void backToContent() {
        currentDownloadCard = "content";
        downloadPage.showCard("content");
        updateBackButton();
        downloadPage.setSubtabsVisible(true);
        downloadPage.setTitle(switch (contentPage.type()) {
            case "mod" -> "模组下载";
            case "resourcepack" -> "资源包";
            case "shader" -> "光影下载";
            default -> "整合包";
        });
        downloadPage.setSubtitle("来自 Modrinth · 自动补齐必需前置 · SHA-1 校验");
    }

    private void showProjectDetail(Modrinth.Project project) {
        detailProject = project;
        boolean hasMcmod = detailProject.mcmodUrl() != null
                && !detailProject.mcmodUrl().isBlank();
        String summary = detailProject.summaryZh() != null
                ? detailProject.summaryZh() : detailProject.description();
        projectDetailPage.setProject(contentDisplayTitle(detailProject),
                detailProject.author() + "  ·  "
                        + Formatting.bytes(detailProject.downloads()) + " 下载  ·  "
                        + detailProject.projectType(),
                "<html><body style='width:720px'>" + Formatting.html(summary) + "</body></html>",
                detailProject.iconUrl());
        projectDetailPage.setMcmodVisible(hasMcmod);
        projectDetailPage.setMcmodTooltip(hasMcmod ? detailProject.mcmodUrl()
                : "MC百科没有收录这个项目");
        currentDownloadCard = "projectDetail";
        downloadPage.showCard("projectDetail");
        updateBackButton();
        downloadPage.setSubtabsVisible(false);
        downloadPage.setTitle("项目详情");
        downloadPage.setSubtitle("选择具体文件版本");
        downloadPage.setToolsVisible(false);
        loadProjectVersions();
    }

    private void loadProjectVersions() {
        if (detailProject == null) {
            return;
        }
        int generation = ++projectVersionGeneration;
        String projectId = detailProject.id();
        projectDetailPage.clearVersions();
        projectDetailPage.setInstallEnabled(false);
        statusLabel.setText("正在读取文件版本…");
        statusLabel.setForeground(Theme.DIM);
        String gameVersion = contentPage.versionField().getText().trim();
        String loader = "mod".equals(detailProject.projectType())
                ? contentPage.loader() : null;
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
                    if (generation != projectVersionGeneration || detailProject == null
                            || !detailProject.id().equals(projectId)) {
                        return;
                    }
                    List<Modrinth.Version> versions = get();
                    Log.info("项目文件版本数: " + versions.size());
                    for (Modrinth.Version version : versions) {
                        projectDetailPage.addVersion(version);
                    }
                    if (!versions.isEmpty()) {
                        projectDetailPage.selectFirstVersion();
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

    private void openDependencyLink(HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
            return;
        }
        String description = event.getDescription();
        String prefix = "mclite://dep/";
        if (description == null || !description.startsWith(prefix)) {
            return;
        }
        DependencyLink link = projectDependencyLinks.get(description.substring(prefix.length()));
        if (link == null) {
            return;
        }
        Modrinth.Project project = new Modrinth.Project(link.projectId(), link.projectId(),
                link.title(), "", "", 0, null, "mod", null, null, link.source());
        showProjectDetail(project);
    }

    private static String dependencyHtml(String body) {
        return "<html><body style='font-family:Menlo,Consolas,monospace;font-size:10px;"
                + "color:" + colorHex(Theme.MUTED) + ";margin:0;'>" + body + "</body></html>";
    }

    private static String colorHex(java.awt.Color color) {
        return String.format("#%06x", color.getRGB() & 0xffffff);
    }

    private void updateProjectVersionDetails(Modrinth.Version version) {
        if (projectDetailPage == null) {
            return;
        }
        int generation = ++projectDependencyGeneration;
        if (version == null) {
            projectDetailPage.setDependencyHtmlDocument(
                    dependencyHtml("选择一个文件版本<br>查看前置模组和内置模组"));
            projectDependencyLinks.clear();
            return;
        }
        projectDetailPage.setDependencyHtmlDocument(dependencyHtml("文件："
                + Formatting.html(version.primaryFile() == null
                ? "无" : version.primaryFile().filename())
                + "<br>游戏版本：" + Formatting.html(String.join(", ", version.gameVersions()))
                + "<br>加载器：" + Formatting.html(String.join(", ", version.loaders()))
                + "<br><br>正在读取前置…"));
        projectDependencyLinks.clear();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return dependencyDescription(version);
            }

            @Override
            protected void done() {
                if (generation != projectDependencyGeneration) {
                    return;
                }
                try {
                    projectDetailPage.setDependencyHtmlDocument(get());
                } catch (Exception ex) {
                    projectDetailPage.setDependencyHtmlDocument("读取前置失败：" + ex.getMessage());
                }
                projectDetailPage.scrollDependencyToTop();
            }
        }.execute();
    }

    private String dependencyDescription(Modrinth.Version version) {
        projectDependencyLinks.clear();
        StringBuilder html = new StringBuilder();
        if (version.primaryFile() != null) {
            html.append("<div>文件：").append(Formatting.html(version.primaryFile().filename()))
                    .append("</div>");
        }
        html.append("<div>游戏版本：").append(Formatting.html(
                String.join(", ", version.gameVersions()))).append("</div>");
        html.append("<div>加载器：").append(Formatting.html(
                String.join(", ", version.loaders()))).append("</div>");
        List<Modrinth.Dependency> required = new ArrayList<>();
        List<Modrinth.Dependency> embedded = new ArrayList<>();
        List<Modrinth.Dependency> optional = new ArrayList<>();
        for (Modrinth.Dependency dependency : version.dependencies()) {
            if ("required".equals(dependency.type())) {
                required.add(dependency);
            } else if ("embedded".equals(dependency.type())) {
                embedded.add(dependency);
            } else if ("optional".equals(dependency.type())) {
                optional.add(dependency);
            }
        }
        Modrinth modrinth = new Modrinth(new TaskProgress(), new CancellationToken());
        java.util.Map<String, String> names = new java.util.HashMap<>();
        html.append("<div style='height:8px'></div>");
        appendDependencies(html, "必需前置", required, modrinth, names);
        appendDependencies(html, "已内置模组", embedded, modrinth, names);
        appendDependencies(html, "可选前置", optional, modrinth, names);
        if (required.isEmpty() && embedded.isEmpty() && optional.isEmpty()) {
            html.append("<div>没有声明前置或内置模组。</div>");
        }
        html.append("<div style='height:8px'></div>");
        html.append("<div>安装时会自动补齐必需前置。</div>");
        return dependencyHtml(html.toString());
    }

    private void appendDependencies(StringBuilder html, String title,
                                    List<Modrinth.Dependency> dependencies,
                                    Modrinth modrinth, java.util.Map<String, String> names) {
        html.append("<div style='margin-top:8px;font-weight:bold;color:")
                .append(colorHex(Theme.TEXT)).append(";'>")
                .append(title).append("（").append(dependencies.size()).append("）</div>");
        if (dependencies.isEmpty()) {
            html.append("<div>· 无</div>");
            return;
        }
        for (Modrinth.Dependency dependency : dependencies) {
            String name = dependency.fileName();
            String projectId = dependency.projectId();
            try {
                if (dependency.versionId() != null) {
                    Modrinth.Version depVersion = modrinth.version(dependency.versionId());
                    if (projectId == null) {
                        projectId = depVersion.projectId();
                    }
                }
                if (projectId != null) {
                    name = names.computeIfAbsent(projectId, id -> {
                        try {
                            return modrinth.projectById(id).title();
                        } catch (Exception ex) {
                            return null;
                        }
                    });
                }
            } catch (Exception ignored) {
            }
            if (name == null || name.isBlank()) {
                name = projectId == null ? "未知前置" : projectId;
            }
            if (projectId == null || projectId.isBlank()) {
                html.append("<div>· ").append(Formatting.html(name)).append("</div>");
                continue;
            }
            String source = projectId.startsWith("cf:") ? "curseforge" : "modrinth";
            String key = Integer.toString(projectDependencyLinks.size());
            projectDependencyLinks.put(key, new DependencyLink(projectId, source, name));
            html.append("<div>· <a style='color:").append(colorHex(Theme.ACCENT))
                    .append(";text-decoration:none;' href='mclite://dep/").append(key)
                    .append("'>").append(Formatting.html(name)).append("</a></div>");
        }
    }

    private void installProjectVersion() {
        if (busy || detailProject == null) {
            return;
        }
        Modrinth.Version version = projectDetailPage.selectedVersion();
        if (version == null) {
            return;
        }
        if (RegionGate.restricted() && "modpack".equals(detailProject.projectType())) {
            RegionGate.showGuide(this);
            return;
        }
        String gameVersion = contentPage.versionField().getText().trim();
        String loader = "mod".equals(detailProject.projectType()) ? contentPage.loader() : null;
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
            if (result.versionId() != null) {
                config.lastVersion(result.versionId());
            }
            config.save();
            gamePaths = new GamePaths(instance);
            configPage.setGameDirText(instance.toString());
            refreshAllVersions();
            refreshInstalledCache();
            installedProjectCache.clear();
            contentPage.reevaluateFilter();
            applyFilter();
            if (result.versionId() != null) {
                for (int i = 0; i < versionListCard.size(); i++) {
                    if (versionListCard.entryAt(i).id().equals(result.versionId())) {
                        versionListCard.selectIndex(i);
                        revealVersion(i);
                        break;
                    }
                }
            }
        } else {
            refreshInstalledCache();
        }
        updateContentSelection();
    }

    void importLocalModpack() {
        if (busyOrLaunching()) {
            showError("任务进行中", "请等当前下载、安装或启动结束后再导入整合包。");
            return;
        }
        if (busy) {
            return;
        }
        Path archive = Ui.chooseFile(this, "选择整合包 ZIP", null, "zip", "mrpack");
        if (archive == null) {
            return;
        }
        String lower = archive.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".mrpack")) {
            showError("文件类型不支持", "请选择 .zip 或 .mrpack 整合包文件。");
            return;
        }
        String display = archive.getFileName().toString().replaceFirst("(?i)\\.(zip|mrpack)$", "");
        beginTask("导入整合包 " + display);
        new SwingWorker<ContentInstaller.Result, Void>() {
            @Override
            protected ContentInstaller.Result doInBackground() throws Exception {
                Path actual = resolveModpackArchive(archive, 0);
                String kind = archiveKind(actual);
                if ("mrpack".equals(kind)) {
                    return new ModpackInstaller(config, taskProgress, taskToken,
                            new Modrinth(taskProgress, taskToken))
                            .installArchive(actual, display);
                }
                if ("curseforge".equals(kind)) {
                    return new CurseForgeModpackInstaller(config, taskProgress, taskToken)
                            .installArchive(actual, display);
                }
                throw new java.io.IOException("不是支持的整合包：需要 modrinth.index.json 或 manifest.json");
            }

            @Override
            protected void done() {
                try {
                    ContentInstaller.Result result = get();
                    finishTask();
                    applyImportedModpack(result);
                    statusLabel.setText("整合包导入完成 · " + result.summary());
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
                        Log.error("整合包导入失败", cause);
                        statusLabel.setText("整合包导入失败");
                        statusLabel.setForeground(Theme.DANGER);
                        showError("整合包导入失败", messageOf(cause, "请确认 ZIP 是完整整合包。"));
                    }
                }
            }
        }.execute();
    }

    private Path resolveModpackArchive(Path archive, int depth) throws java.io.IOException {
        String kind = archiveKind(archive);
        if (!kind.isBlank()) {
            return archive;
        }
        if (depth >= 3) {
            throw new java.io.IOException("整合包嵌套层数过多，请手动解压后再导入。");
        }
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive.toFile())) {
            java.util.List<? extends java.util.zip.ZipEntry> entries = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .toList();
            if (entries.size() == 1) {
                java.util.zip.ZipEntry entry = entries.get(0);
                String name = entry.getName();
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    Path inner = config.cacheDir().resolve("pack-import")
                            .resolve("inner-" + System.nanoTime() + ".zip");
                    Files.createDirectories(inner.getParent());
                    try (var in = zip.getInputStream(entry)) {
                        Files.copy(in, inner, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        return resolveModpackArchive(inner, depth + 1);
                    } catch (java.io.IOException ex) {
                        byte[] data = Files.readAllBytes(inner);
                        String text = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();
                        if (text.length() > 300) {
                            text = text.substring(0, 300) + "…";
                        }
                        throw new java.io.IOException("外层 ZIP 里的 " + name
                                + " 不是有效的整合包。\n\n内层内容：\n" + text
                                + "\n\n这通常说明下载链接失效，文件只是一个错误响应。", ex);
                    }
                }
            }
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < Math.min(5, entries.size()); i++) {
                if (i > 0) {
                    names.append("\n");
                }
                names.append("· ").append(entries.get(i).getName());
            }
            throw new java.io.IOException("不是支持的整合包：根目录需要 manifest.json"
                    + " 或 modrinth.index.json。\n\n当前 ZIP 内容：\n" + names);
        } catch (java.util.zip.ZipException ex) {
            throw new java.io.IOException("不是有效的 ZIP 文件，可能下载不完整。", ex);
        }
    }

    private String archiveKind(Path archive) throws java.io.IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive.toFile())) {
            if (zip.getEntry("modrinth.index.json") != null) {
                return "mrpack";
            }
            if (zip.getEntry("manifest.json") != null) {
                return "curseforge";
            }
        }
        return "";
    }

    private void applyImportedModpack(ContentInstaller.Result result) {
        Path instance = result.gameDir();
        config.gameDir(instance);
        if (result.versionId() != null) {
            config.lastVersion(result.versionId());
        }
        config.save();
        gamePaths = new GamePaths(instance);
        configPage.setGameDirText(instance.toString());
        refreshAllVersions();
        refreshInstalledCache();
        installedProjectCache.clear();
        contentPage.reevaluateFilter();
        applyFilter();
        if (result.versionId() != null) {
            for (int i = 0; i < versionListCard.size(); i++) {
                if (versionListCard.entryAt(i).id().equals(result.versionId())) {
                    versionListCard.selectIndex(i);
                    revealVersion(i);
                    break;
                }
            }
        }
        refreshInstancesPage();
        if (result.versionId() != null) {
            instancesPage.selectId(result.versionId());
        }
        updateContentSelection();
        updateLaunchInfo();
    }

    private void showDownloadTab(String type) {
        if ("plugins".equals(type)) {
            downloadPage.setSubtabsVisible(true);
            downloadPage.setToolsVisible(false);
            downloadPage.setTitle("启动器插件");
            downloadPage.setSubtitle("MCLite 岛等可选组件 · 按实例/加载器匹配 · SHA-256 校验");
            currentDownloadCard = "plugins";
            downloadPage.showCard("plugins");
            downloadPage.selectTab("plugins");
            updateBackButton();
            launcherPluginsPanel.ensureLoaded();
            return;
        }
        boolean versions = "versions".equals(type);
        downloadPage.setSubtabsVisible(true);
        String previousType = contentPage.type();
        contentPage.setType(versions ? "versions" : type);
        boolean contentChanged = !versions && !type.equals(previousType);
        if (contentChanged) {
            contentPage.clearResults();
            contentPage.setPageIndex(0);
            contentPage.setLastSearchedVersion("");
        }
        currentDownloadCard = versions ? "versions" : "content";
        downloadPage.showCard(versions ? "versions" : "content");
        updateBackButton();
        downloadPage.setToolsVisible(versions);
        downloadPage.selectTab(type);
        contentPage.setLoaderVisible("mod".equals(type));
        contentPage.setImportVisible("modpack".equals(type));
        contentPage.installButton().setText(switch (type) {
            case "mod" -> "安装模组";
            case "resourcepack" -> "安装资源包";
            case "modpack" -> "安装整合包";
            case "shader" -> "安装光影";
            default -> "安装所选";
        });
        if (versions) {
            downloadPage.setTitle("下载中心");
            downloadPage.setSubtitle("Mojang 官方清单 · 选择版本下载或启动");
            applyFilter();
            SwingUtilities.invokeLater(() -> {
                int index = versionListCard.selectedIndex();
                if (index >= 0) {
                    revealVersion(index);
                }
            });
            return;
        }
        downloadPage.setTitle(switch (type) {
            case "mod" -> "模组下载";
            case "resourcepack" -> "资源包";
            case "shader" -> "光影下载";
            default -> "整合包";
        });
        downloadPage.setSubtitle("来自 Modrinth · 自动补齐必需前置 · SHA-1 校验");
        String contentVersionBefore = contentPage.versionField().getText().trim();
        updateContentVersionIfNeeded();
        String contentVersionAfter = contentPage.versionField().getText().trim();
        if ((contentChanged || !contentPage.hasResults()
                || !contentVersionBefore.equals(contentVersionAfter)
                || !contentVersionAfter.equals(contentPage.lastSearchedVersion()))
                && !contentSearching) {
            searchContent();
        }
    }

    private void updateContentVersionIfNeeded() {
        if (!contentPage.versionAuto() || contentPage.versionHasFocus()) {
            return;
        }
        String version = baseGameVersion();
        if (version == null || version.isBlank()
                || version.equals(contentPage.versionText())) {
            return;
        }
        contentPage.setVersionText(version);
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

    private java.util.Optional<McMod.Entry> lookupMcmod(Modrinth.Project project, int filter) {
        return mcmodLookupCache.computeIfAbsent(project.id(), ignored -> {
            String[] queries = {project.title(), project.slug()};
            for (String query : queries) {
                if (query == null || query.isBlank()) {
                    continue;
                }
                try {
                    for (McMod.Entry entry : new McMod().search(query, filter)) {
                        if (namesMatch(project.title(), entry.englishName())
                                || namesMatch(project.slug(), entry.englishName())) {
                            return java.util.Optional.of(entry);
                        }
                    }
                } catch (IOException ex) {
                    Log.warn("MC百科查询失败 " + query + ": " + ex.getMessage());
                }
            }
            return java.util.Optional.empty();
        });
    }

    private String contentDisplayTitle(Modrinth.Project project) {
        String english = project.title();
        if (!config.forceModTranslation()) {
            return english;
        }
        String chinese = contentChineseTitles.get(project.id());
        if (chinese == null || chinese.isBlank() || chinese.equalsIgnoreCase(english)) {
            return english;
        }
        String normalizedChinese = chinese.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        String normalizedEnglish = english.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (normalizedEnglish.length() >= 3 && normalizedChinese.contains(normalizedEnglish)) {
            return chinese;
        }
        // MC百科标题没有英文名时补上原版英文名，已有英文时不重复。
        return chinese + " (" + english + ")";
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

    private List<Modrinth.Project> searchProjects(String source, String query, String type,
                                                  String gameVersion, String loader, String sort)
            throws IOException {
        return searchProjects(source, query, type, gameVersion, loader, 0, sort);
    }

    private List<Modrinth.Project> searchProjects(String source, String query, String type,
                                                  String gameVersion, String loader, int offset,
                                                  String sort)
            throws IOException {
        if ("curseforge".equals(source)) {
            return new CurseForge(config.curseForgeApiKey(), ProgressSink.NOOP,
                    new CancellationToken()).search(query, type, gameVersion, loader,
                    offset, 20, sort);
        }
        return new Modrinth(ProgressSink.NOOP, new CancellationToken())
                .search(query, type, gameVersion, loader, offset, 20, sort);
    }

    private String detectLoader() {
        if (selectedEntry == null) {
            return null;
        }
        String versionId = selectedEntry.id();
        Path dir = gamePaths.versionDir(versionId);
        Path json = dir.resolve(versionId + ".json");
        if (Files.isRegularFile(json)) {
            try {
                VersionMeta meta = LocalVersions.load(gamePaths, versionId);
                String detected = InstanceService.detectLoader(versionId, dir, meta);
                if (detected != null) {
                    return detected;
                }
            } catch (Exception ex) {
                Log.warn("检测加载器失败: " + ex.getMessage());
            }
        }
        String id = versionId.toLowerCase(java.util.Locale.ROOT);
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
        contentPage.setPageIndex(0);
        runContentSearch();
    }

    private void refreshContent() {
        contentPage.setPageIndex(0);
        runContentSearch(true);
    }

    private void runContentSearch() {
        runContentSearch(false);
    }

    private void runContentSearch(boolean bypassCache) {
        if (contentPage.model() == null) {
            return;
        }
        installedProjectCache.clear();
        contentSearching = true;
        int generation = ++contentGeneration;
        final int pageNumber = contentPage.pageIndex();
        final int pageOffset = pageNumber * 20;
        if (!contentPage.pageLabelHasFocus()) {
            contentPage.setPageLabel(pageNumber + 1);
        }
        contentPage.setPageButtonsEnabled(pageNumber > 0, true);
        contentChineseTitles.clear();
        contentPage.clearResults();
        contentPage.setSelectionText("正在搜索 " + ("curseforge".equals(contentPage.source())
                ? "CurseForge" : "Modrinth") + "…");
        contentPage.setInstallEnabled(false);
        String query = contentPage.query();
        String gameVersion = contentPage.versionText();
        contentPage.setLastSearchedVersion(gameVersion);
        final String contentType = contentPage.type();
        String loader = "mod".equals(contentType) ? contentPage.loader() : null;
        saveCurseForgeKey();
        String source = contentPage.source();
        String sort = contentPage.sortValue();
        new SwingWorker<List<Modrinth.Project>, Void>() {
            @Override
            protected List<Modrinth.Project> doInBackground() throws Exception {
                ContentCache.Key cacheKey = new ContentCache.Key(source, contentType, query,
                        gameVersion, loader == null ? "" : loader, sort, pageNumber,
                        config.forceModTranslation());
                if (!bypassCache) {
                    ContentCache.Result cached = contentCache.get(cacheKey);
                    if (cached != null) {
                        contentChineseTitles.putAll(cached.chineseTitles());
                        return cached.projects();
                    }
                }
                boolean cjkQuery = containsCjk(query);
                List<Modrinth.Project> projects = cjkQuery ? new ArrayList<>()
                        : new ArrayList<>(searchProjects(source, query, contentType,
                        gameVersion, loader, pageOffset, sort));
                List<McMod.Entry> entries;
                try {
                    entries = new McMod().search(query, "modpack".equals(contentType) ? 2 : 0);
                } catch (IOException ex) {
                    Log.warn("MC百科搜索失败: " + ex.getMessage());
                    entries = List.of();
                }
                if (cjkQuery && entries.isEmpty()) {
                    projects.addAll(searchProjects(source, query, contentType, gameVersion, loader,
                            pageOffset, sort));
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
                                    contentType, gameVersion, loader, pageOffset, sort)));
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
                if (config.forceModTranslation()) {
                    java.util.Map<String, java.util.concurrent.Future<java.util.Optional<McMod.Entry>>> lookups =
                            new java.util.LinkedHashMap<>();
                    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors
                            .newFixedThreadPool(4, runnable -> {
                                Thread thread = new Thread(runnable, "mclite-mcmod-title");
                                thread.setDaemon(true);
                                return thread;
                            });
                    try {
                        int filter = "modpack".equals(contentType) ? 2 : 0;
                        for (Modrinth.Project project : enriched) {
                            if (contentChineseTitles.containsKey(project.id())) {
                                continue;
                            }
                            lookups.put(project.id(), pool.submit(() -> lookupMcmod(project, filter)));
                        }
                        for (int i = 0; i < enriched.size(); i++) {
                            Modrinth.Project project = enriched.get(i);
                            java.util.concurrent.Future<java.util.Optional<McMod.Entry>> future =
                                    lookups.get(project.id());
                            if (future == null) {
                                continue;
                            }
                            try {
                                java.util.Optional<McMod.Entry> found = future.get(
                                        8, java.util.concurrent.TimeUnit.SECONDS);
                                if (found.isPresent()) {
                                    McMod.Entry entry = found.get();
                                    contentChineseTitles.put(project.id(), entry.title());
                                    enriched.set(i, project.withMcmod(entry.summary(), entry.url()));
                                }
                            } catch (Exception ex) {
                                future.cancel(true);
                                Log.warn("MC百科中文名查询失败: " + ex.getMessage());
                            }
                        }
                    } finally {
                        pool.shutdownNow();
                    }
                }
                java.util.Map<String, String> cachedTitles = new java.util.HashMap<>();
                for (Modrinth.Project project : enriched) {
                    String chinese = contentChineseTitles.get(project.id());
                    if (chinese != null) {
                        cachedTitles.put(project.id(), chinese);
                    }
                }
                contentCache.put(cacheKey, new ContentCache.Result(enriched, cachedTitles));
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
                    contentPage.setResults(projects);
                    if (contentPage.listFade() != null) {
                        contentPage.listFade().fadeIn();
                    }
                    contentPage.selectionLabel().setText(projects.isEmpty()
                            ? "没有找到内容" : projects.size() + " 个结果 · 选择后安装");
                    if (!contentPage.pageLabelHasFocus()) {
                        contentPage.setPageLabel(pageNumber + 1);
                    }
                    contentPage.setPageButtonsEnabled(pageNumber > 0,
                            contentPage.resultCount() >= 20);
                    contentPage.selectFirst();
                } catch (Exception ex) {
                    Throwable cause = unwrap(ex);
                    Log.warn("Modrinth 搜索失败: " + messageOf(cause, ""));
                    contentPage.selectionLabel().setText("搜索失败 · " + messageOf(cause, ""));
                }
            }
        }.execute();
    }

    private void updateContentSelection() {
        if (contentPage.list() == null || contentPage.installButton() == null) {
            return;
        }
        Modrinth.Project project = contentPage.list().getSelectedValue();
        boolean selected = project != null;
        int selectedCount = contentPage.list().getSelectedIndices().length;
        contentPage.installButton().setEnabled(selected);
        contentPage.installButton().setText(selectedCount > 1 ? "安装 " + selectedCount + " 个"
                : switch (contentPage.type()) {
                    case "mod" -> "安装模组";
                    case "resourcepack" -> "安装资源包";
                    case "modpack" -> "安装整合包";
                    case "shader" -> "安装光影";
                    default -> "安装所选";
                });
        contentPage.setDetailEnabled(selected);
        contentPage.mcmodButton().setVisible(selected && project.mcmodUrl() != null
                && !project.mcmodUrl().isBlank());
        if (selected) {
            if (selectedCount > 1) {
                contentPage.selectionLabel().setText("已选择 " + selectedCount
                        + " 个内容 · 最多 3 个并发下载");
                contentPage.selectionLabel().setToolTipText("按住 Command 或 Ctrl 可多选");
                return;
            }
            String summary = project.summaryZh() == null ? project.description() : project.summaryZh();
            if (summary != null && summary.length() > 100) {
                summary = summary.substring(0, 98) + "…";
            }
            String installed = installedContent(project);
            String base = summary == null || summary.isBlank()
                    ? project.title() : "MC百科：" + summary;
            contentPage.selectionLabel().setText(installed == null ? base
                    : base + "  ·  已安装：" + installed);
            contentPage.selectionLabel().setToolTipText((project.mcmodUrl() == null
                    ? project.description() : project.summaryZh() + "\n" + project.mcmodUrl())
                    + (installed == null ? "" : "\n已安装文件：" + installed));
        } else {
            contentPage.selectionLabel().setText("选择一个项目");
        }
    }

    private void startContentInstall() {
        if (busy) {
            enqueueInstall(this::startContentInstall);
            return;
        }
        java.util.List<Modrinth.Project> projects = contentPage.list() == null
                ? java.util.List.of() : new java.util.ArrayList<>(contentPage.list().getSelectedValuesList());
        if (projects.isEmpty()) {
            return;
        }
        boolean hasModpack = projects.stream()
                .anyMatch(project -> "modpack".equals(project.projectType()));
        if (hasModpack && projects.size() > 1) {
            showError("整合包不能批量安装", "整合包请一次选择一个安装。");
            return;
        }
        Modrinth.Project project = projects.get(0);
        if (RegionGate.restricted() && "modpack".equals(project.projectType())) {
            RegionGate.showGuide(this);
            return;
        }
        String gameVersion = contentPage.versionField().getText().trim();
        if (gameVersion.isEmpty()) {
            showError("缺少游戏版本", "请填写该内容对应的 Minecraft 版本，例如 1.20.1。");
            return;
        }
        String contentType = contentPage.type();
        String loader = "mod".equals(contentType) ? contentPage.loader() : null;
        if ("mod".equals(contentType) && loader == null) {
            loader = detectLoader();
        }
        if ("mod".equals(contentType) && loader == null) {
            showError("需要模组加载器", "安装模组前，请先在「启动器配置」中导入带 Fabric/Forge 的实例，"
                    + "或在上方选择加载器。");
            return;
        }
        String chosenLoader = loader;
        if (projects.size() == 1) {
            String installed = installedContent(project);
            if (installed != null && !askYesNo("已经安装",
                    project.title() + " 已经安装为：\n" + installed
                            + "\n\n仍要重新安装或更新吗？")) {
                return;
            }
        }
        Config installConfig = config.copyForGameDir(config.effectiveGameDir(
                selectedEntry == null ? "" : selectedEntry.id()));
        if (projects.size() == 1) {
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
                        applyContentResult(project, result);
                        statusLabel.setText("安装完成 · " + result.summary());
                        statusLabel.setForeground(Theme.ACCENT);
                        updateContentSelection();
                    } catch (Exception ex) {
                        contentInstallFailed(ex);
                    }
                }
            }.execute();
            return;
        }

        beginTask("安装 " + projects.size() + " 个内容（最多 3 个并发）");
        new SwingWorker<java.util.List<ContentInstaller.Result>, Void>() {
            @Override
            protected java.util.List<ContentInstaller.Result> doInBackground() throws Exception {
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors
                        .newFixedThreadPool(Math.min(3, projects.size()), runnable -> {
                            Thread thread = new Thread(runnable, "mclite-content");
                            thread.setDaemon(true);
                            return thread;
                        });
                java.util.List<java.util.concurrent.Future<ContentInstaller.Result>> futures =
                        new java.util.ArrayList<>();
                try {
                    for (Modrinth.Project item : projects) {
                        taskToken.check();
                        futures.add(pool.submit(() -> new ContentInstaller(
                                installConfig, taskProgress, taskToken)
                                .install(item, gameVersion, chosenLoader)));
                    }
                    java.util.List<ContentInstaller.Result> results = new java.util.ArrayList<>();
                    for (java.util.concurrent.Future<ContentInstaller.Result> future : futures) {
                        taskToken.check();
                        try {
                            results.add(future.get());
                        } catch (java.util.concurrent.ExecutionException ex) {
                            taskToken.cancel();
                            Throwable cause = ex.getCause();
                            throw cause instanceof Exception exception
                                    ? exception : new java.io.IOException(cause);
                        }
                    }
                    return results;
                } finally {
                    pool.shutdownNow();
                }
            }

            @Override
            protected void done() {
                try {
                    java.util.List<ContentInstaller.Result> results = get();
                    finishTask();
                    refreshInstalledCache();
                    statusLabel.setText("安装完成 · " + results.size() + " 个内容");
                    statusLabel.setForeground(Theme.ACCENT);
                    updateContentSelection();
                } catch (Exception ex) {
                    contentInstallFailed(ex);
                }
            }
        }.execute();
    }

    private void contentInstallFailed(Exception ex) {
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
            sidebarToggle.setText("<");
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
                sidebarToggle.setText("栏");
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
            showDownloadTab(contentPage.type());
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
        if (contentPage.resultCount() > 0) {
            contentPage.selectIndex(0);
            openProjectDetail();
        }
    }

    public void openFirstVersionDetailForScreenshot() {
        if (versionListCard.selectedIndex() < 0 && !versionListCard.isEmpty()) {
            versionListCard.selectFirst();
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
        java.awt.Image skin = SkinStore.load(config, config.accountId());
        if (sidebarSkin != null) {
            sidebarSkin.setSkin(skin);
        }
        if (launchPage != null) {
            launchPage.setAccount(config.username(), skin);
        }
        if (configPage != null) {
            configPage.setAccount(config.username(), skin);
        }
        SkinStore.copyForGame(config, config.accountId());
        if (settingsPage != null) {
            settingsPage.updateAccountsLabel();
        }
        updateLaunchInfo();
    }

    void openAccountsDialog() {
        new AccountsDialog(this, config).setVisible(true);
        reloadAccounts();
    }

    void saveCurseForgeKey() {
        settingsPage.saveCurseForgeKey();
    }

    void openLogsDirectory() {
        try {
            Files.createDirectories(config.logsDir());
            openPath(config.logsDir());
        } catch (IOException ex) {
            showError("无法打开日志目录", ex.getMessage());
        }
    }

    void openConfigDirectory() {
        try {
            Files.createDirectories(config.appHome());
            openPath(config.appHome());
        } catch (IOException ex) {
            showError("无法打开数据目录", ex.getMessage());
        }
    }

    void startJavaDetection() {
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

    void openJavaManager() {
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

    void refreshJavaChoices(Path selectPath) {
        configPage.refreshJavaChoices(javaInfos, selectPath);
    }

    void updateJavaHint() {
        if (configPage == null || launchPage == null) {
            return;
        }
        if (selectedMeta == null) {
            if (javaInfos.isEmpty()) {
                setJavaHint("未检测到 Java；启动时会提示下载官方运行时", Theme.WARNING);
                launchPage.setJava("未检测到");
            } else {
                setJavaHint("自动选择；当前可用 Java " + javaInfos.get(0).major(), Theme.DIM);
                launchPage.setJava("Java " + javaInfos.get(0).major());
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
        configPage.setJavaHintTooltip(java == null ? null : java.path().toString());
        launchPage.setJava(java == null ? "未检测到"
                : "Java " + java.major() + (java.official() ? " · 官方" : ""));
    }

    private void setJavaHint(String text, Color color) {
        configPage.setJavaHint(text, color);
    }

    private JavaManager.JavaInfo resolveJava(VersionMeta meta) {
        JavaChoice choice = configPage.selectedJava();
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
        downloadPage.setRefreshEnabled(false);
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
                downloadPage.setRefreshEnabled(true);
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
        String query = downloadPage.query().toLowerCase();
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
            versionListCard.setEntries(filtered);
            downloadPage.selectFilter(filter);
            countLabel.setText(filtered.size() + " / " + allVersions.size() + " 个版本");
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
            versionListCard.selectIndex(target);
            versionListCard.reveal(target);
        } finally {
            refreshingList = false;
        }
        onVersionSelected();
        final int targetIndex = versionListCard.selectedIndex();
        SwingUtilities.invokeLater(() -> {
            if (targetIndex >= 0) {
                revealVersion(targetIndex);
            }
        });
    }

    private void revealVersion(int index) {
        versionListCard.revealWithMargin(index);
    }

    private void onVersionSelected() {
        VersionManifest.VersionEntry entry = versionListCard.selectedValue();
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
        updateAutoMemoryControls();
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
                        pendingLaunchId = "";
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
        if (!pendingLaunchId.isEmpty() && pendingLaunchId.equals(entry.id())) {
            pendingLaunchId = "";
            SwingUtilities.invokeLater(this::startLaunch);
        }
    }

    void updateDetailForSelection() {
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
        updateAutoMemoryControls();
        updateButtons();
    }

    void updateButtons() {
        if (launchPage == null || downloadPage == null || contentPage == null) {
            return;
        }
        boolean hasSelection = selectedEntry != null;
        boolean installed = hasSelection && isInstalled(selectedEntry);
        boolean local = hasSelection && selectedEntry.local();
        boolean assetsIncomplete = installed && gamePaths.hasMarker(selectedEntry.id())
                && !gamePaths.assetsComplete(selectedEntry.id());
        launchPage.setInstallButton(local ? "本地实例" : installed ? "修复 / 更新" : "下载",
                hasSelection && !local);
        launchPage.setLaunchButton(!installed ? "下载并启动"
                : assetsIncomplete && config.downloadAssets() ? "补全资源并启动"
                : "启动游戏", hasSelection && !busy && manifest != null);
        launchPage.setBusy(busy);
        downloadPage.setDetailEnabled(hasSelection && !busy);
        contentPage.setInstallEnabled(contentPage.list() != null
                && contentPage.list().getSelectedValue() != null);
    }

    void startInstall(boolean thenLaunch) {
        VersionManifest.VersionEntry entry = selectedEntry;
        if (entry == null) {
            return;
        }
        startInstall(entry, thenLaunch);
    }

    void startInstall(VersionManifest.VersionEntry entry, boolean thenLaunch) {
        if (RegionGate.restricted()) {
            RegionGate.showGuide(this);
            return;
        }
        if (busy) {
            boolean launch = thenLaunch && installQueue.isEmpty();
            enqueueInstall(() -> startInstall(entry, launch));
            return;
        }
        beginTask((entry.local() ? "检查 / 补齐 " : "安装 Minecraft ") + entry.id());
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
                    versionListCard.repaintList();
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

    void startLaunch() {
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
        settingsPage.persistControls();
        JavaChoice javaChoice = configPage.selectedJava();
        config.javaPath(javaChoice == null || javaChoice.path() == null ? "" : javaChoice.path().toString());
        config.save();

        VersionManifest.VersionEntry entry = selectedEntry;
        String parentVersion = InstanceService.inheritedParent(gamePaths.versionJson(entry.id()));
        if (parentVersion != null && !Files.isRegularFile(gamePaths.versionJson(parentVersion))) {
            if (askYesNo("游戏文件不完整", "这个实例依赖的 " + parentVersion
                    + " 还没有安装。\n\n是否现在补齐并启动？")) {
                startInstall(entry, true);
            }
            return;
        }
        if (!isInstalled(entry)) {
            if (RegionGate.restricted()) {
                RegionGate.showGuide(this);
            } else {
                startInstall(true);
            }
            return;
        }
        VersionMeta launchMeta = localMeta(entry, selectedMeta);
        if (launchMeta == null) {
            statusLabel.setText("正在读取版本信息，请稍后再次点击启动…");
            statusLabel.setForeground(Theme.WARNING);
            return;
        }
        if (config.integrityCheck()) {
            java.util.List<String> missing = Integrity.missing(config, launchMeta, entry.id());
            if (!missing.isEmpty()) {
                String[] options = {"补齐并启动", "仍要启动", "取消"};
                int choice = ModernDialogs.option(this, "游戏文件不完整",
                        "检测到游戏文件不完整：\n\n" + Integrity.summary(missing)
                                + "\n\n补齐会重新下载缺失文件。",
                        options, 0);
                if (choice == 0) {
                    startInstall(true);
                    return;
                }
                if (choice != 1) {
                    return;
                }
            }
        }
        launchWithMeta(entry, launchMeta);
    }

    private VersionMeta localMeta(VersionManifest.VersionEntry entry, VersionMeta fallback) {
        Path json = gamePaths.versionJson(entry.id());
        if (!Files.isRegularFile(json)) {
            return fallback;
        }
        try {
            VersionMeta local = LocalVersions.load(gamePaths, entry.id());
            metaCache.put(entry.id(), local);
            if (selectedEntry != null && selectedEntry.id().equals(entry.id())) {
                selectedMeta = local;
            }
            return local;
        } catch (Exception ex) {
            Log.warn("读取本地版本信息失败，使用缓存: " + ex.getMessage());
            return fallback;
        }
    }

    private void launchWithMeta(VersionManifest.VersionEntry entry, VersionMeta meta) {
        VersionMeta launchMeta = localMeta(entry, meta);
        if (busy || launching) {
            return;
        }
        if (gameProcess != null && gameProcess.isAlive()) {
            if (!askYesNo("游戏已在运行", "检测到 Minecraft 仍在运行。仍要启动另一个实例吗？")) {
                return;
            }
        }
        int required = launchMeta.javaMajor();
        String requiredPlatform = JavaManager.preferredPlatform(required, launchMeta);
        JavaManager.JavaInfo candidateJava = resolveJava(launchMeta);
        if (candidateJava != null
                && !JavaManager.matchesPlatform(config, candidateJava, requiredPlatform)) {
            statusLabel.setText("当前 Java 架构与整合包不一致，准备下载 "
                    + ("mac-os".equals(requiredPlatform) ? "x86_64" : "arm64") + " Java…");
            statusLabel.setForeground(Theme.WARNING);
            candidateJava = null;
        }
        JavaManager.JavaInfo java = candidateJava;
        if (java == null || java.major() < required) {
            String component = launchMeta.javaComponent() == null || launchMeta.javaComponent().isBlank()
                    ? JavaManager.componentForMajor(required) : launchMeta.javaComponent();
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
            String platform = requiredPlatform;
            if ("mac-os".equals(platform) && !JavaManager.rosettaAvailable()) {
                showError("需要 Rosetta 2",
                        "Java 8 官方只有 x86_64 版本，Apple Silicon 上需要 Rosetta 2。\n\n"
                                + "请先在终端运行：\n"
                                + "softwareupdate --install-rosetta --agree-to-license\n\n"
                                + "然后重新启动游戏。");
                return;
            }
            startJavaInstall(entry, launchMeta, component, required, platform);
            return;
        }
        if (required <= 8 && java.major() > 8) {
            if (!askYesNo("兼容性提示", "此版本官方要求 Java 8，当前选择的是 Java " + java.major()
                    + "。\n旧版本可能无法正常启动。仍要继续吗？")) {
                return;
            }
        }

        Path gameContentDir = config.effectiveGameDir(entry.id());
        if (!verifyModsBeforeLaunch(launchMeta, gameContentDir)) {
            return;
        }
        lastGameContentDir = gameContentDir;
        SkinStore.copyForGame(config, config.accountId(), gameContentDir);
        launching = true;
        LaunchProgress launchProgress = new LaunchProgress(this, entry.id());
        launchProgress.setVisible(true);
        launchProgress.stage("检查游戏文件…");
        new SwingWorker<GameLauncher.LaunchResult, Void>() {
            @Override
            protected GameLauncher.LaunchResult doInBackground() throws Exception {
                try {
                    Path launchGameDir = config.effectiveGameDir(entry.id());
                    int synced = GlobalDatapacks.sync(launchGameDir);
                    if (synced > 0) {
                        Log.info("已同步全局数据包到 " + synced + " 个存档");
                    }
                } catch (IOException ex) {
                    Log.warn("同步全局数据包失败: " + ex.getMessage());
                }
                SwingUtilities.invokeLater(() -> launchProgress.stage("构建启动命令…"));
                GameLauncher.LaunchResult result = GameLauncher.launch(config, launchMeta, entry, java);
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
                            StringBuilder crashMessage = new StringBuilder(crash.summary());
                            Path report = LaunchDiagnostics.latestCrashReport(
                                    gamePaths.versionDir(entry == null ? "" : entry.id()));
                            if (report != null) {
                                for (LaunchDiagnostics.Finding finding
                                        : LaunchDiagnostics.analyzeReport(report)) {
                                    crashMessage.append("\n\n建议：").append(finding.action());
                                }
                            }
                            CrashAnalyzer.Result enriched = new CrashAnalyzer.Result(
                                    crashMessage.toString(), crash.suspectMods(), crash.tail(),
                                    crash.source());
                            new CrashDialog(LauncherFrame.this, enriched,
                                    exited.exitValue()).setVisible(true);
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
        startJavaInstall(entry, meta, component, major, null);
    }

    private void startJavaInstall(VersionManifest.VersionEntry entry, VersionMeta meta,
                                  String component, int major, String platform) {
        if (busy) {
            return;
        }
        beginTask("安装 Java " + major);
        new SwingWorker<JavaManager.JavaInfo, Void>() {
            @Override
            protected JavaManager.JavaInfo doInBackground() throws Exception {
                return new JavaManager(config, taskProgress, taskToken)
                        .install(component, major, platform);
            }

            @Override
            protected void done() {
                try {
                    JavaManager.JavaInfo info = get();
                    javaInfos.removeIf(existing -> existing.path().toAbsolutePath().normalize()
                            .equals(info.path().toAbsolutePath().normalize()));
                    javaInfos.add(0, info);
                    config.javaPath(platform == null || platform.isBlank()
                            ? "" : info.path().toString());
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
        beginTask(title, taskSource());
    }

    private void beginTask(String title, String source) {
        busy = true;
        activeTask = taskCenter.start(title, source);
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
        projectDetailPage.refreshInstallState();
        launchPage.setCancelVisible(true);
        launchPage.setCancelEnabled(true);
    }

    private void updateProgress() {
        if (taskProgress == null) {
            return;
        }
        if (taskToken != null && taskToken.isCancelled()) {
            statusLabel.setText("已取消");
            statusLabel.setForeground(Theme.WARNING);
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
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

    private void enqueueInstall(Runnable action) {
        installQueue.addLast(() -> {
            try {
                action.run();
            } finally {
                if (!busy && activeTask == null) {
                    startNextQueuedInstall();
                }
            }
        });
        statusLabel.setText("已加入下载队列（" + installQueue.size() + " 个）");
        statusLabel.setForeground(Theme.ACCENT);
        refreshTasksButton();
    }

    private void startNextQueuedInstall() {
        Runnable next = installQueue.pollFirst();
        if (next != null) {
            SwingUtilities.invokeLater(next);
        }
    }

    private void finishTask() {
        finishTask(true);
    }

    private void finishTask(boolean startNext) {
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
        launchPage.setCancelVisible(false);
        updateDetailForSelection();
        updateJavaHint();
        updateContentSelection();
        if (startNext) {
            startNextQueuedInstall();
        }
    }

    void cancelTask() {
        if (taskToken != null) {
            taskToken.cancel();
            Updater.cancelActiveDownload();
            if (progressTimer != null) {
                progressTimer.stop();
                progressTimer = null;
            }
            taskCenter.cancel(activeTask);
            if (activeTask != null) {
                activeTask.stage("已取消");
            }
            refreshTasksButton();
            statusLabel.setText("已取消");
            statusLabel.setForeground(Theme.WARNING);
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            launchPage.setCancelEnabled(false);
        }
    }

    private String taskSource() {
        if ("download".equals(currentTop)
                && ("content".equals(currentDownloadCard)
                || "projectDetail".equals(currentDownloadCard))) {
            String source = "curseforge".equals(contentPage.source()) ? "CurseForge" : "Modrinth";
            String version = contentPage.versionText();
            return source + (version.isEmpty() ? "" : " · MC " + version);
        }
        if ("versionDetail".equals(currentDownloadCard)) {
            String loaderType = versionDetailPage == null ? "vanilla" : versionDetailPage.loaderType();
            if (!"vanilla".equals(loaderType)) {
                return "Mojang 官方 + " + switch (loaderType) {
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

    void useVersion(VersionManifest.VersionEntry entry) {
        useVersion(entry, false);
    }

    void useVersion(VersionManifest.VersionEntry entry, boolean launch) {
        if (entry == null) {
            return;
        }
        pendingLaunchId = launch ? entry.id() : "";
        installedProjectCache.clear();
        if (contentPage != null) {
            contentPage.reevaluateFilter();
        }
        downloadPage.clearQuery();
        config.filter("all");
        config.save();
        applyFilter();
        versionListCard.selectId(entry.id());
        versionListCard.reveal(versionListCard.selectedIndex());
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

    TaskCenter taskCenter() {
        return taskCenter;
    }

    void refreshTasksUi() {
        refreshTasksButton();
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
        int running = taskCenter.running() + installQueue.size();
        tasksButton.setText(running > 0 ? "任务 " + running : "任务");
        tasksButton.setPreferredSize(new Dimension(running > 0 ? 74 : 64, 24));
        tasksButton.setMaximumSize(new Dimension(running > 0 ? 74 : 64, 24));
        tasksButton.revalidate();
        tasksButton.repaint();
    }

    private void setControlsEnabled(boolean enabled) {
        downloadPage.setSearchEnabled(enabled);
        versionListCard.setEnabled(enabled || busy);
        downloadPage.setRefreshEnabled(enabled && !manifestLoading);
        configPage.setControlsEnabled(enabled);
        settingsPage.setControlsEnabled(enabled);
        downloadPage.setFiltersEnabled(enabled);
        // 下载过程中仍允许浏览、搜索和翻页，只有安装按钮受 busy 限制。
        contentPage.setControlsEnabled(enabled);
        contentPage.setLoaderVisible("mod".equals(contentPage.type()));
    }

    boolean applyGameDirFromField() {
        String value = configPage.gameDirText();
        if (value.isEmpty()) {
            configPage.setGameDirText(gamePaths.gameDir().toString());
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
            versionListCard.repaintList();
            updateDetailForSelection();
        }
        configPage.setGameDirText(path.toString());
        return true;
    }

    void chooseAccountsDirectory() {
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

    void importHmclDirectories() {
        List<HmclImporter.Entry> entries = HmclImporter.detect();
        if (entries.isEmpty()) {
            showError("未找到 HMCL 游戏目录", "没有在常见 HMCL 配置位置找到游戏目录。\n"
                    + "也可以点「选择」直接选择 HMCL 使用的 .minecraft 文件夹。");
            return;
        }
        HmclImporter.Entry entry;
        if (entries.size() == 1) {
            entry = entries.get(0);
        } else {
            String[] names = entries.stream().map(HmclImporter.Entry::toString)
                    .toArray(String[]::new);
            String selected = ModernDialogs.choice(this, "读取 HMCL 目录",
                    "选择要使用的 HMCL 游戏目录：", names, names[0]);
            if (selected == null) {
                return;
            }
            int index = java.util.Arrays.asList(names).indexOf(selected);
            if (index < 0) {
                return;
            }
            entry = entries.get(index);
        }
        if (!Files.isDirectory(entry.path().resolve("versions"))) {
            showError("目录不可用", "这个 HMCL 游戏目录里没有 versions 文件夹：\n" + entry.path());
            return;
        }
        configPage.setGameDirText(entry.path().toString());
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
            configPage.setGameDirText(entry.path().toString());
            Log.info("自动使用 HMCL 游戏目录: " + entry.path());
            return;
        }
    }

    void chooseInstanceFolder() {
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
        configPage.setGameDirText(candidate.toString());
        if (applyGameDirFromField()) {
            statusLabel.setText("已导入实例 · " + candidate.getFileName());
            statusLabel.setForeground(Theme.ACCENT);
            refreshAllVersions();
            refreshInstalledCache();
            applyFilter();
        }
    }

    void chooseGameDirectory() {
        Path selected = Ui.chooseDirectory(this, "选择 Minecraft 游戏目录", gamePaths.gameDir());
        if (selected != null) {
            configPage.setGameDirText(selected.toString());
            applyGameDirFromField();
        }
    }

    void chooseJava() {
        Path path = Ui.chooseFile(this, "选择 java 可执行文件", null);
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

    void openGameDirectory() {
        try {
            Files.createDirectories(gamePaths.gameDir());
            openPath(gamePaths.gameDir());
        } catch (IOException ex) {
            showError("无法打开目录", ex.getMessage());
        }
    }

    void openRecentLog() {
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

    private void openMcmod(Modrinth.Project project) {
        if (project == null) {
            return;
        }
        String url = project.mcmodUrl();
        if (url == null || url.isBlank()) {
            showError("没有 MC百科页面", project.title() + " 暂时没有匹配到 MC百科条目。");
            return;
        }
        if (url.startsWith("//")) {
            url = "https:" + url;
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://www.mcmod.cn/" + url.replaceFirst("^/+", "");
        }
        try {
            String host = java.net.URI.create(url).getHost();
            if (host == null || !(host.equals("mcmod.cn") || host.endsWith(".mcmod.cn"))) {
                showError("MC百科地址无效", url);
                return;
            }
        } catch (RuntimeException ex) {
            showError("MC百科地址无效", url);
            return;
        }
        if (!Browser.open(url)) {
            showError("无法打开浏览器", "请手动打开：\n" + url);
        }
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

    void refreshAllVersions() {
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

    void refreshInstalledCache() {
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
        settingsPage.persistControls();
        applyGameDirFromField();
        if ((getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) == 0) {
            config.launcherWidth(getWidth());
            config.launcherHeight(getHeight());
        }
        if (!config.save()) {
            ModernDialogs.message(this, "配置未保存",
                    "写入 ~/.mclite/config.properties 失败，请检查磁盘空间和目录权限。");
        }
        dispose();
        System.exit(0);
    }

    void showError(String title, String message) {
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

    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    record JavaChoice(String label, Path path) {
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
            title.setText(contentDisplayTitle(value));
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
