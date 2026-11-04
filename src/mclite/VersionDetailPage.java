package mclite;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;

/** Version install options card: target version, loader choice and recommended mods. */
final class VersionDetailPage {
    private final Runnable onBack;
    private final Consumer<String> onLoaderChosen;
    private final Runnable onInstall;
    private JLabel title;
    private JTextArea meta;
    private JTextField loaderVersion;
    private Ui.AccentButton installButton;
    private Ui.RoundedPanel recommendations;
    private String loaderType = "vanilla";
    private final java.util.List<Ui.SegmentButton> loaderButtons = new java.util.ArrayList<>();

    VersionDetailPage(Runnable onBack, Consumer<String> onLoaderChosen, Runnable onInstall) {
        this.onBack = onBack;
        this.onLoaderChosen = onLoaderChosen;
        this.onInstall = onInstall;
    }

    JPanel build() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        Ui.AccentButton back = Ui.smallButton("返回列表", Ui.AccentButton.Style.GHOST);
        back.addActionListener(e -> onBack.run());
        page.add(LauncherLayout.pageHeader("安装选项", "先选加载器，再下载原版与加载器", back),
                BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel version = LauncherLayout.section("版本", "下载客户端、运行库和资源");
        title = Ui.label("未选择版本", Theme.TITLE, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.add(title);
        version.add(Ui.vgap(5));
        meta = new JTextArea(3, 60);
        meta.setEditable(false);
        meta.setFocusable(false);
        meta.setOpaque(false);
        meta.setLineWrap(true);
        meta.setWrapStyleWord(true);
        meta.setFont(Theme.MONO);
        meta.setForeground(Theme.DIM);
        meta.setBorder(new EmptyBorder(0, 0, 0, 0));
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        version.add(meta);
        form.add(LauncherLayout.lockedHeight(version));
        form.add(Ui.vgap(30));
        form.add(LauncherLayout.divider());
        form.add(Ui.vgap(30));

        JPanel loader = LauncherLayout.section("模组启动器", "原版不需要加载器；版本留空时自动获取最新版");
        JPanel loaderChoices = new JPanel();
        loaderChoices.setOpaque(false);
        loaderChoices.setLayout(new BoxLayout(loaderChoices, BoxLayout.X_AXIS));
        ButtonGroup loaders = new ButtonGroup();
        for (String[] choice : new String[][]{{"原版", "vanilla"}, {"Fabric", "fabric"},
                {"Quilt", "quilt"}, {"Forge", "forge"}, {"NeoForge", "neoforge"}}) {
            Ui.SegmentButton button = new Ui.SegmentButton(choice[0]);
            button.setSelected("vanilla".equals(choice[1]));
            button.addActionListener(e -> {
                loaderType = choice[1];
                if (loaderVersion != null) {
                    loaderVersion.setText("");
                }
                onLoaderChosen.accept(choice[1]);
            });
            loaders.add(button);
            loaderButtons.add(button);
            loaderChoices.add(button);
        }
        loaderChoices.setAlignmentX(Component.LEFT_ALIGNMENT);
        loaderChoices.setMaximumSize(new Dimension(720, 28));
        loader.add(loaderChoices);
        loader.add(Ui.vgap(10));
        loaderVersion = Ui.field("留空 = 自动获取最新版本");
        loaderVersion.setPreferredSize(new Dimension(300, 32));
        loaderVersion.setMaximumSize(new Dimension(300, 32));
        loaderVersion.setAlignmentX(Component.LEFT_ALIGNMENT);
        loader.add(loaderVersion);
        loader.add(Ui.vgap(7));
        loader.add(LauncherLayout.sidebarNote("Forge / NeoForge 会自动查询当前游戏版本对应的最新版本。"
                + "也可以手动填写覆盖。"));
        recommendations = new Ui.RoundedPanel(Theme.CARD_ALT, Theme.BORDER, 14);
        recommendations.setLayout(new BoxLayout(recommendations, BoxLayout.Y_AXIS));
        recommendations.setBorder(new EmptyBorder(10, 10, 10, 10));
        recommendations.setAlignmentX(Component.LEFT_ALIGNMENT);
        recommendations.setVisible(false);
        loader.add(recommendations);
        loader.add(Ui.vgap(4));
        form.add(loader);
        form.add(Ui.vgap(30));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        installButton = Ui.button("下载原版", Ui.AccentButton.Style.PRIMARY);
        installButton.addActionListener(e -> onInstall.run());
        actions.add(installButton);
        actions.add(Box.createHorizontalGlue());
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(actions);
        form.add(Box.createVerticalGlue());
        page.add(Ui.scroll(form), BorderLayout.CENTER);
        return page;
    }

    /** Resets the loader choice and version text for a freshly opened version. */
    void reset(String versionId, String metaText) {
        loaderType = "vanilla";
        if (!loaderButtons.isEmpty()) {
            loaderButtons.get(0).setSelected(true);
        }
        if (loaderVersion != null) {
            loaderVersion.setText("");
            loaderVersion.setEnabled(true);
        }
        if (installButton != null) {
            installButton.setText("下载原版");
        }
        setTitle(versionId);
        setMeta(metaText);
    }

    void setTitle(String text) {
        if (title != null) {
            title.setText(text);
        }
    }

    void setMeta(String text) {
        if (meta != null) {
            meta.setText(text == null ? "" : text);
        }
    }

    String loaderType() {
        return loaderType;
    }

    void setLoaderType(String type) {
        this.loaderType = type == null ? "vanilla" : type;
    }

    String loaderVersionText() {
        return loaderVersion == null ? "" : loaderVersion.getText().trim();
    }

    void setLoaderVersionText(String text) {
        if (loaderVersion != null) {
            loaderVersion.setText(text == null ? "" : text);
        }
    }

    void setLoaderVersionEnabled(boolean enabled) {
        if (loaderVersion != null) {
            loaderVersion.setEnabled(enabled);
        }
    }

    void setInstallText(String text) {
        if (installButton != null) {
            installButton.setText(text);
        }
    }

    void setInstallEnabled(boolean enabled) {
        if (installButton != null) {
            installButton.setEnabled(enabled);
        }
    }

    JPanel recommendations() {
        return recommendations;
    }
}
