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
import java.awt.Insets;

final class LaunchPage {
    private final LauncherFrame owner;

    LaunchPage(LauncherFrame owner) {
        this.owner = owner;
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
        owner.launchSkin = new Ui.SkinHead(34);
        accountRow.add(owner.launchSkin, BorderLayout.WEST);
        owner.launchAccount = Ui.label(owner.config.username() + "  ·  离线账户", Theme.BODY_BOLD, Theme.TEXT);
        accountRow.add(owner.launchAccount, BorderLayout.CENTER);
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
        owner.launchVersion = Ui.label("未选择版本", Theme.TITLE, Theme.TEXT);
        owner.launchVersion.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.add(owner.launchVersion);
        version.add(Ui.vgap(4));
        owner.launchMeta = new JTextArea(3, 50);
        owner.launchMeta.setEditable(false);
        owner.launchMeta.setFocusable(false);
        owner.launchMeta.setOpaque(false);
        owner.launchMeta.setLineWrap(true);
        owner.launchMeta.setWrapStyleWord(true);
        owner.launchMeta.setFont(Theme.MONO);
        owner.launchMeta.setForeground(Theme.DIM);
        owner.launchMeta.setBorder(new EmptyBorder(0, 0, 0, 0));
        owner.launchMeta.setText("请到「下载中心」选择一个版本");
        owner.launchMeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.launchMeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        version.add(owner.launchMeta);
        form.add(LauncherLayout.lockedHeight(version));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel details = LauncherLayout.section("启动参数", "这些值来自「启动器配置」和「设置」");
        owner.launchJava = Ui.label("检测中…", Theme.MONO, Theme.MUTED);
        owner.launchMemory = Ui.label(owner.config.memoryMb() + " MB", Theme.MONO, Theme.MUTED);
        owner.launchJvm = Ui.label(owner.config.jvmArgs().isBlank() ? "无额外参数" : owner.config.jvmArgs(),
                Theme.MONO, Theme.MUTED);
        owner.launchAssets = Ui.label(owner.config.downloadAssets() ? "完整资源" : "仅核心文件",
                Theme.MONO, Theme.MUTED);
        details.add(infoLine("Java", owner.launchJava));
        details.add(Ui.vgap(8));
        details.add(infoLine("内存", owner.launchMemory));
        details.add(Ui.vgap(8));
        details.add(infoLine("资源", owner.launchAssets));
        details.add(Ui.vgap(8));
        details.add(infoLine("JVM", owner.launchJvm));
        form.add(LauncherLayout.lockedHeight(details));
        form.add(Ui.vgap(30));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        owner.installButton = Ui.button("下载 / 修复", Ui.AccentButton.Style.SECONDARY);
        owner.installButton.addActionListener(e -> owner.startInstall(false));
        owner.logButton = Ui.smallButton("日志", Ui.AccentButton.Style.GHOST);
        owner.logButton.addActionListener(e -> owner.openRecentLog());
        owner.cancelButton = Ui.smallButton("取消", Ui.AccentButton.Style.DANGER);
        owner.cancelButton.setVisible(false);
        owner.cancelButton.addActionListener(e -> owner.cancelTask());
        owner.launchButton = new Ui.AccentButton("启动游戏", Ui.AccentButton.Style.PRIMARY,
                Theme.H2, new Insets(10, 24, 10, 24));
        owner.launchButton.setPreferredSize(new Dimension(180, 40));
        owner.launchButton.setMaximumSize(new Dimension(180, 40));
        owner.launchButton.addActionListener(e -> owner.startLaunch());
        actions.add(owner.installButton);
        actions.add(Ui.hgap(8));
        actions.add(owner.logButton);
        actions.add(Ui.hgap(8));
        actions.add(owner.cancelButton);
        actions.add(Box.createHorizontalGlue());
        actions.add(owner.launchButton);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        form.add(actions);
        form.add(Ui.vgap(14));
        form.add(LauncherLayout.sidebarNote("启动前会检查客户端、运行库和资源；缺失时会提示下载 / 修复。"));
        form.add(Box.createVerticalGlue());
        page.add(form, BorderLayout.CENTER);
        owner.updateButtons();
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

}
