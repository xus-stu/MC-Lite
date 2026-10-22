package mclite;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;

public final class RegionDialog extends JDialog {
    static final String OFFICIAL = "https://www.minecraft.net/zh-hans/download";
    static final String CHINA = "https://mc.163.com/";

    public RegionDialog(Component parent) {
        super(parent == null ? null : (Frame) javax.swing.SwingUtilities.getWindowAncestor(parent),
                "下载 Minecraft", true);
        setUndecorated(true);
        setBackground(Theme.CARD);
        JPanel root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 18);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(20, 22, 16, 22));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label("下载 Minecraft", Theme.H2, Theme.TEXT), BorderLayout.WEST);
        Ui.AccentButton close = Ui.smallButton("×", Ui.AccentButton.Style.GHOST);
        close.addActionListener(e -> dispose());
        head.add(close, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        JLabel text = Ui.label("<html><body style='width:460px'>"
                + "<b>为遵守相关法律法规，我们不对中国大陆地区提供游戏本体和整合包下载服务。</b><br><br>"
                + "模组下载服务可用，资源包和光影也可以正常下载。"
                + "游戏本体请前往官网自行下载，安装完成后在启动器配置中选择游戏目录，再刷新版本列表。"
                + "<br><br>感谢您的理解与支持。"
                + "</body></html>", Theme.BODY, Theme.MUTED);
        root.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new javax.swing.BoxLayout(actions,
                javax.swing.BoxLayout.X_AXIS));
        Ui.AccentButton official = Ui.smallButton("Minecraft 官网",
                Ui.AccentButton.Style.PRIMARY);
        official.addActionListener(e -> Browser.open(OFFICIAL));
        Ui.AccentButton china = Ui.smallButton("中国版官网",
                Ui.AccentButton.Style.SECONDARY);
        china.addActionListener(e -> Browser.open(CHINA));
        Ui.AccentButton closeBottom = Ui.smallButton("关闭",
                Ui.AccentButton.Style.GHOST);
        closeBottom.addActionListener(e -> dispose());
        actions.add(official);
        actions.add(Ui.hgap(8));
        actions.add(china);
        actions.add(javax.swing.Box.createHorizontalGlue());
        actions.add(closeBottom);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(520, 240));
        setLocationRelativeTo(parent);
    }
}
