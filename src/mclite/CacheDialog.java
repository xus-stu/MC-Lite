package mclite;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 清理缓存对话框：把缓存拆成可勾选的条目（整合包归档逐个列出），只删勾中的。
 *
 * <p>不做自动清理：整合包归档玩家自己一般有备份，删不删由用户勾选决定。</p>
 */
final class CacheDialog {

    private record Item(String label, Path path, long size) {
        String text() {
            return label + "  ·  " + Formatting.bytes(size);
        }
    }

    private final Component parent;
    private final Config config;
    private final Runnable after;
    private final List<Item> items = new ArrayList<>();
    private final List<Ui.ToggleChip> chips = new ArrayList<>();
    private final JLabel selectedLabel = Ui.label("", Theme.TINY, Theme.DIM);
    private JDialog dialog;
    private Ui.AccentButton clearButton;

    private CacheDialog(Component parent, Config config, Runnable after) {
        this.parent = parent;
        this.config = config;
        this.after = after;
    }

    static void show(Component parent, Config config, Runnable after) {
        new CacheDialog(parent, config, after).open();
    }

    private void open() {
        items.addAll(scan(config.cacheDir()));
        dialog = new JDialog(parentFrame(), "清理缓存", true);
        dialog.setUndecorated(true);
        dialog.setBackground(new java.awt.Color(0, 0, 0, 0));

        JPanel root = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER_LIGHT, 18);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label("清理缓存", Theme.H2, Theme.TEXT), BorderLayout.WEST);
        Ui.AccentButton close = Ui.smallButton("×", Ui.AccentButton.Style.GHOST);
        close.addActionListener(e -> dialog.dispose());
        head.add(close, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(2, 0, 2, 0));
        JLabel hint = Ui.label("勾选要删除的项目，只删勾中的；游戏文件、存档和 Java 运行时都不受影响。",
                Theme.TINY, Theme.DIM);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(hint);
        body.add(Ui.vgap(10));

        if (items.isEmpty()) {
            body.add(Ui.label("缓存已经是空的。", Theme.BODY, Theme.MUTED));
        }
        for (Item item : items) {
            Ui.ToggleChip chip = new Ui.ToggleChip(item.text());
            chip.setAlignmentX(Component.LEFT_ALIGNMENT);
            chip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            chip.setPreferredSize(new Dimension(500, 34));
            chip.setToolTipText(item.path().toString());
            chip.addActionListener(e -> updateSelection());
            chips.add(chip);
            body.add(chip);
            body.add(Ui.vgap(6));
        }
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = Ui.scroll(body);
        int height = Math.min(360, Math.max(120, items.size() * 40 + 60));
        scroll.setPreferredSize(new Dimension(560, height));
        root.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.add(selectedLabel);
        actions.add(Box.createHorizontalGlue());
        Ui.AccentButton all = Ui.smallButton("全选", Ui.AccentButton.Style.GHOST);
        all.addActionListener(e -> {
            boolean selectAll = chips.stream().anyMatch(chip -> !chip.isSelected());
            for (Ui.ToggleChip chip : chips) {
                chip.setSelected(selectAll);
            }
            updateSelection();
        });
        Ui.AccentButton cancel = Ui.smallButton("取消", Ui.AccentButton.Style.GHOST);
        cancel.addActionListener(e -> dialog.dispose());
        clearButton = Ui.smallButton("清理选中", Ui.AccentButton.Style.PRIMARY);
        clearButton.addActionListener(e -> clearSelected());
        actions.add(all);
        actions.add(Ui.hgap(8));
        actions.add(cancel);
        actions.add(Ui.hgap(8));
        actions.add(clearButton);
        root.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        updateSelection();
        dialog.setVisible(true);
    }

    private void updateSelection() {
        List<Item> selected = selectedItems();
        long total = 0L;
        for (Item item : selected) {
            total += item.size();
        }
        selectedLabel.setText(selected.isEmpty() ? "未选择" : "已选 " + Formatting.bytes(total));
        if (clearButton != null) {
            clearButton.setEnabled(!selected.isEmpty());
        }
    }

    private List<Item> selectedItems() {
        List<Item> selected = new ArrayList<>();
        for (int i = 0; i < chips.size() && i < items.size(); i++) {
            if (chips.get(i).isSelected()) {
                selected.add(items.get(i));
            }
        }
        return selected;
    }

    private void clearSelected() {
        List<Item> selected = selectedItems();
        if (selected.isEmpty()) {
            return;
        }
        long freed = 0L;
        for (Item item : selected) {
            freed += CacheCleaner.remove(item.path());
        }
        dialog.dispose();
        if (after != null) {
            after.run();
        }
        ModernDialogs.message(parent, "清理完成", "已释放 " + Formatting.bytes(freed) + "。");
    }

    private java.awt.Frame parentFrame() {
        java.awt.Window window = parent instanceof java.awt.Window w ? w
                : SwingUtilities.getWindowAncestor(parent);
        return window instanceof java.awt.Frame frame ? frame : null;
    }

    private static List<Item> scan(Path cache) {
        List<Item> list = new ArrayList<>();
        if (cache == null || !Files.isDirectory(cache)) {
            return list;
        }
        Path packs = cache.resolve("packs");
        if (Files.isDirectory(packs)) {
            try (Stream<Path> stream = Files.list(packs)) {
                stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparingLong(CacheDialog::fileSize).reversed()
                                .thenComparing(path -> path.getFileName().toString()))
                        .forEach(path -> list.add(new Item("整合包归档 · " + path.getFileName(),
                                path, fileSize(path))));
            } catch (IOException ignored) {
                // 读不了就跳过。
            }
        }
        Path icons = cache.resolve("icons");
        long iconSize = CacheCleaner.size(icons);
        if (iconSize > 0) {
            list.add(new Item("图标缓存（" + countFiles(icons) + " 个文件）", icons, iconSize));
        }
        addIfPresent(list, cache.resolve("loader-installer.jar"), "加载器安装器");
        addIfPresent(list, cache.resolve("authlib-injector.jar"), "外置登录注入");
        addIfPresent(list, cache.resolve("version_manifest_v2.json"), "版本索引");
        addIfPresent(list, cache.resolve("java-runtimes.json"), "运行时索引");
        return list;
    }

    private static void addIfPresent(List<Item> list, Path path, String label) {
        long size = fileSize(path);
        if (size > 0) {
            list.add(new Item(label + " · " + path.getFileName(), path, size));
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static long countFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).count();
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
