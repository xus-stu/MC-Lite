package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JEditorPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 下载中心「启动器插件」：每个插件一张卡片，点进去是详情页。
 * 详情简介从 GitHub raw 拉取（失败用内置文案）。
 */
public final class LauncherPluginsPanel extends JPanel {

    private static final String ISLAND_DESC_URL =
            "https://raw.githubusercontent.com/xus-stu/MC-Lite/main/docs/island.md";
    private static final String PYTHON_DESC_URL =
            "https://raw.githubusercontent.com/xus-stu/MC-Lite/main/python/README.md";

    private static final Map<String, String> DESC_CACHE = new HashMap<>();

    private final Config config;
    private final GamePaths paths;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private Runnable pythonInstall;
    private Runnable pythonStart;

    private IslandCatalog.Manifest manifest = IslandCatalog.Manifest.EMPTY;
    private boolean manifestLoading;
    private long manifestAt;
    private TaskCenter taskCenter;
    private boolean manifestLoaded;

    // 岛详情页控件
    private final JComboBox<InstalledInstance> instanceBox = new Ui.DarkCombo<>();
    private final DefaultListModel<IslandCatalog.Build> builds = new DefaultListModel<>();
    private final JList<IslandCatalog.Build> buildList = new JList<>(builds);
    private final JLabel islandStatus = new JLabel(" ");
    private final JLabel installedLabel = new JLabel(" ");
    private final JButton installButton;
    private final JButton uninstallButton;

    void setTaskCenter(TaskCenter taskCenter) {
        this.taskCenter = taskCenter;
    }

    public LauncherPluginsPanel(Config config, GamePaths paths) {
        this.config = config;
        this.paths = paths;
        setOpaque(false);
        setLayout(new BorderLayout());
        deck.setOpaque(false);
        deck.add(buildCatalog(), "catalog");
        deck.add(new JPanel(), "detail");
        add(deck, BorderLayout.CENTER);

        installButton = Ui.smallButton("安装所选", Ui.AccentButton.Style.PRIMARY);
        uninstallButton = Ui.smallButton("卸载", Ui.AccentButton.Style.DANGER);
        installButton.addActionListener(e -> installSelected());
        uninstallButton.addActionListener(e -> uninstallSelected());
    }

    public void setPythonActions(Runnable install, Runnable start) {
        this.pythonInstall = install;
        this.pythonStart = start;
    }

    /** 切到插件页时调用；清单在打开岛详情时再拉。 */
    public void ensureLoaded() {
        showCatalog();
    }

    public void showCatalog() {
        cards.show(deck, "catalog");
    }

    // ---------------------------------------------------------------- 列表页

    private JPanel buildCatalog() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setBorder(new EmptyBorder(6, 6, 6, 6));

        Ui.RoundedPanel card = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 20);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(Ui.label("启动器插件", Theme.TITLE, Theme.TEXT));
        head.add(Ui.vgap(3));
        head.add(Ui.label("点开查看详情；插件不随启动器打包，按需下载。", Theme.SMALL, Theme.MUTED));
        card.add(head, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.add(pluginCard("MCLite Island", "本地音乐 + 3D 歌词 + 灵动岛",
                "零登录、零联网的本地音乐播放器，支持 JEI/Jade 目标信息与可导入 LRC。",
                () -> openIslandDetail()));
        list.add(Ui.vgap(10));
        list.add(pluginCard("Python Bridge 模块", "让 PyCharm / 外部脚本连接启动器",
                "把 mclite_bridge 安装到指定 Python 解释器，并可一键启动 Bridge 服务。",
                () -> openPythonDetail()));
        card.add(list, BorderLayout.CENTER);
        outer.add(card, BorderLayout.CENTER);
        return outer;
    }

    private JPanel pluginCard(String title, String subtitle, String intro, Runnable onOpen) {
        Ui.RoundedPanel card = new Ui.RoundedPanel(Theme.CARD_ALT, Theme.BORDER_LIGHT, 14);
        card.setLayout(new BorderLayout(10, 0));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.label(title, Theme.H2, Theme.TEXT));
        text.add(Ui.vgap(2));
        text.add(Ui.label(subtitle, Theme.SMALL, Theme.ACCENT));
        text.add(Ui.vgap(4));
        text.add(Ui.label(intro, Theme.SMALL, Theme.MUTED));
        card.add(text, BorderLayout.CENTER);

        JButton open = Ui.smallButton("查看详情", Ui.AccentButton.Style.GHOST);
        open.addActionListener(e -> onOpen.run());
        card.add(open, BorderLayout.EAST);
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onOpen.run();
            }
        });
        return card;
    }

    // ---------------------------------------------------------------- 岛详情

    private void openIslandDetail() {
        JPanel detail = detailShell("MCLite Island", this::showCatalog);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JScrollPane intro = descriptionPane(
                "零登录、零联网的本地音乐播放器：歌单、LRC 歌词、3D 歌词与灵动岛。",
                ISLAND_DESC_URL);
        body.add(intro);
        body.add(Ui.vgap(10));

        JPanel instanceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        instanceRow.setOpaque(false);
        instanceRow.add(Ui.label("目标实例", Theme.SMALL, Theme.MUTED));
        instanceBox.setPreferredSize(new Dimension(380, 30));
        instanceBox.removeAllItems();
        for (InstalledInstance instance : InstalledInstance.scan(paths)) {
            instanceBox.addItem(instance);
        }
        instanceBox.addActionListener(e -> refreshForInstance());
        instanceRow.add(instanceBox);
        body.add(instanceRow);
        body.add(Ui.vgap(8));

        buildList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        buildList.setOpaque(false);
        buildList.setCellRenderer(new RoundedBuildCellRenderer());
        JScrollPane scroll = Ui.scroll(buildList);
        scroll.setPreferredSize(new Dimension(520, 150));
        Ui.RoundedPanel listCard = new Ui.RoundedPanel(Theme.BG, Theme.BORDER_LIGHT, 14);
        listCard.setLayout(new BorderLayout());
        listCard.setBorder(new EmptyBorder(8, 8, 8, 8));
        listCard.add(scroll, BorderLayout.CENTER);
        body.add(listCard);
        body.add(Ui.vgap(6));
        installedLabel.setForeground(Theme.MUTED);
        islandStatus.setForeground(Theme.MUTED);
        body.add(installedLabel);
        body.add(islandStatus);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton refresh = Ui.smallButton("刷新清单", Ui.AccentButton.Style.GHOST);
        JButton openMusic = Ui.smallButton("打开音乐目录", Ui.AccentButton.Style.GHOST);
        JButton uninstall = uninstallButton;
        JButton install = installButton;
        refresh.addActionListener(e -> loadManifest());
        openMusic.addActionListener(e -> openMusicFolder());
        actions.add(refresh);
        actions.add(openMusic);
        actions.add(uninstall);
        actions.add(install);
        body.add(Ui.vgap(8));
        body.add(actions);

        JPanel card = (JPanel) detail.getClientProperty("card");
        JScrollPane bodyScroll = Ui.scroll(body);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(bodyScroll, BorderLayout.CENTER);
        replaceDetail(detail);
        selectDefaultInstance();
        loadManifest();
    }

    // ---------------------------------------------------------------- Python 详情

    private void openPythonDetail() {
        JPanel detail = detailShell("Python Bridge 模块", this::showCatalog);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(descriptionPane(
                "安装 Python 客户端后，PyCharm 可以直接 import mclite_bridge；Bridge 启动后才能调用 API。",
                PYTHON_DESC_URL));
        body.add(Ui.vgap(12));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton install = Ui.smallButton("安装 Python 模块…", Ui.AccentButton.Style.PRIMARY);
        JButton start = Ui.smallButton("启动 Python Bridge", Ui.AccentButton.Style.GHOST);
        install.addActionListener(e -> {
            if (pythonInstall != null) {
                pythonInstall.run();
            }
        });
        start.addActionListener(e -> {
            if (pythonStart != null) {
                pythonStart.run();
            }
        });
        actions.add(install);
        actions.add(start);
        body.add(actions);
        JPanel card = (JPanel) detail.getClientProperty("card");
        JScrollPane bodyScroll = Ui.scroll(body);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(bodyScroll, BorderLayout.CENTER);
        replaceDetail(detail);
    }

    private JPanel detailShell(String title, Runnable onBack) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setBorder(new EmptyBorder(6, 6, 6, 6));
        Ui.RoundedPanel card = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 20);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JButton back = Ui.smallButton("< 插件列表", Ui.AccentButton.Style.GHOST);
        back.addActionListener(e -> onBack.run());
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(Ui.label(title, Theme.TITLE, Theme.TEXT));
        head.add(back, BorderLayout.WEST);
        head.add(titleBox, BorderLayout.CENTER);
        card.add(head, BorderLayout.NORTH);
        outer.add(card, BorderLayout.CENTER);
        outer.putClientProperty("card", card);
        return outer;
    }

    private void replaceDetail(JPanel detail) {
        deck.removeAll();
        deck.add(buildCatalog(), "catalog");
        deck.add(detail, "detail");
        deck.revalidate();
        deck.repaint();
        cards.show(deck, "detail");
    }

    private JScrollPane descriptionPane(String fallback, String url) {
        JEditorPane area = new JEditorPane();
        area.setContentType("text/html");
        area.setEditable(false);
        area.setOpaque(false);
        area.putClientProperty("Fallback", fallback);
        area.setText(renderMarkdown(fallback));
        area.setCaretPosition(0);
        JScrollPane scroll = Ui.scroll(area);
        scroll.setPreferredSize(new Dimension(560, 210));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        loadDescription(area, fallback, url);
        return scroll;
    }

    private void loadDescription(JEditorPane area, String fallback, String url) {
        String cached = DESC_CACHE.get(url);
        if (cached != null) {
            area.setText(renderMarkdown(cached));
            area.setCaretPosition(0);
            return;
        }
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                Net net = new Net(ProgressSink.NOOP, new CancellationToken());
                byte[] bytes = null;
                IOException last = null;
                for (String candidate : GithubSources.candidates(url)) {
                    try {
                        bytes = net.getUnverifiedBytes(candidate, "插件简介");
                        break;
                    } catch (IOException ex) {
                        last = ex;
                    }
                }
                if (bytes == null) {
                    throw last == null ? new IOException("简介下载失败") : last;
                }
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                String trimmed = text.replace("\r", "").trim();
                if (trimmed.length() > 1400) {
                    trimmed = trimmed.substring(0, 1400).trim() + "\n\n…";
                }
                return trimmed.isBlank() ? fallback : trimmed;
            }

            @Override
            protected void done() {
                try {
                    String text = get();
                    DESC_CACHE.put(url, text);
                    area.setText(renderMarkdown(text));
                    area.setCaretPosition(0);
                } catch (Exception ignored) {
                    area.setText(renderMarkdown(fallback));
                }
            }
        }.execute();
    }

    /** 极简 Markdown → HTML，够详情页把标题/列表/代码/链接显示成人能读的样子。 */
    private static String renderMarkdown(String markdown) {
        String color = hex(Theme.TEXT);
        String muted = hex(Theme.MUTED);
        String bg = hex(Theme.CARD);
        StringBuilder html = new StringBuilder(512);
        html.append("<html><head><style>")
                .append("body{font-family:sans-serif;font-size:12px;color:").append(color)
                .append(";background:").append(bg).append(";margin:0;padding:6px;}")
                .append("h1,h2,h3{color:").append(color).append(";margin:8px 0 4px 0;}")
                .append("h1{font-size:18px} h2{font-size:15px} h3{font-size:13px}")
                .append("p{margin:4px 0} li{margin:2px 0} code{font-family:monospace;color:")
                .append(muted).append(";} pre{font-family:monospace;color:").append(muted)
                .append(";margin:4px 0;} a{color:").append(hex(Theme.ACCENT)).append(";}")
                .append("</style></head><body>");

        boolean inCode = false;
        boolean inList = false;
        for (String raw : markdown.split("\n", -1)) {
            String line = raw;
            if (line.trim().startsWith("```")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append(inCode ? "</pre>" : "<pre>");
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                html.append(escape(line)).append("\n");
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append("<p></p>");
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                int level = Math.min(3, trimmed.lastIndexOf('#') + 1);
                html.append("<h").append(level).append(">")
                        .append(inline(trimmed.substring(level).trim()))
                        .append("</h").append(level).append(">");
                continue;
            }
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(inline(trimmed.substring(2))).append("</li>");
                continue;
            }
            if (inList) {
                html.append("</ul>");
                inList = false;
            }
            html.append("<p>").append(inline(trimmed)).append("</p>");
        }
        if (inList) {
            html.append("</ul>");
        }
        if (inCode) {
            html.append("</pre>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private static String inline(String text) {
        String escaped = escape(text);
        escaped = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        escaped = escaped.replaceAll("`([^`]+)`", "<code>$1</code>");
        escaped = escaped.replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        return escaped;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String hex(java.awt.Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    // ---------------------------------------------------------------- 安装逻辑

    private void selectDefaultInstance() {
        String last = config.lastVersion();
        for (int i = 0; i < instanceBox.getItemCount(); i++) {
            if (instanceBox.getItemAt(i).id().equals(last)) {
                instanceBox.setSelectedIndex(i);
                return;
            }
        }
        if (instanceBox.getItemCount() > 0) {
            instanceBox.setSelectedIndex(0);
        }
    }

    private InstalledInstance selectedInstance() {
        return (InstalledInstance) instanceBox.getSelectedItem();
    }

    private void loadManifest() {
        if (manifestLoading) {
            return;
        }
        manifestLoading = true;
        islandStatus.setText("正在获取组件清单…");
        installButton.setEnabled(false);
        new SwingWorker<IslandCatalog.Manifest, Void>() {
            @Override
            protected IslandCatalog.Manifest doInBackground() throws Exception {
                Net net = new Net(ProgressSink.NOOP, new CancellationToken());
                return IslandCatalog.fetch(net);
            }

            @Override
            protected void done() {
                manifestLoading = false;
                try {
                    manifest = get();
                    manifestLoaded = true;
                    manifestAt = System.currentTimeMillis();
                    islandStatus.setText(manifest.builds().isEmpty()
                            ? "清单里没有可用构建" : "清单已更新：" + manifest.builds().size() + " 个构建");
                } catch (Exception ex) {
                    manifest = IslandCatalog.Manifest.EMPTY;
                    islandStatus.setText("获取失败：" + ex.getMessage());
                }
                refreshForInstance();
            }
        }.execute();
    }

    private void refreshForInstance() {
        InstalledInstance instance = selectedInstance();
        builds.clear();
        if (instance == null) {
            installedLabel.setText("没有已安装的实例，请先在版本列表里安装 MC");
            buildList.setEnabled(false);
            installButton.setEnabled(false);
            uninstallButton.setEnabled(false);
            return;
        }
        buildList.setEnabled(true);
        for (IslandCatalog.Build build : IslandCatalog.compatible(manifest, instance)) {
            builds.addElement(build);
        }
        if (builds.size() == 1) {
            buildList.setSelectedIndex(0);
        }
        try {
            List<Path> jars = IslandInstaller.installedJars(config, instance);
            installedLabel.setText(jars.isEmpty()
                    ? "当前实例未安装" : "已安装：" + jars.get(0).getFileName());
        } catch (Exception ex) {
            installedLabel.setText("读取 mods 失败：" + ex.getMessage());
        }
        updateButtons();
    }

    private void updateButtons() {
        installButton.setEnabled(!manifestLoading && selectedInstance() != null
                && buildList.getSelectedValue() != null);
        uninstallButton.setEnabled(selectedInstance() != null);
    }

    private void installSelected() {
        InstalledInstance instance = selectedInstance();
        IslandCatalog.Build build = buildList.getSelectedValue();
        if (instance == null || build == null) {
            return;
        }
        installButton.setEnabled(false);
        islandStatus.setText("正在下载 " + build.displayName() + " …");

        DownloadTask task = taskCenter == null ? null
                : taskCenter.start("MCLite 岛 " + build.version(), "GitHub Releases");
        TaskProgress progress = new TaskProgress();
        progress.stage("测速选择镜像…");
        javax.swing.Timer timer = new javax.swing.Timer(100, event -> {
            if (taskCenter != null && task != null) {
                taskCenter.update(task, progress);
            }
        });
        timer.start();

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                Net net = new Net(progress, new CancellationToken());
                try {
                    return IslandInstaller.install(net, config, instance, build, progress);
                } catch (Exception first) {
                    // 清单可能缓存了已删除的旧版本：强制刷新后，用同实例的最新构建再试一次
                    progress.reset("刷新清单后重试…");
                    IslandCatalog.Manifest fresh = IslandCatalog.fetch(net);
                    IslandCatalog.Build latest = IslandCatalog.compatible(fresh, instance).stream()
                            .findFirst().orElse(null);
                    if (latest == null) {
                        throw first;
                    }
                    return IslandInstaller.install(net, config, instance, latest, progress);
                }
            }

            @Override
            protected void done() {
                timer.stop();
                try {
                    Path jar = get();
                    islandStatus.setText("已安装：" + jar.getFileName() + "（重启游戏生效）");
                    if (taskCenter != null && task != null) {
                        progress.stage("安装完成");
                        taskCenter.update(task, progress);
                        taskCenter.finish(task);
                    }
                } catch (Exception ex) {
                    islandStatus.setText("安装失败：" + ex.getMessage());
                    if (taskCenter != null && task != null) {
                        taskCenter.fail(task, ex.getMessage() == null ? "安装失败" : ex.getMessage());
                    }
                }
                refreshForInstance();
            }
        }.execute();
    }

    private void uninstallSelected() {
        InstalledInstance instance = selectedInstance();
        if (instance == null) {
            return;
        }
        if (!ModernDialogs.confirm(this, "卸载 MCLite 岛",
                "从实例 " + instance.id() + " 的 mods 目录删除 mclite-island-*.jar？")) {
            return;
        }
        try {
            int removed = IslandInstaller.uninstall(config, instance);
            islandStatus.setText(removed == 0 ? "没有找到可卸载的组件" : "已卸载 " + removed + " 个文件");
        } catch (Exception ex) {
            islandStatus.setText("卸载失败：" + ex.getMessage());
        }
        refreshForInstance();
    }

    private void openMusicFolder() {
        InstalledInstance instance = selectedInstance();
        if (instance == null) {
            return;
        }
        Path dir = config.effectiveGameDir(instance.id()).resolve("mclite-island").resolve("music");
        try {
            Files.createDirectories(dir);
            java.awt.Desktop.getDesktop().open(dir.toFile());
        } catch (Exception ex) {
            ModernDialogs.message(this, "打开目录失败", "路径：" + dir + "\n" + ex.getMessage());
        }
    }

    private static final class RoundedBuildCellRenderer extends JLabel
            implements javax.swing.ListCellRenderer<IslandCatalog.Build> {
        private boolean selected;

        RoundedBuildCellRenderer() {
            setOpaque(false);
            setForeground(Theme.TEXT);
            setBorder(new EmptyBorder(6, 10, 6, 10));
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<? extends IslandCatalog.Build> list,
                                                               IslandCatalog.Build value, int index,
                                                               boolean isSelected, boolean cellHasFocus) {
            setText(value == null ? "" : value.displayName());
            this.selected = isSelected;
            setForeground(isSelected ? Theme.ACCENT : Theme.TEXT);
            return this;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                g2.setColor(Theme.CARD_ALT);
                g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
