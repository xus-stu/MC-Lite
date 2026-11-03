package mclite;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;

final class ConfigPage {
    private final LauncherFrame owner;

    ConfigPage(LauncherFrame owner) {
        this.owner = owner;
    }

    JPanel build() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(LauncherLayout.pageHeader("启动器配置", "账户、游戏目录和 Java 运行时", null), BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(buildConfigForm());
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildConfigForm() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel account = LauncherLayout.section("账户", "离线账户只用于单机 / 局域网，正版服务器仍需正版登录");
        JPanel accountRow = new JPanel(new BorderLayout(10, 0));
        accountRow.setOpaque(false);
        owner.configSkin = new Ui.SkinHead(36);
        accountRow.add(owner.configSkin, BorderLayout.WEST);
        JPanel accountText = new JPanel();
        accountText.setOpaque(false);
        accountText.setLayout(new BoxLayout(accountText, BoxLayout.Y_AXIS));
        owner.configAccountLabel = Ui.label(owner.config.username(), Theme.BODY_BOLD, Theme.TEXT);
        JLabel accountType = Ui.label("离线账户", Theme.TINY, Theme.DIM);
        accountText.add(owner.configAccountLabel);
        accountText.add(Ui.vgap(1));
        accountText.add(accountType);
        accountRow.add(accountText, BorderLayout.CENTER);
        Ui.AccentButton manage = Ui.smallButton("登录 / 账户管理", Ui.AccentButton.Style.SECONDARY);
        manage.addActionListener(e -> owner.openAccountsDialog());
        accountRow.add(manage, BorderLayout.EAST);
        accountRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        accountRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        account.add(accountRow);
        account.add(Ui.vgap(7));
        account.add(LauncherLayout.sidebarNote("账户保存在 " + owner.config.accountsDir()
                + "，可在账户管理里修改保存目录；创建离线账户不需要密码。"));
        form.add(LauncherLayout.lockedHeight(account));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel directory = LauncherLayout.section("游戏目录", "版本、存档、资源和日志都会放在这个目录");
        JPanel dirRow = new JPanel(new BorderLayout(6, 0));
        dirRow.setOpaque(false);
        owner.gameDirField = Ui.field(owner.config.gameDir().toString(), "游戏目录");
        owner.gameDirField.setCaretPosition(0);
        owner.gameDirField.setToolTipText("Minecraft 的 .minecraft 目录。");
        owner.gameDirField.addActionListener(e -> owner.applyGameDirFromField());
        owner.gameDirField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                owner.applyGameDirFromField();
            }
        });
        dirRow.add(owner.gameDirField, BorderLayout.CENTER);
        Ui.AccentButton browseDir = Ui.smallButton("选择", Ui.AccentButton.Style.SECONDARY);
        browseDir.setPreferredSize(new Dimension(62, 32));
        browseDir.setMaximumSize(new Dimension(62, 32));
        browseDir.addActionListener(e -> owner.chooseGameDirectory());
        dirRow.add(browseDir, BorderLayout.EAST);
        dirRow.setMaximumSize(new Dimension(720, 32));
        dirRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        directory.add(dirRow);
        directory.add(Ui.vgap(7));
        JPanel dirLinks = new JPanel();
        dirLinks.setOpaque(false);
        dirLinks.setLayout(new BoxLayout(dirLinks, BoxLayout.X_AXIS));
        owner.openDirButton = Ui.smallButton("打开游戏目录", Ui.AccentButton.Style.LINK);
        owner.openDirButton.addActionListener(e -> owner.openGameDirectory());
        dirLinks.add(owner.openDirButton);
        dirLinks.add(Ui.hgap(16));
        Ui.AccentButton importInstance = Ui.smallButton("导入已有实例", Ui.AccentButton.Style.LINK);
        importInstance.addActionListener(e -> owner.chooseInstanceFolder());
        dirLinks.add(importInstance);
        dirLinks.add(Ui.hgap(16));
        Ui.AccentButton importHmcl = Ui.smallButton("读取 HMCL 目录", Ui.AccentButton.Style.LINK);
        importHmcl.addActionListener(e -> owner.importHmclDirectories());
        dirLinks.add(importHmcl);
        dirLinks.add(Box.createHorizontalGlue());
        dirLinks.setAlignmentX(Component.LEFT_ALIGNMENT);
        directory.add(dirLinks);
        directory.add(Ui.vgap(10));
        Ui.ToggleChip isolate = new Ui.ToggleChip("版本隔离（每个版本独立存档 / mods / 配置）");
        isolate.setSelected(owner.config.isolateVersions());
        isolate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        isolate.setAlignmentX(Component.LEFT_ALIGNMENT);
        isolate.addActionListener(e -> {
            owner.config.isolateVersions(isolate.isSelected());
            owner.config.save();
        });
        directory.add(isolate);
        directory.add(Ui.vgap(6));
        directory.add(LauncherLayout.sidebarNote("开启后游戏目录使用 versions/<版本>/ 作为实例目录；"
                + "关闭时所有版本共用同一个 .minecraft。"));
        form.add(LauncherLayout.lockedHeight(directory));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel java = LauncherLayout.section("Java 运行时", "自动选择会优先匹配该版本需要的 Java 大版本");
        JPanel javaRow = new JPanel(new BorderLayout(6, 0));
        javaRow.setOpaque(false);
        owner.javaCombo.setPreferredSize(new Dimension(100, 32));
        owner.javaCombo.setEnabled(true);
        owner.javaCombo.setOpaque(true);
        owner.javaCombo.setBackground(Theme.CARD_ALT);
        owner.javaCombo.addActionListener(e -> {
            if (owner.refreshingJavaCombo) {
                return;
            }
            LauncherFrame.JavaChoice choice = (LauncherFrame.JavaChoice) owner.javaCombo.getSelectedItem();
            owner.config.javaPath(choice == null || choice.path() == null ? "" : choice.path().toString());
            owner.config.save();
            owner.updateJavaHint();
        });
        javaRow.add(owner.javaCombo, BorderLayout.CENTER);
        JPanel javaButtons = new JPanel();
        javaButtons.setOpaque(false);
        javaButtons.setLayout(new BoxLayout(javaButtons, BoxLayout.X_AXIS));
        Ui.AccentButton manageJava = Ui.smallButton("管理列表", Ui.AccentButton.Style.SECONDARY);
        manageJava.setPreferredSize(new Dimension(78, 32));
        manageJava.setMaximumSize(new Dimension(78, 32));
        manageJava.addActionListener(e -> owner.openJavaManager());
        Ui.AccentButton browseJava = Ui.smallButton("选择", Ui.AccentButton.Style.SECONDARY);
        browseJava.setPreferredSize(new Dimension(62, 32));
        browseJava.setMaximumSize(new Dimension(62, 32));
        browseJava.addActionListener(e -> owner.chooseJava());
        javaButtons.add(manageJava);
        javaButtons.add(Ui.hgap(6));
        javaButtons.add(browseJava);
        javaRow.add(javaButtons, BorderLayout.EAST);
        javaRow.setMaximumSize(new Dimension(720, 32));
        javaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        java.add(javaRow);
        java.add(Ui.vgap(6));
        owner.javaHint = new JTextArea(2, 40);
        owner.javaHint.setEditable(false);
        owner.javaHint.setFocusable(false);
        owner.javaHint.setOpaque(false);
        owner.javaHint.setLineWrap(true);
        owner.javaHint.setWrapStyleWord(true);
        owner.javaHint.setFont(Theme.TINY);
        owner.javaHint.setForeground(Theme.DIM);
        owner.javaHint.setBorder(new EmptyBorder(0, 0, 0, 0));
        owner.javaHint.setText("正在检测 Java…");
        owner.javaHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.javaHint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        java.add(owner.javaHint);
        java.add(Ui.vgap(8));
        owner.autoJavaToggle = new Ui.ToggleChip("缺少 Java 时自动从 Mojang 下载");
        owner.autoJavaToggle.setSelected(owner.config.autoDownloadJava());
        owner.autoJavaToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        owner.autoJavaToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        owner.autoJavaToggle.addActionListener(e -> {
            owner.config.autoDownloadJava(owner.autoJavaToggle.isSelected());
            owner.config.save();
        });
        java.add(owner.autoJavaToggle);
        form.add(LauncherLayout.lockedHeight(java));
        form.add(Box.createVerticalGlue());
        return form;
    }

}
