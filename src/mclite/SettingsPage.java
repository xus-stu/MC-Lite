package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings page, extracted from LauncherFrame. */
final class SettingsPage {
    private final LauncherFrame owner;
    private final Config config;
    private final TaskCenter taskCenter;
    private final Runnable updateLaunchInfo;
    private final java.util.function.BooleanSupplier canRestart;
    private javax.swing.JTextField jvmArgsField;
    private Ui.ValueSlider memorySlider;
    private Ui.ToggleChip autoMemoryToggle;
    private Ui.ToggleChip autoJvmToggle;
    private Ui.ToggleChip metalToggle;
    private Ui.ToggleChip autoUpdateToggle;
    private Ui.ToggleChip removeRegionLimitsToggle;
    private Ui.ValueSlider widthSlider;
    private Ui.ValueSlider heightSlider;
    private javax.swing.JToggleButton assetsToggle;
    private javax.swing.JToggleButton mirrorToggle;
    private Ui.ToggleChip forceTranslationToggle;
    private javax.swing.JTextField curseForgeKeyField;
    private javax.swing.JTextArea uiCssArea;
    private Ui.DarkCombo<FontCatalog.Choice> fontCombo;
    private javax.swing.JLabel accountsDirLabel;
    private Ui.ToggleChip recommendToggle;
    private Ui.AccentButton applyFontButton;
    private Ui.AccentButton applyCssButton;
    private Ui.AccentButton resetCssButton;

    SettingsPage(LauncherFrame owner, Config config, TaskCenter taskCenter,
                 Runnable updateLaunchInfo, java.util.function.BooleanSupplier canRestart) {
        this.owner = owner;
        this.config = config;
        this.taskCenter = taskCenter;
        this.updateLaunchInfo = updateLaunchInfo;
        this.canRestart = canRestart;
    }

    void updateAutoMemoryControls() {
        int memory = owner.selectedMemoryMb();
        if (autoMemoryToggle != null) {
            autoMemoryToggle.setText("自动分配内存（推荐 " + memory + " MB）");
        }
        if (memorySlider != null) {
            memorySlider.setEnabled(!config.autoMemory());
            if (config.autoMemory()) {
                memorySlider.setValue(memory);
            }
        }
    }

    void saveCurseForgeKey() {
        if (curseForgeKeyField != null) {
            config.curseForgeApiKey(curseForgeKeyField.getText());
            config.save();
        }
    }

    void saveJvmArgs() {
        if (jvmArgsField != null) {
            config.jvmArgs(jvmArgsField.getText());
            config.save();
            updateLaunchInfo.run();
        }
    }

    void persistControls() {
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
    }

    void updateAccountsLabel() {
        if (accountsDirLabel != null) {
            accountsDirLabel.setText(config.accountsDir().toString());
            accountsDirLabel.setToolTipText(config.accountsDir().toString());
        }
    }

    void setControlsEnabled(boolean enabled) {
        for (javax.swing.JComponent component : new javax.swing.JComponent[]{
                jvmArgsField, memorySlider, autoMemoryToggle, autoJvmToggle, metalToggle,
                autoUpdateToggle, removeRegionLimitsToggle, widthSlider, heightSlider,
                assetsToggle, mirrorToggle, forceTranslationToggle, curseForgeKeyField,
                uiCssArea, fontCombo, recommendToggle, applyFontButton, applyCssButton,
                resetCssButton}) {
            if (component != null) {
                component.setEnabled(enabled);
            }
        }
        if (memorySlider != null) {
            memorySlider.setEnabled(enabled && !config.autoMemory());
        }
    }

    JPanel build() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        Ui.AccentButton openData = Ui.smallButton("打开数据目录", Ui.AccentButton.Style.GHOST);
        openData.addActionListener(e -> owner.openConfigDirectory());
        Ui.AccentButton openLogs = Ui.smallButton("打开日志目录", Ui.AccentButton.Style.GHOST);
        openLogs.addActionListener(e -> owner.openLogsDirectory());
        JPanel settingsActions = new JPanel();
        settingsActions.setOpaque(false);
        settingsActions.setLayout(new BoxLayout(settingsActions, BoxLayout.X_AXIS));
        settingsActions.add(openLogs);
        Ui.AccentButton installPython = Ui.smallButton("安装 Python 模块…",
                Ui.AccentButton.Style.GHOST);
        installPython.setToolTipText("把 Bridge 的 Python 客户端安装到指定解释器，PyCharm 里可直接 import");
        installPython.addActionListener(e -> installPythonModule());
        settingsActions.add(installPython);
        settingsActions.add(Ui.hgap(6));
        Ui.AccentButton checkUpdate = Ui.smallButton("检查更新", Ui.AccentButton.Style.GHOST);
        checkUpdate.addActionListener(e -> Updater.checkNow(config, owner,
                taskCenter, owner::refreshTasksUi));
        settingsActions.add(checkUpdate);
        settingsActions.add(Ui.hgap(6));
        settingsActions.add(openData);
        page.add(LauncherLayout.pageHeader("设置", "性能、窗口和下载选项会自动保存", settingsActions), BorderLayout.NORTH);
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

        JPanel performance = LauncherLayout.section("性能", "修改后会在下一次启动游戏时生效");
        JPanel memoryRow = new JPanel();
        memoryRow.setOpaque(false);
        memoryRow.setLayout(new BoxLayout(memoryRow, BoxLayout.X_AXIS));
        memoryRow.add(Ui.label("内存分配", Theme.TINY, Theme.DIM));
        memoryRow.add(Ui.hgap(12));
        memorySlider = new Ui.ValueSlider(512, 32768, 512, config.memoryMb(), " MB");
        memorySlider.addChangeListener(value -> {
            config.memoryMb(value);
            config.save();
            updateLaunchInfo.run();
        });
        memoryRow.add(memorySlider);
        memoryRow.add(Box.createHorizontalGlue());
        memoryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
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
            updateAutoMemoryControls();
            updateLaunchInfo.run();
        });
        performance.add(autoMemoryToggle);
        updateAutoMemoryControls();
        performance.add(Ui.vgap(8));
        autoJvmToggle = new Ui.ToggleChip("自动 JVM 优化参数（按 Java/版本选择 GC）");
        autoJvmToggle.setSelected(config.autoJvmTuning());
        autoJvmToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        autoJvmToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoJvmToggle.addActionListener(e -> {
            config.autoJvmTuning(autoJvmToggle.isSelected());
            config.save();
            updateLaunchInfo.run();
        });
        performance.add(autoJvmToggle);
        if (LauncherFrame.isMac()) {
            performance.add(Ui.vgap(8));
            metalToggle = new Ui.ToggleChip("强制使用 Metal 渲染（实验性，重启生效）");
            metalToggle.setSelected(config.metalRendering());
            metalToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            metalToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            metalToggle.setToolTipText("启用后使用 Metal 绘制界面；如果出现黑屏或花屏，关闭此选项并重启");
            metalToggle.addActionListener(e -> {
                config.metalRendering(metalToggle.isSelected());
                config.save();
                ModernDialogs.message(owner, "渲染设置",
                        metalToggle.isSelected()
                                ? "已启用 Metal 渲染，重启启动器后生效。\n如果界面黑屏，请编辑配置关闭 metalRendering。"
                                : "已恢复软件渲染，重启启动器后生效。");
            });
            performance.add(metalToggle);
        }
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
        performance.add(LauncherLayout.sidebarNote("按空格分隔；引号可用于包含空格的参数。不要把 access token 等敏感信息写在这里。"));
        form.add(LauncherLayout.lockedHeight(performance));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel window = LauncherLayout.section("窗口", "首次启动使用该尺寸；游戏内设置可能覆盖它");
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
        form.add(LauncherLayout.lockedHeight(window));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel download = LauncherLayout.section("下载", "完整资源包含声音、语言和图标");
        assetsToggle = new Ui.ToggleChip("下载完整资源");
        assetsToggle.setSelected(config.downloadAssets());
        assetsToggle.setToolTipText("首次下载约 300–500 MB；关闭后现代版本可能无法启动。");
        assetsToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        assetsToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        assetsToggle.addActionListener(e -> {
            config.downloadAssets(assetsToggle.isSelected());
            config.save();
            owner.refreshSelectionUi();
            updateLaunchInfo.run();
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
            owner.refreshContentVisuals();
        });
        download.add(forceTranslationToggle);
        download.add(Ui.vgap(8));
        autoUpdateToggle = new Ui.ToggleChip("自动检查启动器更新（后台下载，下次启动生效）");
        autoUpdateToggle.setSelected(config.autoUpdate());
        autoUpdateToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        autoUpdateToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoUpdateToggle.addActionListener(e -> {
            config.autoUpdate(autoUpdateToggle.isSelected());
            config.save();
        });
        download.add(autoUpdateToggle);
        download.add(Ui.vgap(8));
        recommendToggle = new Ui.ToggleChip("安装加载器后显示推荐模组");
        recommendToggle.setSelected(config.recommendMods());
        recommendToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        recommendToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        recommendToggle.addActionListener(e -> {
            config.recommendMods(recommendToggle.isSelected());
            config.save();
            owner.refreshSelectionUi();
        });
        download.add(recommendToggle);
        download.add(Ui.vgap(8));
        Ui.ToggleChip integrityToggle = new Ui.ToggleChip("启动前检查游戏文件完整性");
        integrityToggle.setSelected(config.integrityCheck());
        integrityToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        integrityToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        integrityToggle.addActionListener(e -> {
            config.integrityCheck(integrityToggle.isSelected());
            config.save();
        });
        download.add(integrityToggle);
        if (!RegionGate.locked()) {
            download.add(Ui.vgap(8));
            removeRegionLimitsToggle = new Ui.ToggleChip("更新时移除地区限制（仅普通版）");
            removeRegionLimitsToggle.setSelected(config.removeRegionLimits());
            removeRegionLimitsToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            removeRegionLimitsToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            removeRegionLimitsToggle.addActionListener(e -> {
                config.removeRegionLimits(removeRegionLimitsToggle.isSelected());
                config.save();
            });
            download.add(removeRegionLimitsToggle);
        }
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
        download.add(LauncherLayout.sidebarNote("CurseForge 官方 API 需要个人 Key；Modrinth 不需要 Key。"
                + "HMCL 内置了它自己申请的 Key，本项目不能复用。"
                + "资源对象在多个版本之间共享；删除后可通过「修复 / 更新」补全。"));
        form.add(LauncherLayout.lockedHeight(download));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel benchmark = LauncherLayout.section("跑分模块", "可选下载，不随启动器捆绑，不强制安装");
        JLabel benchmarkStatus = Ui.label(BenchmarkManager.installed(config)
                ? "已安装" : "未下载", Theme.TINY, Theme.DIM);
        benchmarkStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        benchmark.add(benchmarkStatus);
        benchmark.add(Ui.vgap(8));
        JPanel benchmarkActions = new JPanel();
        benchmarkActions.setOpaque(false);
        benchmarkActions.setLayout(new BoxLayout(benchmarkActions, BoxLayout.X_AXIS));
        Ui.AccentButton benchmarkDownload = Ui.smallButton("下载跑分模块",
                Ui.AccentButton.Style.SECONDARY);
        benchmarkDownload.addActionListener(e -> {
            benchmarkStatus.setText("下载中…");
            BenchmarkManager.download(config, taskCenter, owner::refreshTasksUi,
                    benchmarkStatus::setText);
        });
        Ui.AccentButton benchmarkRun = Ui.smallButton("运行跑分",
                Ui.AccentButton.Style.GHOST);
        benchmarkRun.addActionListener(e -> BenchmarkManager.run(config, benchmarkStatus::setText));
        Ui.AccentButton benchmarkGame = Ui.smallButton("用当前实例跑分",
                Ui.AccentButton.Style.SECONDARY);
        benchmarkGame.addActionListener(e -> owner.runGameBenchmark(benchmarkStatus));
        benchmarkActions.add(benchmarkDownload);
        benchmarkActions.add(Ui.hgap(8));
        benchmarkActions.add(benchmarkRun);
        benchmarkActions.add(Ui.hgap(8));
        benchmarkActions.add(benchmarkGame);
        benchmarkActions.add(Box.createHorizontalGlue());
        benchmarkActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        benchmarkActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        benchmark.add(benchmarkActions);
        benchmark.add(Ui.vgap(7));
        benchmark.add(LauncherLayout.sidebarNote("模块独立运行，扫描 CPU、内存、系统和显卡；"
                + "扫描不到硬件时显示未知硬件。"));
        form.add(LauncherLayout.lockedHeight(benchmark));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel style = LauncherLayout.section("界面样式", "支持简单 CSS：background / color / card / accent / border / font-size");
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
        applyFontButton = Ui.smallButton("应用字体", Ui.AccentButton.Style.SECONDARY);
        applyFontButton.addActionListener(e -> applyUiFont());
        fontRow.add(applyFontButton);
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
        applyCssButton = Ui.smallButton("应用界面样式", Ui.AccentButton.Style.SECONDARY);
        applyCssButton.addActionListener(e -> applyUiCss());
        resetCssButton = Ui.smallButton("恢复默认样式", Ui.AccentButton.Style.GHOST);
        resetCssButton.addActionListener(e -> {
            uiCssArea.setText(Theme.defaultCss());
            applyUiCss();
        });
        cssActions.add(applyCssButton);
        cssActions.add(Ui.hgap(8));
        cssActions.add(resetCssButton);
        cssActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        cssActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        style.add(cssActions);
        form.add(LauncherLayout.lockedHeight(style));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel data = LauncherLayout.section("数据目录", "启动器配置、缓存、账户和官方 Java 运行时");
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
        changeAccounts.addActionListener(e -> owner.chooseAccountsDirectory());
        accountsRow.add(changeAccounts, BorderLayout.EAST);
        accountsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        accountsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        data.add(accountsRow);
        data.add(Ui.vgap(5));
        data.add(LauncherLayout.sidebarNote("游戏文件不会放在这里，而是放在「启动器配置」中选择的游戏目录。"));
        form.add(LauncherLayout.lockedHeight(data));
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

    private void installPythonModule() {
        java.nio.file.Path python = Ui.chooseFile(owner, "选择 Python 解释器",
                java.nio.file.Path.of("/usr/bin/python3"));
        if (python == null) {
            return;
        }
        if (!java.nio.file.Files.isRegularFile(python)) {
            ModernDialogs.message(owner, "Python 模块", "选择的文件不可用：" + python);
            return;
        }
        new javax.swing.SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    PythonModuleInstaller.install(python);
                    return "已安装到 " + python + "\n\n在 PyCharm 里重新加载解释器后即可 import mclite_bridge。";
                } catch (Exception ex) {
                    return "安装失败：" + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    ModernDialogs.message(owner, "Python 模块", get());
                } catch (Exception ex) {
                    ModernDialogs.message(owner, "Python 模块", "安装失败：" + ex.getMessage());
                }
            }
        }.execute();
    }

    private void restartFrame() {
        if (!canRestart.getAsBoolean()) {
            ModernDialogs.message(owner, "暂时无法重建界面",
                    "下载、安装或启动正在进行，请等任务结束后再应用界面样式。");
            return;
        }
        owner.dispose();
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
            owner.showError("保存界面样式失败", ex.getMessage());
        }
    }

}
