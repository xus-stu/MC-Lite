package mclite.terrain;

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
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 地形查询页面：扫描已生成区块的生物群系，重点找沼泽。 */
final class TerrainPage {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("swamp", "沼泽"), Map.entry("mangrove_swamp", "红树林沼泽"),
            Map.entry("mushroom_fields", "蘑菇岛"), Map.entry("plains", "平原"),
            Map.entry("sunflower_plains", "向日葵平原"), Map.entry("forest", "森林"),
            Map.entry("flower_forest", "繁花森林"), Map.entry("birch_forest", "白桦森林"),
            Map.entry("dark_forest", "黑森林"), Map.entry("taiga", "针叶林"),
            Map.entry("snowy_taiga", "雪原针叶林"), Map.entry("old_growth_pine_taiga", "原始松木针叶林"),
            Map.entry("old_growth_spruce_taiga", "原始云杉针叶林"), Map.entry("jungle", "丛林"),
            Map.entry("sparse_jungle", "稀疏丛林"), Map.entry("bamboo_jungle", "竹林"),
            Map.entry("desert", "沙漠"), Map.entry("savanna", "热带草原"),
            Map.entry("savanna_plateau", "热带草原高原"), Map.entry("windswept_savanna", "风袭热带草原"),
            Map.entry("badlands", "恶地"), Map.entry("eroded_badlands", "风蚀恶地"),
            Map.entry("wooded_badlands", "疏林恶地"), Map.entry("beach", "沙滩"),
            Map.entry("snowy_beach", "雪沙滩"), Map.entry("stony_shore", "石岸"),
            Map.entry("ocean", "海洋"), Map.entry("deep_ocean", "深海"),
            Map.entry("warm_ocean", "暖水海洋"), Map.entry("lukewarm_ocean", "温水海洋"),
            Map.entry("cold_ocean", "冷水海洋"), Map.entry("deep_cold_ocean", "冷深海"),
            Map.entry("frozen_ocean", "冻洋"), Map.entry("deep_frozen_ocean", "冻深海"),
            Map.entry("frozen_river", "冻河"), Map.entry("river", "河流"),
            Map.entry("snowy_plains", "雪原"), Map.entry("ice_spikes", "冰刺平原"),
            Map.entry("snowy_slopes", "积雪山坡"), Map.entry("jagged_peaks", "尖峭山峰"),
            Map.entry("frozen_peaks", "冰封山峰"), Map.entry("stony_peaks", "裸岩山峰"),
            Map.entry("meadow", "草甸"), Map.entry("grove", "雪林"),
            Map.entry("cherry_grove", "樱花树林"), Map.entry("pale_garden", "苍白之园"),
            Map.entry("windswept_hills", "风袭丘陵"), Map.entry("windswept_gravelly_hills", "风袭沙砾丘陵"),
            Map.entry("windswept_forest", "风袭森林"), Map.entry("dripstone_caves", "溶洞"),
            Map.entry("lush_caves", "繁茂洞穴"), Map.entry("deep_dark", "深暗之域"),
            Map.entry("nether_wastes", "下界荒原"), Map.entry("soul_sand_valley", "灵魂沙峡谷"),
            Map.entry("crimson_forest", "绯红森林"), Map.entry("warped_forest", "诡异森林"),
            Map.entry("basalt_deltas", "玄武岩三角洲"), Map.entry("the_end", "末地"),
            Map.entry("end_highlands", "末地高地"), Map.entry("end_midlands", "末地内陆"),
            Map.entry("small_end_islands", "末地小岛"), Map.entry("end_barrens", "末地荒岛"));

    private final Host host;
    private final JComboBox<Scan.World> worldBox = new Ui.DarkCombo<>();
    private final JPanel rows = new JPanel();
    private final JLabel status = Ui.label("", Theme.TINY, Theme.DIM);
    private final JLabel seedLine = Ui.label("", Theme.SMALL, Theme.MUTED);
    private final JLabel swampLine = Ui.label("", Theme.SMALL, Theme.TEXT);
    private final Ui.ThinProgress progress = new Ui.ThinProgress();
    private final Ui.AccentButton scanButton =
            Ui.smallButton("扫描已生成地形", Ui.AccentButton.Style.PRIMARY);
    private String loadedInstance = "";
    private Map<String, String> modNames = Map.of();

    TerrainPage(Host host) {
        this.host = host;
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
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
        worldBox.setPreferredSize(new Dimension(380, 30));
        worldBox.setMaximumSize(new Dimension(380, 30));
        worldBox.addActionListener(e -> showWorldInfo());
        row.add(worldBox);
        row.add(Ui.hgap(10));
        scanButton.addActionListener(e -> scan());
        row.add(scanButton);
        row.add(Box.createHorizontalGlue());
        worldCard.add(row);
        worldCard.add(Ui.vgap(8));
        seedLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        worldCard.add(seedLine);
        body.add(worldCard);
        body.add(Ui.vgap(12));

        Ui.RoundedPanel resultCard = card();
        resultCard.add(Ui.label("结果", Theme.BODY_BOLD, Theme.TEXT));
        resultCard.add(Ui.vgap(6));
        swampLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultCard.add(swampLine);
        resultCard.add(Ui.vgap(6));
        progress.setVisible(false);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        resultCard.add(progress);
        resultCard.add(Ui.vgap(6));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultCard.add(status);
        resultCard.add(Ui.vgap(8));
        resultCard.add(rows);
        body.add(resultCard);
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = Ui.scroll(body);
        page.add(scroll, BorderLayout.CENTER);
        reloadWorlds();
        return page;
    }

    private JComponent header() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Theme.BG);
        header.setBorder(new EmptyBorder(9, 14, 9, 14));
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(Ui.label("地形查询", Theme.H1, Theme.TEXT));
        info.add(Ui.vgap(3));
        info.add(Ui.label("扫描已生成区块的生物群系：找沼泽、红树林沼泽、蘑菇岛等", Theme.TINY, Theme.DIM));
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

    private void reloadWorlds() {
        String instance = String.valueOf(host.instanceDir());
        if (instance.equals(loadedInstance) && worldBox.getItemCount() > 0) {
            showWorldInfo();
            return;
        }
        loadedInstance = instance;
        worldBox.removeAllItems();
        for (Scan.World world : Scan.worlds(host.instanceDir())) {
            worldBox.addItem(world);
        }
        if (worldBox.getItemCount() == 0) {
            seedLine.setText("这个实例还没有存档。");
            return;
        }
        worldBox.setSelectedIndex(0);
        showWorldInfo();
    }

    private void showWorldInfo() {
        if (!(worldBox.getSelectedItem() instanceof Scan.World world)) {
            return;
        }
        seedLine.setText("种子 " + (world.seed() == 0 ? "未知" : String.valueOf(world.seed()))
                + " · 出生点 X " + world.spawnX() + " Z " + world.spawnZ());
        swampLine.setText("还没扫描：点「扫描已生成地形」。");
        rows.removeAll();
        rows.revalidate();
        rows.repaint();
    }

    private void scan() {
        if (!(worldBox.getSelectedItem() instanceof Scan.World world)) {
            return;
        }
        scanButton.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        status.setText("正在读取区块…");
        new javax.swing.SwingWorker<List<Scan.BiomeHit>, Integer>() {
            @Override
            protected List<Scan.BiomeHit> doInBackground() {
                // 模组会改地形/加群系：先从 mods/*.jar 的语言文件收集中文名。
                modNames = ModNames.load(host.instanceDir());
                return Scan.biomes(world, new Scan.Listener() {
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
                if (chunks.isEmpty()) {
                    return;
                }
                progress.setIndeterminate(false);
                progress.setValue(chunks.get(chunks.size() - 1) / 100.0);
                status.setText("扫描中 " + chunks.get(chunks.size() - 1) + "%");
            }

            @Override
            protected void done() {
                scanButton.setEnabled(true);
                progress.setVisible(false);
                try {
                    render(world, get());
                } catch (Exception ex) {
                    status.setText("扫描失败：" + ex.getMessage());
                }
            }
        }.execute();
    }

    private void render(Scan.World world, List<Scan.BiomeHit> hits) {
        rows.removeAll();
        Scan.BiomeHit swamp = null;
        for (Scan.BiomeHit hit : hits) {
            if (isSwamp(hit.id()) && (swamp == null || hit.distance() < swamp.distance())) {
                swamp = hit;
            }
        }
        if (swamp == null) {
            swampLine.setForeground(Theme.DIM);
            swampLine.setText("已生成的 " + hits.size() + " 种地形里没有沼泽："
                    + "去没探索过的方向，或放弃已生成区域（本插件只能查已生成的区块）。");
        } else {
            swampLine.setForeground(Theme.ACCENT);
            swampLine.setText("★ 最近沼泽：X " + swamp.x() + " Z " + swamp.z()
                    + " · 距离出生点 " + swamp.distance() + " 格");
        }
        int count = 0;
        for (Scan.BiomeHit hit : hits) {
            if (count++ >= 60) {
                break;
            }
            rows.add(biomeRow(hit));
            rows.add(Ui.vgap(3));
        }
        if (hits.isEmpty()) {
            rows.add(Ui.label("没有扫到生物群系数据（可能是新存档还没生成区块）。", Theme.TINY, Theme.DIM));
        }
        status.setText("共 " + hits.size() + " 种地形（其中 " + moddedCount(hits) + " 种来自模组），"
                + "按已生成区块覆盖数量排序；名字取自 mods 语言文件 " + modNames.size() + " 条");
        rows.revalidate();
        rows.repaint();
    }

    private boolean isSwamp(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (String part : new String[]{"swamp", "marsh", "bog", "bayou", "mangrove", "moor", "wetland"}) {
            if (key.contains(part)) {
                return true;
            }
        }
        String zh = name(id);
        return zh.contains("沼泽") || zh.contains("湿地") || zh.contains("红树") || zh.contains("泽地");
    }

    private int moddedCount(List<Scan.BiomeHit> hits) {
        int count = 0;
        for (Scan.BiomeHit hit : hits) {
            if (hit.id().contains(":") && !hit.id().startsWith("minecraft:")) {
                count++;
            }
        }
        return count;
    }

    private JComponent biomeRow(Scan.BiomeHit hit) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel name = Ui.label((isSwamp(hit.id()) ? "★ " : "") + name(hit.id()),
                Theme.SMALL, isSwamp(hit.id()) ? Theme.ACCENT : Theme.TEXT);
        name.setPreferredSize(new Dimension(180, 20));
        row.add(name, BorderLayout.WEST);
        row.add(Ui.label(hit.chunks() + " 个区块 · 最近 X " + hit.x() + " Z " + hit.z()
                + " · " + hit.distance() + " 格", Theme.TINY, Theme.DIM), BorderLayout.CENTER);
        Ui.AccentButton copy = Ui.smallButton("复制", Ui.AccentButton.Style.GHOST);
        copy.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection("/tp @s " + hit.x() + " 100 " + hit.z()), null);
                status.setText("已复制传送指令。");
            } catch (RuntimeException ignored) {
                // 无剪贴板环境。
            }
        });
        row.add(copy, BorderLayout.EAST);
        return row;
    }

    private String name(String id) {
        String mod = modNames.get(id);
        if (mod != null && !mod.isBlank()) {
            return mod;
        }
        String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        String vanilla = NAMES.get(path);
        if (vanilla != null) {
            return vanilla;
        }
        String pretty = path.replace('_', ' ');
        if (id.contains(":") && !id.startsWith("minecraft:")) {
            return pretty + "（" + id.substring(0, id.indexOf(':')) + "）";
        }
        return pretty;
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
