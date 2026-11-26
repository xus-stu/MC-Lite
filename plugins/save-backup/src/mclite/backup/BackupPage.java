package mclite.backup;

import mclite.Theme;
import mclite.Ui;
import mclite.plugin.Host;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.List;

/** 存档备份页：备份、列表、覆盖恢复。 */
final class BackupPage {

    private final Host host;
    private final JComboBox<Backups.World> worldBox = new Ui.DarkCombo<>();
    private final JPanel archiveRows = new JPanel();
    private final JLabel status = Ui.label("", Theme.TINY, Theme.DIM);
    private final Ui.ToggleChip autoBeforeRestore = new Ui.ToggleChip("恢复前先备份当前");

    BackupPage(Host host) {
        this.host = host;
        archiveRows.setOpaque(false);
        archiveRows.setLayout(new BoxLayout(archiveRows, BoxLayout.Y_AXIS));
        archiveRows.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private Path backupsDir() {
        return host.appHome().resolve("backups");
    }

    JPanel build() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        if (!(host.exclusiveChrome() && host.bareChrome())) {
            page.add(header(), BorderLayout.NORTH);
        }

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(18, 24, 24, 24));

        Ui.RoundedPanel worldCard = card();
        worldCard.add(Ui.label("存档", Theme.BODY_BOLD, Theme.TEXT));
        worldCard.add(Ui.vgap(6));
        JPanel row = row();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        worldBox.setPreferredSize(new Dimension(360, 30));
        worldBox.setMaximumSize(new Dimension(360, 30));
        row.add(worldBox);
        row.add(Ui.hgap(10));
        Ui.AccentButton refresh = Ui.smallButton("刷新", Ui.AccentButton.Style.GHOST);
        refresh.addActionListener(e -> reload());
        row.add(refresh);
        row.add(Ui.hgap(10));
        Ui.AccentButton backup = Ui.smallButton("立即备份", Ui.AccentButton.Style.PRIMARY);
        backup.addActionListener(e -> doBackup());
        row.add(backup);
        row.add(Ui.hgap(10));
        Ui.AccentButton open = Ui.smallButton("打开备份目录", Ui.AccentButton.Style.GHOST);
        open.addActionListener(e -> openBackupsDir());
        row.add(open);
        row.add(Box.createHorizontalGlue());
        worldCard.add(row);
        worldCard.add(Ui.vgap(8));
        autoBeforeRestore.setSelected(true);
        worldCard.add(autoBeforeRestore);
        worldCard.add(Ui.vgap(4));
        JLabel hint = Ui.label("备份保存在 " + backupsDir(), Theme.TINY, Theme.DIM);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        worldCard.add(hint);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        worldCard.add(status);
        body.add(worldCard);
        body.add(Ui.vgap(12));

        Ui.RoundedPanel listCard = card();
        listCard.add(Ui.label("备份列表", Theme.BODY_BOLD, Theme.TEXT));
        listCard.add(Ui.vgap(6));
        listCard.add(archiveRows);
        body.add(listCard);
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = Ui.scroll(body);
        page.add(scroll, BorderLayout.CENTER);
        reload();
        return page;
    }

    private JComponent header() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Theme.BG);
        header.setBorder(new EmptyBorder(9, 14, 9, 14));
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(Ui.label("存档备份", Theme.H1, Theme.TEXT));
        info.add(Ui.vgap(3));
        info.add(Ui.label("在这里备份和直接覆盖恢复存档，不用手动拖文件", Theme.TINY, Theme.DIM));
        if (host.exclusiveChrome()) {
            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
            Ui.AccentButton back = Ui.smallButton("< 返回", Ui.AccentButton.Style.GHOST);
            back.addActionListener(e -> host.backToFeatures());
            left.add(back);
            left.add(Ui.hgap(10));
            left.add(info);
            header.add(left, BorderLayout.WEST);
        } else {
            header.add(info, BorderLayout.WEST);
        }
        return header;
    }

    private void reload() {
        worldBox.removeAllItems();
        for (Backups.World world : Backups.worlds(host.instanceDir())) {
            worldBox.addItem(world);
        }
        if (worldBox.getItemCount() == 0) {
            status.setText("这个实例还没有存档。");
        } else {
            status.setText("共 " + worldBox.getItemCount() + " 个存档");
        }
        refreshArchives();
    }

    private void refreshArchives() {
        archiveRows.removeAll();
        List<Backups.Archive> archives = Backups.archives(backupsDir());
        if (archives.isEmpty()) {
            archiveRows.add(Ui.label("还没有备份：选一个存档点「立即备份」。", Theme.TINY, Theme.DIM));
        }
        for (Backups.Archive archive : archives) {
            archiveRows.add(archiveRow(archive));
            archiveRows.add(Ui.vgap(4));
        }
        archiveRows.revalidate();
        archiveRows.repaint();
    }

    private JComponent archiveRow(Backups.Archive archive) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(archive.time());
        row.add(Ui.label(archive.world() + " · " + when + " · " + Backups.human(archive.bytes()),
                Theme.MONO, Theme.MUTED), BorderLayout.CENTER);
        JPanel actions = row();
        Ui.AccentButton restore = Ui.smallButton("恢复", Ui.AccentButton.Style.SECONDARY);
        restore.addActionListener(e -> doRestore(archive));
        actions.add(restore);
        actions.add(Ui.hgap(6));
        Ui.AccentButton delete = Ui.smallButton("删除", Ui.AccentButton.Style.GHOST);
        delete.addActionListener(e -> {
            try {
                Files.deleteIfExists(archive.file());
                status.setText("已删除 " + archive.file().getFileName());
            } catch (Exception ex) {
                status.setText("删除失败：" + ex.getMessage());
            }
            refreshArchives();
        });
        actions.add(delete);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private void doBackup() {
        if (!(worldBox.getSelectedItem() instanceof Backups.World world)) {
            status.setText("先选一个存档。");
            return;
        }
        status.setText("正在备份 " + world.name() + " …");
        new javax.swing.SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return Backups.backup(world.dir(), backupsDir());
            }

            @Override
            protected void done() {
                try {
                    Path file = get();
                    status.setText("备份完成：" + file.getFileName()
                            + "（" + Backups.human(Backups.size(backupsDir())) + "）");
                } catch (Exception ex) {
                    status.setText("备份失败：" + ex.getMessage());
                }
                refreshArchives();
            }
        }.execute();
    }

    private void doRestore(Backups.Archive archive) {
        Backups.World world = null;
        for (int i = 0; i < worldBox.getItemCount(); i++) {
            Backups.World candidate = worldBox.getItemAt(i);
            if (candidate.name().equals(archive.world())) {
                world = candidate;
                break;
            }
        }
        String target = world == null ? archive.world() : world.name();
        boolean confirmed = mclite.ModernDialogs.confirm(null,
                "覆盖恢复存档",
                "将把「" + target + "」恢复为 " + archive.world() + " 于 "
                        + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(archive.time())
                        + " 的备份。\n当前存档会先改名保留（不会直接丢）。\n"
                        + "请确认游戏已经完全关闭。");
        if (!confirmed) {
            return;
        }
        status.setText("正在恢复 " + target + " …");
        final Backups.World resolved = world;
        new javax.swing.SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return Backups.restore(host.instanceDir().resolve("saves"), backupsDir(),
                        archive.file(), target, autoBeforeRestore.isSelected());
            }

            @Override
            protected void done() {
                try {
                    Path keep = get();
                    status.setText("已恢复「" + target + "」"
                            + (keep == null ? "" : "；原存档保留为 " + keep.getFileName()));
                } catch (Exception ex) {
                    status.setText("恢复失败：" + ex.getMessage());
                }
                reload();
            }
        }.execute();
    }

    private void openBackupsDir() {
        try {
            Files.createDirectories(backupsDir());
            java.awt.Desktop.getDesktop().open(backupsDir().toFile());
        } catch (Exception ex) {
            status.setText("无法打开备份目录：" + ex.getMessage());
        }
    }

    private static Ui.RoundedPanel card() {
        Ui.RoundedPanel panel = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER, 14);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(14, 16, 16, 16));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JPanel row() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }
}
