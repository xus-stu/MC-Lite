package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;

/** Project detail card: project header, file versions and dependency panel. */
final class ProjectDetailPage {
    private final Runnable onBack;
    private final Runnable onRefresh;
    private final Runnable onInstall;
    private final Runnable onMcmod;
    private final Consumer<HyperlinkEvent> onDependencyLink;
    private final Consumer<Modrinth.Version> onVersionSelected;
    private Ui.ProjectIcon icon;
    private JLabel title;
    private JLabel meta;
    private JLabel summary;
    private Ui.AccentButton mcmodButton;
    private DefaultListModel<Modrinth.Version> versionModel;
    private JList<Modrinth.Version> versionList;
    private JEditorPane dependencyText;
    private Ui.AccentButton refreshButton;
    private Ui.AccentButton installButton;

    ProjectDetailPage(Runnable onBack, Runnable onRefresh, Runnable onInstall, Runnable onMcmod,
                      Consumer<HyperlinkEvent> onDependencyLink,
                      Consumer<Modrinth.Version> onVersionSelected) {
        this.onBack = onBack;
        this.onRefresh = onRefresh;
        this.onInstall = onInstall;
        this.onMcmod = onMcmod;
        this.onDependencyLink = onDependencyLink;
        this.onVersionSelected = onVersionSelected;
    }

    JPanel build(ListCellRenderer<Modrinth.Version> renderer) {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        Ui.AccentButton back = Ui.smallButton("返回内容", Ui.AccentButton.Style.GHOST);
        back.addActionListener(e -> onBack.run());
        page.add(LauncherLayout.pageHeader("文件版本", "选择具体文件版本后安装", back), BorderLayout.NORTH);

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBackground(Theme.CARD);
        top.setBorder(new EmptyBorder(12, 14, 12, 14));
        icon = new Ui.ProjectIcon(64);
        top.add(icon, BorderLayout.WEST);
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        title = Ui.label("", Theme.H1, Theme.TEXT);
        meta = Ui.label("", Theme.MONO, Theme.DIM);
        summary = new JLabel();
        summary.setFont(Theme.TINY);
        summary.setForeground(Theme.MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(title);
        info.add(Ui.vgap(2));
        info.add(meta);
        info.add(Ui.vgap(3));
        info.add(summary);
        top.add(info, BorderLayout.CENTER);
        mcmodButton = Ui.smallButton("MC百科", Ui.AccentButton.Style.LINK);
        mcmodButton.addActionListener(e -> onMcmod.run());
        top.add(mcmodButton, BorderLayout.EAST);
        page.add(top, BorderLayout.NORTH);

        versionModel = new DefaultListModel<>();
        versionList = new JList<>(versionModel);
        versionList.setBackground(Theme.BG);
        versionList.setSelectionBackground(Theme.BG);
        versionList.setFixedCellHeight(50);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setCellRenderer(renderer);
        versionList.setBorder(new EmptyBorder(4, 8, 8, 8));
        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Modrinth.Version selected = versionList.getSelectedValue();
                if (installButton != null) {
                    installButton.setEnabled(selected != null);
                }
                onVersionSelected.accept(selected);
            }
        });
        versionList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                versionList.putClientProperty("hoverIndex",
                        versionList.locationToIndex(e.getPoint()));
                versionList.repaint();
            }
        });
        versionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                versionList.putClientProperty("hoverIndex", -1);
                versionList.repaint();
            }
        });
        JPanel versionArea = new JPanel(new BorderLayout(10, 0));
        versionArea.setOpaque(false);
        versionArea.add(Ui.scrollDark(versionList), BorderLayout.CENTER);

        JPanel dependencyPanel = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER, 14);
        dependencyPanel.setLayout(new BorderLayout(0, 8));
        dependencyPanel.setBorder(new EmptyBorder(12, 14, 12, 14));
        dependencyPanel.setPreferredSize(new Dimension(310, 0));
        dependencyPanel.add(Ui.label("版本详情 · 前置模组", Theme.TINY, Theme.DIM), BorderLayout.NORTH);
        dependencyText = new JEditorPane();
        dependencyText.setContentType("text/html");
        dependencyText.setEditable(false);
        dependencyText.setOpaque(false);
        dependencyText.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        dependencyText.setForeground(Theme.MUTED);
        dependencyText.setFont(Theme.MONO);
        dependencyText.setBorder(new EmptyBorder(2, 0, 2, 0));
        dependencyText.addHyperlinkListener(onDependencyLink::accept);
        dependencyText.setText("选择一个文件版本");
        dependencyPanel.add(Ui.scrollDark(dependencyText), BorderLayout.CENTER);
        versionArea.add(dependencyPanel, BorderLayout.EAST);
        page.add(versionArea, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBackground(Theme.CARD);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(7, 14, 7, 14)));
        JLabel hint = Ui.label("选择一个文件版本", Theme.MONO, Theme.DIM);
        bottom.add(hint, BorderLayout.CENTER);
        JPanel projectActions = new JPanel();
        projectActions.setOpaque(false);
        projectActions.setLayout(new BoxLayout(projectActions, BoxLayout.X_AXIS));
        refreshButton = Ui.smallButton("刷新版本", Ui.AccentButton.Style.GHOST);
        refreshButton.addActionListener(e -> onRefresh.run());
        installButton = Ui.smallButton("安装此版本", Ui.AccentButton.Style.PRIMARY);
        installButton.setEnabled(false);
        installButton.addActionListener(e -> onInstall.run());
        projectActions.add(refreshButton);
        projectActions.add(Ui.hgap(6));
        projectActions.add(installButton);
        bottom.add(projectActions, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);
        return page;
    }

    void setProject(String projectTitle, String projectMeta, String summaryHtml, String iconUrl) {
        if (title != null) {
            title.setText(projectTitle == null ? "" : projectTitle);
        }
        if (meta != null) {
            meta.setText(projectMeta == null ? "" : projectMeta);
        }
        if (summary != null) {
            summary.setText(summaryHtml == null ? "" : summaryHtml);
        }
        if (icon != null) {
            icon.setUrl(iconUrl);
        }
    }

    void setSummaryHtml(String summaryHtml) {
        if (summary != null) {
            summary.setText(summaryHtml == null ? "" : summaryHtml);
        }
    }

    void setIconUrl(String iconUrl) {
        if (icon != null) {
            icon.setUrl(iconUrl);
        }
    }

    void setMcmodVisible(boolean visible) {
        if (mcmodButton != null) {
            mcmodButton.setVisible(visible);
        }
    }

    void clearVersions() {
        if (versionModel != null) {
            versionModel.clear();
        }
    }

    void addVersion(Modrinth.Version version) {
        if (versionModel != null) {
            versionModel.addElement(version);
        }
    }

    int versionCount() {
        return versionModel == null ? 0 : versionModel.size();
    }

    Modrinth.Version versionAt(int index) {
        return versionModel == null || index < 0 || index >= versionModel.size()
                ? null : versionModel.get(index);
    }

    Modrinth.Version selectedVersion() {
        return versionList == null ? null : versionList.getSelectedValue();
    }

    void selectFirstVersion() {
        if (versionList != null && versionModel != null && !versionModel.isEmpty()) {
            versionList.setSelectedIndex(0);
        }
    }

    void setInstallEnabled(boolean enabled) {
        if (installButton != null) {
            installButton.setEnabled(enabled);
        }
    }

    void setRefreshEnabled(boolean enabled) {
        if (refreshButton != null) {
            refreshButton.setEnabled(enabled);
        }
    }

    void setDependencyText(String text) {
        if (dependencyText != null) {
            dependencyText.setText(text == null ? "" : text);
        }
    }

    void setDependencyHtmlDocument(String html) {
        if (dependencyText != null) {
            dependencyText.setText(html == null ? "" : html);
        }
    }

    void scrollDependencyToTop() {
        if (dependencyText != null) {
            dependencyText.setCaretPosition(0);
        }
    }

    void setMcmodTooltip(String tooltip) {
        if (mcmodButton != null) {
            mcmodButton.setToolTipText(tooltip);
        }
    }
}
