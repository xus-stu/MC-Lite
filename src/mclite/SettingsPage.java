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

    SettingsPage(LauncherFrame owner) {
        this.owner = owner;
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
        settingsActions.add(Ui.hgap(6));
        Ui.AccentButton checkUpdate = Ui.smallButton("检查更新", Ui.AccentButton.Style.GHOST);
        checkUpdate.addActionListener(e -> Updater.checkNow(owner.config, owner,
                owner.taskCenter, owner::refreshTasksUi));
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
        owner.memorySlider = new Ui.ValueSlider(512, 32768, 512, owner.config.memoryMb(), " MB");
        owner.memorySlider.addChangeListener(value -> {
            owner.config.memoryMb(value);
            owner.config.save();
            owner.updateLaunchInfo();
        });
        memoryRow.add(owner.memorySlider);
        memoryRow.add(Box.createHorizontalGlue());
        memoryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        memoryRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        performance.add(memoryRow);
        performance.add(Ui.vgap(8));
        owner.autoMemoryToggle = new Ui.ToggleChip("自动分配内存（推荐 "
                + owner.config.effectiveMemoryMb() + " MB）");
        owner.autoMemoryToggle.setSelected(owner.config.autoMemory());
        owner.autoMemoryToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.autoMemoryToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.autoMemoryToggle.addActionListener(e -> {
            owner.config.autoMemory(owner.autoMemoryToggle.isSelected());
            owner.config.save();
            owner.updateAutoMemoryControls();
            owner.updateLaunchInfo();
        });
        performance.add(owner.autoMemoryToggle);
        owner.updateAutoMemoryControls();
        performance.add(Ui.vgap(8));
        owner.autoJvmToggle = new Ui.ToggleChip("自动 JVM 优化参数（按 Java/版本选择 GC）");
        owner.autoJvmToggle.setSelected(owner.config.autoJvmTuning());
        owner.autoJvmToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.autoJvmToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.autoJvmToggle.addActionListener(e -> {
            owner.config.autoJvmTuning(owner.autoJvmToggle.isSelected());
            owner.config.save();
            owner.updateLaunchInfo();
        });
        performance.add(owner.autoJvmToggle);
        if (LauncherFrame.isMac()) {
            performance.add(Ui.vgap(8));
            owner.metalToggle = new Ui.ToggleChip("强制使用 Metal 渲染（实验性，重启生效）");
            owner.metalToggle.setSelected(owner.config.metalRendering());
            owner.metalToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            owner.metalToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            owner.metalToggle.setToolTipText("启用后使用 Metal 绘制界面；如果出现黑屏或花屏，关闭此选项并重启");
            owner.metalToggle.addActionListener(e -> {
                owner.config.metalRendering(owner.metalToggle.isSelected());
                owner.config.save();
                ModernDialogs.message(owner, "渲染设置",
                        owner.metalToggle.isSelected()
                                ? "已启用 Metal 渲染，重启启动器后生效。\n如果界面黑屏，请编辑配置关闭 metalRendering。"
                                : "已恢复软件渲染，重启启动器后生效。");
            });
            performance.add(owner.metalToggle);
        }
        performance.add(Ui.vgap(10));

        JLabel jvmLabel = Ui.label("额外 JVM 参数", Theme.TINY, Theme.DIM);
        jvmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        performance.add(jvmLabel);
        performance.add(Ui.vgap(4));
        owner.jvmArgsField = Ui.field(owner.config.jvmArgs(), "例如 -XX:+UseG1GC -Dfile.encoding=UTF-8");
        owner.jvmArgsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.jvmArgsField.setPreferredSize(new Dimension(720, 32));
        owner.jvmArgsField.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.jvmArgsField.addActionListener(e -> owner.saveJvmArgs());
        owner.jvmArgsField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                owner.saveJvmArgs();
            }
        });
        performance.add(owner.jvmArgsField);
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
        owner.widthSlider = new Ui.ValueSlider(320, 3840, 10, owner.config.windowWidth(), " px");
        owner.widthSlider.setPreferredSize(new Dimension(205, 30));
        owner.widthSlider.setMaximumSize(new Dimension(205, 30));
        owner.heightSlider = new Ui.ValueSlider(240, 2160, 10, owner.config.windowHeight(), " px");
        owner.heightSlider.setPreferredSize(new Dimension(205, 30));
        owner.heightSlider.setMaximumSize(new Dimension(205, 30));
        owner.widthSlider.addChangeListener(value -> {
            owner.config.windowWidth(value);
            owner.config.save();
        });
        owner.heightSlider.addChangeListener(value -> {
            owner.config.windowHeight(value);
            owner.config.save();
        });
        windowRow.add(owner.widthSlider);
        windowRow.add(Ui.hgap(10));
        windowRow.add(Ui.label("×", Theme.BODY, Theme.DIM));
        windowRow.add(Ui.hgap(10));
        windowRow.add(owner.heightSlider);
        windowRow.add(Box.createHorizontalGlue());
        windowRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        windowRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        window.add(windowRow);
        form.add(LauncherLayout.lockedHeight(window));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel download = LauncherLayout.section("下载", "完整资源包含声音、语言和图标");
        owner.assetsToggle = new Ui.ToggleChip("下载完整资源");
        owner.assetsToggle.setSelected(owner.config.downloadAssets());
        owner.assetsToggle.setToolTipText("首次下载约 300–500 MB；关闭后现代版本可能无法启动。");
        owner.assetsToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.assetsToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.assetsToggle.addActionListener(e -> {
            owner.config.downloadAssets(owner.assetsToggle.isSelected());
            owner.config.save();
            owner.updateDetailForSelection();
            owner.updateLaunchInfo();
        });
        download.add(owner.assetsToggle);
        download.add(Ui.vgap(10));
        owner.mirrorToggle = new Ui.ToggleChip("自动测试官方源 / 镜像源");
        owner.mirrorToggle.setSelected(owner.config.autoMirror());
        owner.mirrorToggle.setToolTipText("同时探测 Mojang 官方源和 BMCLAPI 镜像，选择更快的那个。");
        owner.mirrorToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.mirrorToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.mirrorToggle.addActionListener(e -> {
            owner.config.autoMirror(owner.mirrorToggle.isSelected());
            owner.config.save();
        });
        download.add(owner.mirrorToggle);
        download.add(Ui.vgap(8));
        owner.forceTranslationToggle = new Ui.ToggleChip("强制显示中文模组 / 整合包名称（MC百科）");
        owner.forceTranslationToggle.setSelected(owner.config.forceModTranslation());
        owner.forceTranslationToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.forceTranslationToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.forceTranslationToggle.addActionListener(e -> {
            owner.config.forceModTranslation(owner.forceTranslationToggle.isSelected());
            owner.config.save();
            if (owner.contentList != null) {
                owner.contentList.repaint();
            }
        });
        download.add(owner.forceTranslationToggle);
        download.add(Ui.vgap(8));
        owner.autoUpdateToggle = new Ui.ToggleChip("自动检查启动器更新（后台下载，下次启动生效）");
        owner.autoUpdateToggle.setSelected(owner.config.autoUpdate());
        owner.autoUpdateToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.autoUpdateToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.autoUpdateToggle.addActionListener(e -> {
            owner.config.autoUpdate(owner.autoUpdateToggle.isSelected());
            owner.config.save();
        });
        download.add(owner.autoUpdateToggle);
        download.add(Ui.vgap(8));
        owner.recommendToggle = new Ui.ToggleChip("安装加载器后显示推荐模组");
        owner.recommendToggle.setSelected(owner.config.recommendMods());
        owner.recommendToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.recommendToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.recommendToggle.addActionListener(e -> {
            owner.config.recommendMods(owner.recommendToggle.isSelected());
            owner.config.save();
            owner.updateRecommendations(owner.versionLoaderType);
        });
        download.add(owner.recommendToggle);
        download.add(Ui.vgap(8));
        Ui.ToggleChip integrityToggle = new Ui.ToggleChip("启动前检查游戏文件完整性");
        integrityToggle.setSelected(owner.config.integrityCheck());
        integrityToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        integrityToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        integrityToggle.addActionListener(e -> {
            owner.config.integrityCheck(integrityToggle.isSelected());
            owner.config.save();
        });
        download.add(integrityToggle);
        if (!RegionGate.locked()) {
            download.add(Ui.vgap(8));
            owner.removeRegionLimitsToggle = new Ui.ToggleChip("更新时移除地区限制（仅普通版）");
            owner.removeRegionLimitsToggle.setSelected(owner.config.removeRegionLimits());
            owner.removeRegionLimitsToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            owner.removeRegionLimitsToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            owner.removeRegionLimitsToggle.addActionListener(e -> {
                owner.config.removeRegionLimits(owner.removeRegionLimitsToggle.isSelected());
                owner.config.save();
            });
            download.add(owner.removeRegionLimitsToggle);
        }
        download.add(Ui.vgap(12));
        JLabel keyLabel = Ui.label("CurseForge API Key", Theme.TINY, Theme.DIM);
        keyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        download.add(keyLabel);
        download.add(Ui.vgap(4));
        owner.curseForgeKeyField = Ui.field(owner.config.curseForgeApiKey(),
                "在 CurseForge 控制台申请的个人 API Key");
        owner.curseForgeKeyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.curseForgeKeyField.setPreferredSize(new Dimension(720, 32));
        owner.curseForgeKeyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.curseForgeKeyField.addActionListener(e -> owner.saveCurseForgeKey());
        owner.curseForgeKeyField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                owner.saveCurseForgeKey();
            }
        });
        download.add(owner.curseForgeKeyField);
        download.add(Ui.vgap(6));
        download.add(LauncherLayout.sidebarNote("CurseForge 官方 API 需要个人 Key；Modrinth 不需要 Key。"
                + "HMCL 内置了它自己申请的 Key，本项目不能复用。"
                + "资源对象在多个版本之间共享；删除后可通过「修复 / 更新」补全。"));
        form.add(LauncherLayout.lockedHeight(download));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel benchmark = LauncherLayout.section("跑分模块", "可选下载，不随启动器捆绑，不强制安装");
        JLabel benchmarkStatus = Ui.label(BenchmarkManager.installed(owner.config)
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
            BenchmarkManager.download(owner.config, owner.taskCenter, owner::refreshTasksUi,
                    benchmarkStatus::setText);
        });
        Ui.AccentButton benchmarkRun = Ui.smallButton("运行跑分",
                Ui.AccentButton.Style.GHOST);
        benchmarkRun.addActionListener(e -> BenchmarkManager.run(owner.config, benchmarkStatus::setText));
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
        owner.fontCombo = new Ui.DarkCombo<>();
        java.util.List<FontCatalog.Choice> fontChoices = FontCatalog.choices();
        FontCatalog.Choice selectedFont = fontChoices.get(0);
        for (FontCatalog.Choice choice : fontChoices) {
            owner.fontCombo.addItem(choice);
            if (!owner.config.uiFontFamily().isBlank()
                    && owner.config.uiFontFamily().equals(choice.family())) {
                selectedFont = choice;
            }
        }
        owner.fontCombo.setSelectedItem(selectedFont);
        owner.fontCombo.setPreferredSize(new Dimension(280, 32));
        owner.fontCombo.setMaximumSize(new Dimension(280, 32));
        fontRow.add(owner.fontCombo);
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
        owner.uiCssArea = new JTextArea(loadUiCssText());
        owner.uiCssArea.setFont(Theme.MONO);
        owner.uiCssArea.setBackground(Theme.FIELD);
        owner.uiCssArea.setForeground(Theme.TEXT);
        owner.uiCssArea.setCaretColor(Theme.ACCENT);
        owner.uiCssArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        owner.uiCssArea.setLineWrap(true);
        owner.uiCssArea.setWrapStyleWord(true);
        JScrollPane cssScroll = Ui.scrollDark(owner.uiCssArea);
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
            owner.uiCssArea.setText(Theme.defaultCss());
            applyUiCss();
        });
        cssActions.add(applyCss);
        cssActions.add(Ui.hgap(8));
        cssActions.add(resetCss);
        cssActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        cssActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        style.add(cssActions);
        form.add(LauncherLayout.lockedHeight(style));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel data = LauncherLayout.section("数据目录", "启动器配置、缓存、账户和官方 Java 运行时");
        JLabel dataPath = Ui.label(owner.config.appHome().toString(), Theme.MONO, Theme.MUTED);
        dataPath.setAlignmentX(Component.LEFT_ALIGNMENT);
        data.add(dataPath);
        data.add(Ui.vgap(12));
        JPanel accountsRow = new JPanel(new BorderLayout(8, 0));
        accountsRow.setOpaque(false);
        owner.accountsDirLabel = Ui.label(owner.config.accountsDir().toString(), Theme.MONO, Theme.MUTED);
        owner.accountsDirLabel.setToolTipText(owner.config.accountsDir().toString());
        accountsRow.add(owner.accountsDirLabel, BorderLayout.CENTER);
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
                    if (owner.uiCssArea != null) {
                        owner.uiCssArea.setText(preset.css());
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
            Path file = owner.config.uiCssFile();
            if (Files.isRegularFile(file)) {
                return Files.readString(file);
            }
        } catch (IOException ex) {
            Log.warn("读取界面样式失败: " + ex.getMessage());
        }
        return Theme.defaultCss();
    }

    private void applyUiFont() {
        Object selected = owner.fontCombo == null ? null : owner.fontCombo.getSelectedItem();
        String family = selected instanceof FontCatalog.Choice choice ? choice.family() : "";
        owner.config.uiFontFamily(family);
        owner.config.save();
        Theme.setFontFamily(family);
        Theme.install();
        restartFrame();
    }

    private void restartFrame() {
        owner.dispose();
        SwingUtilities.invokeLater(() -> {
            LauncherFrame frame = new LauncherFrame(owner.config);
            frame.setVisible(true);
            frame.selectPage("settings");
        });
    }

    private void applyUiCss() {
        try {
            Path file = owner.config.uiCssFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, owner.uiCssArea.getText());
            Theme.applyCss(file);
            if (!owner.config.uiFontFamily().isBlank()) {
                Theme.setFontFamily(owner.config.uiFontFamily());
            }
            Theme.install();
            restartFrame();
        } catch (IOException ex) {
            owner.showError("保存界面样式失败", ex.getMessage());
        }
    }

}
