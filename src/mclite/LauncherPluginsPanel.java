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
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 下载中心「启动器插件」页：从 release 清单里挑构建，装进选中实例。
 *
 * <p>启动器本身不包含任何 mod jar；这里只做可选的联网下载 + 校验 + 安装。
 */
public final class LauncherPluginsPanel extends JPanel {

    private final Config config;
    private final GamePaths paths;
    private final JComboBox<InstalledInstance> instanceBox = new Ui.DarkCombo<>();
    private final DefaultListModel<IslandCatalog.Build> builds = new DefaultListModel<>();
    private final JList<IslandCatalog.Build> buildList = new JList<>(builds);
    private final JLabel status = new JLabel("正在获取组件清单…");
    private final JLabel installed = new JLabel(" ");
    private final JButton installButton;
    private final JButton uninstallButton;
    private final JButton refreshButton;
    private Runnable pythonInstall;
    private Runnable pythonStart;

    private IslandCatalog.Manifest manifest = IslandCatalog.Manifest.EMPTY;
    private boolean loading;
    private boolean busy;
    private boolean manifestFailed;

    private final JPanel root;

    public LauncherPluginsPanel(Config config, GamePaths paths) {
        this.config = config;
        this.paths = paths;
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(6, 6, 6, 6));

        root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 20);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(18, 18, 16, 18));
        add(root, BorderLayout.CENTER);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Ui.label("扩展组件 · MCLite 岛", Theme.TITLE, Theme.TEXT));
        header.add(Ui.vgap(3));
        header.add(Ui.label("本地音乐 + 3D 歌词 + 灵动岛；可选下载，不随启动器打包。",
                Theme.SMALL, Theme.MUTED));
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel instanceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        instanceRow.setOpaque(false);
        instanceRow.add(Ui.label("目标实例", Theme.SMALL, Theme.MUTED));
        instanceBox.setPreferredSize(new Dimension(380, 30));
        instanceRow.add(instanceBox);
        center.add(instanceRow);
        center.add(Ui.vgap(10));

        buildList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        buildList.setOpaque(false);
        buildList.setForeground(Theme.TEXT);
        buildList.setCellRenderer(new RoundedBuildCellRenderer());
        JScrollPane scroll = Ui.scroll(buildList);
        scroll.setPreferredSize(new Dimension(520, 170));
        Ui.RoundedPanel listCard = new Ui.RoundedPanel(Theme.BG, Theme.BORDER_LIGHT, 14);
        listCard.setLayout(new BorderLayout());
        listCard.setBorder(new EmptyBorder(8, 8, 8, 8));
        listCard.add(scroll, BorderLayout.CENTER);
        center.add(listCard);
        center.add(Ui.vgap(8));
        installed.setForeground(Theme.MUTED);
        center.add(installed);
        status.setForeground(Theme.MUTED);
        center.add(status);
        root.add(center, BorderLayout.CENTER);

        installButton = Ui.smallButton("安装所选", Ui.AccentButton.Style.PRIMARY);
        uninstallButton = Ui.smallButton("卸载", Ui.AccentButton.Style.DANGER);
        refreshButton = Ui.smallButton("刷新清单", Ui.AccentButton.Style.GHOST);
        JButton openMusic = Ui.smallButton("打开音乐目录", Ui.AccentButton.Style.GHOST);
        JButton installPython = Ui.smallButton("安装 Python 模块…", Ui.AccentButton.Style.GHOST);
        JButton startPython = Ui.smallButton("启动 Python Bridge", Ui.AccentButton.Style.GHOST);
        installPython.addActionListener(e -> {
            if (pythonInstall != null) {
                pythonInstall.run();
            }
        });
        startPython.addActionListener(e -> {
            if (pythonStart != null) {
                pythonStart.run();
            }
        });

        JPanel pythonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pythonRow.setOpaque(false);
        pythonRow.add(Ui.label("Python Bridge 模块", Theme.SMALL, Theme.MUTED));
        pythonRow.add(installPython);
        pythonRow.add(startPython);
        center.add(Ui.vgap(10));
        center.add(pythonRow);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.add(refreshButton);
        footer.add(openMusic);
        footer.add(uninstallButton);
        footer.add(installButton);
        root.add(footer, BorderLayout.SOUTH);

        for (InstalledInstance instance : InstalledInstance.scan(paths)) {
            instanceBox.addItem(instance);
        }
        selectDefaultInstance();
        instanceBox.addActionListener(e -> refreshForInstance());
        buildList.addListSelectionListener(e -> updateButtons());
        installButton.addActionListener(e -> installSelected());
        uninstallButton.addActionListener(e -> uninstallSelected());
        refreshButton.addActionListener(e -> loadManifest());
        openMusic.addActionListener(e -> openMusicFolder());
        updateButtons();
    }

    public void setPythonActions(Runnable install, Runnable start) {
        this.pythonInstall = install;
        this.pythonStart = start;
    }

    private boolean loaded;

    /** 切到「启动器插件」页时调用；只拉一次清单。 */
    public void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            loadManifest();
        }
    }

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
        if (loading) {
            return;
        }
        loading = true;
        status.setText("正在获取组件清单…");
        updateButtons();
        SwingWorker<IslandCatalog.Manifest, Void> worker = new SwingWorker<>() {
            @Override
            protected IslandCatalog.Manifest doInBackground() throws Exception {
                Net net = new Net(ProgressSink.NOOP, new CancellationToken());
                return IslandCatalog.fetch(net);
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    manifest = get();
                    manifestFailed = false;
                    status.setText(manifest.builds().isEmpty()
                            ? "清单里没有可用构建"
                            : "清单已更新：" + manifest.builds().size() + " 个构建");
                } catch (Exception ex) {
                    manifest = IslandCatalog.Manifest.EMPTY;
                    manifestFailed = true;
                    status.setText("获取失败：" + rootMessage(ex));
                }
                refreshForInstance();
                updateButtons();
            }
        };
        worker.execute();
    }

    private void refreshForInstance() {
        InstalledInstance instance = selectedInstance();
        builds.clear();
        if (instance == null) {
            installed.setText("没有已安装的实例");
            status.setText("先在实例页安装一个版本，再回来装扩展");
            updateButtons();
            return;
        }
        for (IslandCatalog.Build build : IslandCatalog.compatible(manifest, instance)) {
            builds.addElement(build);
        }
        if (builds.isEmpty() && !loading && !manifestFailed) {
            status.setText("该实例没有匹配构建（需要 MC " + instance.mcVersion()
                    + " / " + instance.loader() + "）");
        }
        refreshInstalled(instance);
        if (builds.size() == 1) {
            buildList.setSelectedIndex(0);
        }
        updateButtons();
    }

    private void refreshInstalled(InstalledInstance instance) {
        try {
            List<Path> jars = IslandInstaller.installedJars(config, instance);
            installed.setText(jars.isEmpty()
                    ? "当前实例未安装"
                    : "当前实例已安装：" + jars.get(0).getFileName());
        } catch (Exception ex) {
            installed.setText("读取 mods 目录失败：" + rootMessage(ex));
        }
    }

    private void updateButtons() {
        boolean hasInstance = selectedInstance() != null;
        boolean hasBuild = buildList.getSelectedValue() != null;
        installButton.setEnabled(!busy && !loading && hasInstance && hasBuild);
        uninstallButton.setEnabled(!busy && hasInstance);
        refreshButton.setEnabled(!busy && !loading);
        instanceBox.setEnabled(!busy);
        buildList.setEnabled(!busy);
    }

    private void installSelected() {
        InstalledInstance instance = selectedInstance();
        IslandCatalog.Build build = buildList.getSelectedValue();
        if (instance == null || build == null) {
            return;
        }
        busy = true;
        updateButtons();
        status.setText("正在下载 " + build.displayName() + " …");
        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                Net net = new Net(ProgressSink.NOOP, new CancellationToken());
                return IslandInstaller.install(net, config, instance, build);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    Path jar = get();
                    status.setText("已安装：" + jar.getFileName() + "（重启游戏生效）");
                } catch (Exception ex) {
                    status.setText("安装失败：" + rootMessage(ex));
                }
                refreshInstalled(instance);
                updateButtons();
            }
        };
        worker.execute();
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
            status.setText(removed == 0 ? "没有找到可卸载的组件" : "已卸载 " + removed + " 个文件");
        } catch (Exception ex) {
            status.setText("卸载失败：" + rootMessage(ex));
        }
        refreshInstalled(instance);
        updateButtons();
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
            ModernDialogs.message(this, "打开目录失败", "路径：" + dir + "\n" + rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    /** 列表项也用圆角高亮，避免默认矩形选中条。 */
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
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
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
