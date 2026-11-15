package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 「种子」大区：顶部固定实例/存档选择，下面分三个子页。
 *
 * <ul>
 *   <li>世界种子：读存档里的种子、版本、出生点。</li>
 *   <li>遗迹结构：扫描 region 的 structures.starts，列出已生成遗迹（含模组结构）。</li>
 *   <li>史莱姆区块：按种子计算。</li>
 * </ul>
 *
 * <p>所有卡片圆角，切页有淡入，行悬停有渐变，扫描有进度动画。</p>
 */
final class SeedPage {

    private static final int ROW_CAP = 200;

    private final Config config;
    private final Supplier<String> instance;

    private final JLabel instanceValue = Ui.label("—", Theme.MONO, Theme.DIM);
    private final JComboBox<WorldSeed.World> worldBox = new Ui.DarkCombo<>();
    private final JLabel[] seedLabels = {
            Ui.label("—", Theme.MONO, Theme.TEXT),
            Ui.label("—", Theme.MONO, Theme.TEXT),
            Ui.label("—", Theme.MONO, Theme.TEXT)
    };
    private final JLabel worldVersion = Ui.label("—", Theme.MONO, Theme.DIM);
    private final JLabel worldPlayed = Ui.label("—", Theme.MONO, Theme.DIM);
    private final JLabel worldSpawn = Ui.label("—", Theme.MONO, Theme.DIM);

    private final JComboBox<Filter> filterBox = new Ui.DarkCombo<>();
    private final JComboBox<Range> rangeBox = new Ui.DarkCombo<>();
    private final JComboBox<Range> slimeRange = new Ui.DarkCombo<>();
    private final Ui.AccentButton scanButton =
            Ui.smallButton("扫描存档", Ui.AccentButton.Style.PRIMARY);
    private final JLabel scanStatus = Ui.label("", Theme.TINY, Theme.DIM);
    private final Ui.ThinProgress progress = new Ui.ThinProgress();
    private final Ui.FadePanel structureRows = fadeColumn();
    private final Ui.FadePanel slimeRows = fadeColumn();
    private final JLabel slimeStatus = Ui.label("", Theme.TINY, Theme.DIM);

    private CardLayout cardsLayout;
    private JPanel cards;
    private Ui.SegmentButton worldTab;
    private Ui.SegmentButton structureTab;
    private Ui.SegmentButton slimeTab;

    private String loadedInstance = "";
    private Path instanceDir;
    private WorldSeed.World world;
    private List<StructureScan.Found> found = List.of();
    private int spawnX;
    private int spawnZ;
    private boolean revealAfterScan;
    private SwingWorker<List<StructureScan.Found>, Integer> worker;

    private record Filter(String text, String id) {
        @Override
        public String toString() {
            return text;
        }
    }

    private record Range(String text, int value) {
        @Override
        public String toString() {
            return text;
        }
    }

    SeedPage(Config config, Supplier<String> instance) {
        this.config = config;
        this.instance = instance;
    }

    // ------------------------------------------------------------------ 外壳

    JPanel build() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(LauncherLayout.pageHeader("种子查询",
                "当前实例的种子、已生成遗迹和史莱姆区块", null), BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        JPanel selector = buildSelector();
        selector.add(buildSubtabs(), BorderLayout.SOUTH);
        middle.add(selector, BorderLayout.NORTH);

        cardsLayout = new CardLayout();
        cards = new Ui.FadePanel(cardsLayout);
        cards.setOpaque(false);
        cards.add(buildWorldView(), "world");
        cards.add(buildStructureView(), "structure");
        cards.add(buildSlimeView(), "slime");
        middle.add(cards, BorderLayout.CENTER);
        page.add(middle, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildSelector() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.CARD);
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(8, 14, 8, 14)));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.add(Ui.label("当前实例", Theme.TINY, Theme.DIM));
        left.add(Ui.hgap(8));
        left.add(instanceValue);
        bar.add(left, BorderLayout.WEST);

        worldBox.setPreferredSize(new Dimension(380, 28));
        worldBox.addActionListener(e -> onWorldChanged());
        JPanel center = new JPanel(new BorderLayout(10, 0));
        center.setOpaque(false);
        center.add(Ui.label("存档", Theme.TINY, Theme.DIM), BorderLayout.WEST);
        center.add(worldBox, BorderLayout.CENTER);
        bar.add(center, BorderLayout.CENTER);

        Ui.AccentButton refresh = Ui.smallButton("刷新", Ui.AccentButton.Style.GHOST);
        refresh.addActionListener(e -> reloadWorlds());
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(refresh);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildSubtabs() {
        JPanel tabs = new JPanel();
        tabs.setOpaque(false);
        tabs.setLayout(new BoxLayout(tabs, BoxLayout.X_AXIS));
        worldTab = new Ui.SegmentButton("世界种子");
        structureTab = new Ui.SegmentButton("遗迹结构");
        slimeTab = new Ui.SegmentButton("史莱姆区块");
        ButtonGroup group = new ButtonGroup();
        group.add(worldTab);
        group.add(structureTab);
        group.add(slimeTab);
        worldTab.setSelected(true);
        worldTab.addActionListener(e -> selectTab("world"));
        structureTab.addActionListener(e -> selectTab("structure"));
        slimeTab.addActionListener(e -> selectTab("slime"));
        tabs.add(worldTab);
        tabs.add(structureTab);
        tabs.add(slimeTab);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(4, 14, 0, 14));
        bar.add(tabs, BorderLayout.WEST);
        return bar;
    }

    void selectTab(String name) {
        if (cardsLayout == null || cards == null) {
            return;
        }
        String target = switch (name == null ? "" : name) {
            case "structure" -> "structure";
            case "slime" -> "slime";
            default -> "world";
        };
        cardsLayout.show(cards, target);
        if (worldTab != null) {
            worldTab.setSelected("world".equals(target));
            structureTab.setSelected("structure".equals(target));
            slimeTab.setSelected("slime".equals(target));
        }
        if (cards instanceof Ui.FadePanel fade) {
            fade.fadeIn();
        }
    }

    // ------------------------------------------------------------------ 子页

    private JComponent buildWorldView() {
        JPanel body = column();
        Ui.RoundedPanel seeds = card();
        seeds.add(heading("种子", "从存档的 WorldGenSettings / world_gen_settings.dat 读取"));
        String[] names = {"主世界", "下界", "末地"};
        for (int i = 0; i < names.length; i++) {
            seeds.add(seedRow(names[i], seedLabels[i]));
            if (i < names.length - 1) {
                seeds.add(Ui.vgap(4));
            }
        }
        body.add(seeds);
        body.add(Ui.vgap(14));

        Ui.RoundedPanel info = card();
        info.add(heading("这个世界", "版本、最后游玩时间和出生点，都来自 level.dat"));
        info.add(infoRow("版本", worldVersion));
        info.add(Ui.vgap(6));
        info.add(infoRow("最后游玩", worldPlayed));
        info.add(Ui.vgap(6));
        info.add(infoRow("出生点", worldSpawn));
        body.add(info);
        body.add(Box.createVerticalGlue());
        return scroll(body);
    }

    private JComponent buildStructureView() {
        JPanel body = column();
        Ui.RoundedPanel card = card();
        card.add(heading("已生成遗迹 / 结构",
                "扫描 region 的 structures.starts：要塞（末地门）、下界要塞、矿井、村庄与模组结构都在内"));

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        filterBox.addItem(new Filter("常见遗迹", null));
        filterBox.addItem(new Filter("全部结构", "*"));
        filterBox.setPreferredSize(new Dimension(210, 28));
        filterBox.setMaximumSize(new Dimension(210, 28));
        filterBox.addActionListener(e -> renderStructures());

        rangeBox.addItem(new Range("500 格内", 500));
        rangeBox.addItem(new Range("2000 格内", 2000));
        rangeBox.addItem(new Range("5000 格内", 5000));
        rangeBox.addItem(new Range("不限范围", 0));
        rangeBox.setSelectedIndex(1);
        rangeBox.setPreferredSize(new Dimension(130, 28));
        rangeBox.setMaximumSize(new Dimension(130, 28));
        rangeBox.addActionListener(e -> renderStructures());

        controls.add(filterBox);
        controls.add(Ui.hgap(8));
        controls.add(rangeBox);
        controls.add(Ui.hgap(10));
        scanButton.addActionListener(e -> scan());
        controls.add(scanButton);
        controls.add(Box.createHorizontalGlue());
        card.add(controls);
        card.add(Ui.vgap(10));

        progress.setVisible(false);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        card.add(progress);
        card.add(Ui.vgap(6));

        scanStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(scanStatus);
        card.add(Ui.vgap(8));
        card.add(structureRows);
        body.add(card);
        body.add(Box.createVerticalGlue());
        return scroll(body);
    }

    private JComponent buildSlimeView() {
        JPanel body = column();
        Ui.RoundedPanel card = card();
        card.add(heading("史莱姆区块", "按种子计算，和生物群系无关；只按主世界种子算"));
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        slimeRange.addItem(new Range("半径 16 区块", 16));
        slimeRange.addItem(new Range("半径 32 区块", 32));
        slimeRange.addItem(new Range("半径 64 区块", 64));
        slimeRange.setSelectedIndex(1);
        slimeRange.setPreferredSize(new Dimension(140, 28));
        slimeRange.setMaximumSize(new Dimension(140, 28));
        controls.add(slimeRange);
        controls.add(Ui.hgap(10));
        Ui.AccentButton find = Ui.smallButton("查找最近的区块", Ui.AccentButton.Style.SECONDARY);
        find.addActionListener(e -> findSlimes());
        controls.add(find);
        controls.add(Box.createHorizontalGlue());
        card.add(controls);
        card.add(Ui.vgap(8));
        slimeStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(slimeStatus);
        card.add(Ui.vgap(8));
        card.add(slimeRows);
        body.add(card);
        body.add(Box.createVerticalGlue());
        return scroll(body);
    }

    // ------------------------------------------------------------------ 通用件

    private static JPanel column() {
        JPanel body = new JPanel();
        body.setOpaque(true);
        body.setBackground(Theme.BG);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(20, 26, 28, 26));
        return body;
    }

    private static JComponent scroll(JPanel body) {
        JScrollPane scroll = Ui.scroll(body);
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new java.awt.Point(0, 0)));
        return scroll;
    }

    private static Ui.FadePanel fadeColumn() {
        Ui.FadePanel panel = new Ui.FadePanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static Ui.RoundedPanel card() {
        Ui.RoundedPanel panel = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER, 14);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(14, 16, 16, 16));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JComponent heading(String text, String hint) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Ui.label(text, Theme.H1, Theme.TEXT));
        if (hint != null && !hint.isBlank()) {
            panel.add(Ui.vgap(3));
            panel.add(Ui.label(hint, Theme.TINY, Theme.DIM));
        }
        panel.add(Ui.vgap(10));
        return panel;
    }

    private static JComponent infoRow(String name, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.add(Ui.label(name, Theme.TINY, Theme.DIM), BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private JComponent seedRow(String name, JLabel value) {
        Row row = new Row(name, "", "复制", "复制这个种子");
        row.setDetail(value);
        row.setAction(() -> copy(value.getText()), "种子已复制");
        return row;
    }

    // ------------------------------------------------------------------ 数据

    void onShown() {
        String id = instance.get();
        instanceValue.setText(id == null || id.isBlank() ? "未选择版本" : id);
        Path dir = config.effectiveGameDir(id);
        String key = String.valueOf(dir);
        if (!key.equals(loadedInstance)) {
            loadedInstance = key;
            instanceDir = dir;
            reloadWorlds();
        } else if (worldBox.getItemCount() == 0) {
            reloadWorlds();
        }
    }

    private void reloadWorlds() {
        if (instanceDir == null) {
            return;
        }
        List<WorldSeed.World> worlds = WorldSeed.list(instanceDir);
        worldBox.removeAllItems();
        for (WorldSeed.World item : worlds) {
            worldBox.addItem(item);
        }
        if (worlds.isEmpty()) {
            world = null;
            for (JLabel label : seedLabels) {
                label.setText("—");
            }
            worldVersion.setText("—");
            worldPlayed.setText("—");
            worldSpawn.setText("—");
            found = List.of();
            structureRows.removeAll();
            structureRows.revalidate();
            structureRows.repaint();
            scanStatus.setText("这个实例还没有存档（saves 目录为空），进游戏存一次档就能读种子。");
            return;
        }
        if (worldBox.getSelectedIndex() < 0) {
            worldBox.setSelectedIndex(0);
        }
        onWorldChanged();
    }

    private void onWorldChanged() {
        world = worldBox.getSelectedItem() instanceof WorldSeed.World selected ? selected : null;
        if (world == null) {
            return;
        }
        Long[] seeds = {world.seedOf("minecraft:overworld"), world.seedOf("minecraft:the_nether"),
                world.seedOf("minecraft:the_end")};
        for (int i = 0; i < seeds.length; i++) {
            seedLabels[i].setText(seeds[i] == null || seeds[i] == 0L ? "—" : String.valueOf(seeds[i]));
        }
        worldVersion.setText(world.version().isBlank() ? "—" : world.version());
        worldPlayed.setText(world.when().isBlank() ? "—" : world.when());
        int[] spawn = WorldSeed.spawn(world.dir());
        spawnX = spawn[0];
        spawnZ = spawn[1];
        worldSpawn.setText("X " + spawnX + " Z " + spawnZ);
        found = List.of();
        for (JComponent panel : new JComponent[]{structureRows, slimeRows}) {
            panel.removeAll();
            panel.revalidate();
            panel.repaint();
        }
        slimeStatus.setText("");
        filterBox.setSelectedIndex(0);
        scanStatus.setText("点「扫描存档」读取已生成结构（" + world.name() + "）。");
    }

    void scan() {
        if (world == null) {
            scanStatus.setText("先选一个存档。");
            return;
        }
        if (worker != null) {
            worker.cancel(true);
        }
        scanButton.setEnabled(false);
        scanButton.setText("扫描中…");
        progress.setVisible(true);
        progress.setIndeterminate(true);
        scanStatus.setText("正在读取区域文件…");
        Path dir = world.dir();
        worker = new SwingWorker<>() {
            @Override
            protected List<StructureScan.Found> doInBackground() {
                return StructureScan.scan(dir, new StructureScan.Listener() {
                    @Override
                    public void progress(int done, int total) {
                        publish(total <= 0 ? 0 : done * 100 / total);
                    }

                    @Override
                    public boolean cancelled() {
                        return isCancelled();
                    }
                });
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (chunks.isEmpty() || isCancelled()) {
                    return;
                }
                int percent = chunks.get(chunks.size() - 1);
                progress.setIndeterminate(false);
                progress.setValue(percent / 100.0);
                scanStatus.setText("扫描中 " + percent + "%");
            }

            @Override
            protected void done() {
                scanButton.setEnabled(true);
                scanButton.setText("扫描存档");
                progress.setVisible(false);
                progress.setIndeterminate(false);
                if (isCancelled()) {
                    scanStatus.setText("已取消。");
                    return;
                }
                try {
                    found = get();
                } catch (Exception ex) {
                    found = List.of();
                    scanStatus.setText("扫描失败：" + ex.getMessage());
                    return;
                }
                rebuildFilters();
                renderStructures();
                if (revealAfterScan) {
                    revealAfterScan = false;
                    selectTab("slime");
                    findSlimes();
                }
            }
        };
        worker.execute();
    }

    private void rebuildFilters() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StructureScan.Found item : found) {
            counts.merge(item.id(), 1, Integer::sum);
        }
        String selected = filterBox.getSelectedItem() instanceof Filter filter ? filter.id() : null;
        filterBox.removeAllItems();
        filterBox.addItem(new Filter("常见遗迹", null));
        filterBox.addItem(new Filter("全部结构（" + found.size() + "）", "*"));
        counts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(24)
                .forEach(entry -> filterBox.addItem(
                        new Filter(StructureScan.label(entry.getKey()) + " ×" + entry.getValue(),
                                entry.getKey())));
        if (selected != null) {
            for (int i = 0; i < filterBox.getItemCount(); i++) {
                if (selected.equals(filterBox.getItemAt(i).id())) {
                    filterBox.setSelectedIndex(i);
                    return;
                }
            }
        }
        filterBox.setSelectedIndex(0);
    }

    private void renderStructures() {
        structureRows.removeAll();
        Filter filter = filterBox.getSelectedItem() instanceof Filter item ? item : new Filter("", null);
        Range range = rangeBox.getSelectedItem() instanceof Range item ? item : new Range("", 0);
        List<StructureScan.Found> shown = new ArrayList<>();
        for (StructureScan.Found item : found) {
            if (!matches(filter, item)) {
                continue;
            }
            if (range.value() > 0 && distance(item.x(), item.z()) > range.value()) {
                continue;
            }
            shown.add(item);
        }
        shown.sort(Comparator
                .comparingInt((StructureScan.Found item) -> dimensionOrder(item.dimension()))
                .thenComparingLong(item -> distance(item.x(), item.z())));
        int cap = Math.min(shown.size(), ROW_CAP);
        for (int i = 0; i < cap; i++) {
            StructureScan.Found item = shown.get(i);
            boolean home = item.dimension().equals("minecraft:overworld");
            String detail = item.dimensionLabel() + " · X " + item.x() + " Z " + item.z()
                    + (home ? " · " + distance(item.x(), item.z()) + " 格" : "");
            String command = "/tp @s " + item.x() + " " + (item.dimension().equals(
                    "minecraft:the_nether") ? 64 : 100) + " " + item.z();
            Row row = new Row(StructureScan.label(item.id()), detail, "复制",
                    "复制传送指令（Y 为估算值）");
            row.setAction(() -> copy(command), "指令已复制");
            structureRows.add(row);
            structureRows.add(Ui.vgap(3));
        }
        if (shown.isEmpty()) {
            structureRows.add(empty("这个范围里没有匹配的结构" + (found.isEmpty() ? "（先点扫描存档）" : "")));
        }
        scanStatus.setText(found.isEmpty()
                ? "没有扫描结果"
                : "共 " + shown.size() + " 个匹配"
                + (shown.size() > cap ? "，显示最近 " + cap + " 个" : "")
                + " · Y 由指令估算，落地前看清地形");
        structureRows.revalidate();
        structureRows.repaint();
        structureRows.fadeIn();
    }

    private static int dimensionOrder(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> 0;
            case "minecraft:the_nether" -> 1;
            case "minecraft:the_end" -> 2;
            default -> 3;
        };
    }

    private boolean matches(Filter filter, StructureScan.Found item) {
        if (filter.id() == null) {
            return StructureScan.common(item.id());
        }
        if ("*".equals(filter.id())) {
            return true;
        }
        return filter.id().equals(item.id());
    }

    void findSlimes() {
        if (world == null) {
            slimeStatus.setText("先选一个存档。");
            return;
        }
        if (!world.hasSeed()) {
            slimeStatus.setText("读不到种子，无法计算。");
            return;
        }
        Range range = slimeRange.getSelectedItem() instanceof Range item ? item : new Range("", 32);
        int centerX = spawnX >> 4;
        int centerZ = spawnZ >> 4;
        List<SlimeChunks.Spot> spots = SlimeChunks.nearest(world.seedOf("minecraft:overworld"),
                centerX, centerZ, range.value(), 24);
        slimeRows.removeAll();
        for (SlimeChunks.Spot spot : spots) {
            String detail = "区块 " + spot.chunkX() + ", " + spot.chunkZ()
                    + " · X " + spot.x() + " Z " + spot.z()
                    + " · " + distance(spot.x(), spot.z()) + " 格";
            String command = "/tp @s " + spot.x() + " 100 " + spot.z();
            Row row = new Row("史莱姆区块", detail, "复制", "复制传送指令（Y 为估算值）");
            row.setAction(() -> copy(command), "指令已复制");
            slimeRows.add(row);
            slimeRows.add(Ui.vgap(3));
        }
        if (spots.isEmpty()) {
            slimeRows.add(empty("这个半径内没有史莱姆区块，换个更大的半径试试"));
        }
        slimeStatus.setText("种子 " + world.seedOf("minecraft:overworld") + " · 出生点附近 "
                + spots.size() + " 个 · 坐标是区块中心");
        slimeRows.revalidate();
        slimeRows.repaint();
        slimeRows.fadeIn();
    }

    /** 截图模式：扫描完成后自动切到史莱姆子页。 */
    void prepareScreenshot() {
        revealAfterScan = true;
        scan();
    }

    private long distance(int x, int z) {
        long dx = x - spawnX;
        long dz = z - spawnZ;
        return Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    private static JLabel empty(String text) {
        JLabel label = Ui.label(text, Theme.TINY, Theme.DIM);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(6, 12, 6, 12));
        return label;
    }

    private void copy(String text) {
        if (text == null || text.isBlank() || "—".equals(text)) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        } catch (RuntimeException ignored) {
            // 无头/无剪贴板环境忽略。
        }
    }

    // ------------------------------------------------------------------ 行

    /** 圆角行：悬停渐变 + 复制反馈，点击整行也能复制。 */
    private final class Row extends JPanel {
        private final JLabel name;
        private JLabel detail;
        private final Ui.AccentButton button;
        private Runnable action;
        private String flashText = "已复制";
        private float glow;
        private Timer anim;

        Row(String title, String text, String buttonText, String tooltip) {
            super(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(6, 12, 6, 8));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            name = Ui.label(title, Theme.BODY, Theme.TEXT);
            name.setPreferredSize(new Dimension(150, 20));
            detail = Ui.label(text, Theme.MONO, Theme.DIM);
            button = Ui.smallButton(buttonText, Ui.AccentButton.Style.GHOST);
            button.setToolTipText(tooltip);
            add(name, BorderLayout.WEST);
            add(detail, BorderLayout.CENTER);
            add(button, BorderLayout.EAST);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover(true);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover(false);
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    run();
                }
            });
            button.addActionListener(e -> run());
        }

        void setDetail(JLabel label) {
            remove(detail);
            detail = label;
            add(detail, BorderLayout.CENTER);
            revalidate();
        }

        void setAction(Runnable action, String flash) {
            this.action = action;
            this.flashText = flash;
        }

        private void run() {
            if (action != null) {
                action.run();
                flash();
            }
        }

        private void flash() {
            String original = button.getText();
            button.setText(flashText);
            Timer timer = new Timer(1200, e -> button.setText(original));
            timer.setRepeats(false);
            timer.start();
        }

        private void hover(boolean inside) {
            if (anim == null) {
                anim = new Timer(14, e -> {
                    float target = getClientProperty("hover") == Boolean.TRUE ? 1f : 0f;
                    glow += (target - glow) * 0.28f;
                    if (Math.abs(target - glow) < 0.02f) {
                        glow = target;
                        ((Timer) e.getSource()).stop();
                    }
                    repaint();
                });
            }
            putClientProperty("hover", inside);
            anim.restart();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (glow > 0.01f) {
                Graphics2D g2 = Ui.prepare(g);
                g2.setColor(new java.awt.Color(255, 255, 255, Math.round(glow * 16)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new java.awt.Color(255, 255, 255, Math.round(glow * 34)));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
