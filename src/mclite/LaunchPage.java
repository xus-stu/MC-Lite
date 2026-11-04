package mclite;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;

/** Launch page: account, selected version, resolved Java and action buttons. */
final class LaunchPage {
    private final LauncherFrame owner;
    private final Config config;
    private Ui.SkinHead accountSkin;
    private JLabel accountLabel;
    private JLabel versionLabel;
    private JTextArea metaArea;
    private JLabel javaLabel;
    private JLabel memoryLabel;
    private JLabel jvmLabel;
    private JLabel assetsLabel;
    private Ui.AccentButton installButton;
    private Ui.AccentButton logButton;
    private Ui.AccentButton cancelButton;
    private Ui.AccentButton launchButton;

    LaunchPage(LauncherFrame owner, Config config) {
        this.owner = owner;
        this.config = config;
    }

    JPanel build() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(LauncherLayout.pageHeader("启动游戏", "确认账户、版本和 Java 后启动", null), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel account = LauncherLayout.section("账户", "离线账户，仅用于单机 / 局域网");
        JPanel accountRow = new JPanel(new BorderLayout(10, 0));
        accountRow.setOpaque(false);
        accountSkin = new Ui.SkinHead(34);
        accountRow.add(accountSkin, BorderLayout.WEST);
        accountLabel = Ui.label(config.username() + "  ·  离线账户", Theme.BODY_BOLD, Theme.TEXT);
        accountRow.add(accountLabel, BorderLayout.CENTER);
        Ui.AccentButton switchAccount = Ui.smallButton("切换账户", Ui.AccentButton.Style.SECONDARY);
        switchAccount.addActionListener(e -> owner.openAccountsDialog());
        accountRow.add(switchAccount, BorderLayout.EAST);
        accountRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        accountRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        account.add(accountRow);
        form.add(LauncherLayout.lockedHeight(account));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel version = LauncherLayout.section("版本", "下载或修复会先补全客户端、运行库和资源");
        versionLabel = Ui.label("未选择版本", Theme.TITLE, Theme.TEXT);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.add(versionLabel);
        version.add(Ui.vgap(4));
        metaArea = new JTextArea(3, 50);
        metaArea.setEditable(false);
        metaArea.setFocusable(false);
        metaArea.setOpaque(false);
        metaArea.setLineWrap(true);
        metaArea.setWrapStyleWord(true);
        metaArea.setFont(Theme.MONO);
        metaArea.setForeground(Theme.DIM);
        metaArea.setBorder(new EmptyBorder(0, 0, 0, 0));
        metaArea.setText("请到「下载中心」选择一个版本");
        metaArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        version.add(metaArea);
        form.add(LauncherLayout.lockedHeight(version));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel details = LauncherLayout.section("启动参数", "这些值来自「启动器配置」和「设置」");
        javaLabel = Ui.label("检测中…", Theme.MONO, Theme.MUTED);
        memoryLabel = Ui.label(config.memoryMb() + " MB", Theme.MONO, Theme.MUTED);
        jvmLabel = Ui.label(config.jvmArgs().isBlank() ? "无额外参数" : config.jvmArgs(),
                Theme.MONO, Theme.MUTED);
        assetsLabel = Ui.label(config.downloadAssets() ? "完整资源" : "仅核心文件",
                Theme.MONO, Theme.MUTED);
        details.add(infoLine("Java", javaLabel));
        details.add(Ui.vgap(8));
        details.add(infoLine("内存", memoryLabel));
        details.add(Ui.vgap(8));
        details.add(infoLine("资源", assetsLabel));
        details.add(Ui.vgap(8));
        details.add(infoLine("JVM", jvmLabel));
        form.add(LauncherLayout.lockedHeight(details));
        form.add(Ui.vgap(30));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        installButton = Ui.button("下载 / 修复", Ui.AccentButton.Style.SECONDARY);
        installButton.addActionListener(e -> owner.startInstall(false));
        logButton = Ui.smallButton("日志", Ui.AccentButton.Style.GHOST);
        logButton.addActionListener(e -> owner.openRecentLog());
        cancelButton = Ui.smallButton("取消", Ui.AccentButton.Style.DANGER);
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> owner.cancelTask());
        launchButton = new Ui.AccentButton("启动游戏", Ui.AccentButton.Style.PRIMARY,
                Theme.H2, new Insets(10, 24, 10, 24));
        launchButton.setPreferredSize(new Dimension(180, 40));
        launchButton.setMaximumSize(new Dimension(180, 40));
        launchButton.addActionListener(e -> owner.startLaunch());
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
        form.add(LauncherLayout.sidebarNote("启动前会检查客户端、运行库和资源；缺失时会提示下载 / 修复。"));
        form.add(Box.createVerticalGlue());
        page.add(form, BorderLayout.CENTER);
        return page;
    }

    void setAccountName(String username) {
        if (accountLabel != null) {
            accountLabel.setText(username + "  ·  离线账户");
        }
    }

    void setAccount(String username, Image skin) {
        if (accountLabel != null) {
            accountLabel.setText(username + "  ·  离线账户");
        }
        if (accountSkin != null) {
            accountSkin.setSkin(skin);
        }
    }

    void setVersion(String text) {
        if (versionLabel != null) {
            versionLabel.setText(text);
        }
    }

    void setMeta(String text) {
        if (metaArea != null) {
            metaArea.setText(text);
        }
    }

    void setMemory(String text) {
        if (memoryLabel != null) {
            memoryLabel.setText(text);
        }
    }

    void setJvm(String text) {
        if (jvmLabel != null) {
            jvmLabel.setText(text);
        }
    }

    void setAssets(String text) {
        if (assetsLabel != null) {
            assetsLabel.setText(text);
        }
    }

    String javaText() {
        return javaLabel == null ? null : javaLabel.getText();
    }

    void setJava(String text) {
        if (javaLabel != null) {
            javaLabel.setText(text);
        }
    }

    void setInstallButton(String text, boolean enabled) {
        if (installButton != null) {
            installButton.setText(text);
            installButton.setEnabled(enabled);
        }
    }

    void setLaunchButton(String text, boolean enabled) {
        if (launchButton != null) {
            launchButton.setText(text);
            launchButton.setEnabled(enabled);
        }
    }

    /** Cancel button follows a running task; the log button is disabled while busy. */
    void setBusy(boolean busy) {
        if (cancelButton != null) {
            cancelButton.setVisible(busy);
            cancelButton.setEnabled(busy);
        }
        if (logButton != null) {
            logButton.setEnabled(!busy);
        }
    }

    void setCancelVisible(boolean visible) {
        if (cancelButton != null) {
            cancelButton.setVisible(visible);
        }
    }

    void setCancelEnabled(boolean enabled) {
        if (cancelButton != null) {
            cancelButton.setEnabled(enabled);
        }
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
}
