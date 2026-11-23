package mclite.editor;

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
import java.nio.file.Path;
import java.util.List;

/** 修改工具页面：游戏模式 / 极限 / 允许命令 / 时间。 */
final class EditorPage {

    private static final String[] MODES = {"生存", "创造", "冒险", "旁观"};
    private static final int[] MODE_IDS = {LevelFile.SURVIVAL, LevelFile.CREATIVE,
            LevelFile.ADVENTURE, LevelFile.SPECTATOR};

    private final Host host;
    private final JComboBox<LevelFile.World> worldBox = new Ui.DarkCombo<>();
    private final JLabel worldLine = Ui.label("", Theme.TINY, Theme.DIM);
    private final JComboBox<String> modeBox = new Ui.DarkCombo<>();
    private final Ui.ToggleChip hardcoreChip = new Ui.ToggleChip("极限模式");
    private final Ui.ToggleChip commandChip = new Ui.ToggleChip("允许命令");
    private final JComboBox<String> timeBox = new Ui.DarkCombo<>();
    private final JLabel status = Ui.label("", Theme.TINY, Theme.DIM);
    private LevelFile.World world;

    EditorPage(Host host) {
        this.host = host;
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
        JPanel worldRow = row();
        worldRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        worldBox.setPreferredSize(new Dimension(380, 30));
        worldBox.setMaximumSize(new Dimension(380, 30));
        worldBox.addActionListener(e -> selectWorld());
        worldRow.add(worldBox);
        worldRow.add(Ui.hgap(10));
        Ui.AccentButton refresh = Ui.smallButton("刷新", Ui.AccentButton.Style.GHOST);
        refresh.addActionListener(e -> reload());
        worldRow.add(refresh);
        worldRow.add(Box.createHorizontalGlue());
        worldCard.add(worldRow);
        worldCard.add(Ui.vgap(8));
        worldLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        worldCard.add(worldLine);
        body.add(worldCard);
        body.add(Ui.vgap(12));

        Ui.RoundedPanel edit = card();
        edit.add(Ui.label("修改", Theme.BODY_BOLD, Theme.TEXT));
        edit.add(Ui.vgap(8));

        edit.add(labelRow("游戏模式", modeBox, MODES));
        edit.add(Ui.vgap(6));
        JPanel chips = row();
        chips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        chips.add(hardcoreChip);
        chips.add(Ui.hgap(8));
        chips.add(commandChip);
        chips.add(Box.createHorizontalGlue());
        edit.add(chips);
        edit.add(Ui.vgap(6));
        edit.add(labelRow("时间", timeBox, new String[]{"不变", "白天", "正午", "夜晚", "午夜"}));
        edit.add(Ui.vgap(12));

        JPanel actions = row();
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        Ui.AccentButton save = Ui.smallButton("保存修改", Ui.AccentButton.Style.PRIMARY);
        save.addActionListener(e -> save());
        actions.add(save);
        actions.add(Box.createHorizontalGlue());
        edit.add(actions);
        edit.add(Ui.vgap(8));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        edit.add(status);
        edit.add(Ui.vgap(8));
        edit.add(note("规则：极限存档不能改回普通生存、也不能关闭极限；普通生存可以升级为极限。"));
        edit.add(note("保存前会自动把 level.dat 备份成 level.dat.bak-<时间戳>；改完请重新进存档。"));
        edit.add(note("只能在游戏关闭时改；存档正在被游戏占用时不要保存。"));
        body.add(edit);
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
        info.add(Ui.label("修改工具", Theme.H1, Theme.TEXT));
        info.add(Ui.vgap(3));
        info.add(Ui.label("改存档游戏模式 / 极限 / 允许命令 / 时间（自动备份）", Theme.TINY, Theme.DIM));
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
        for (LevelFile.World item : LevelFile.worlds(host.instanceDir())) {
            worldBox.addItem(item);
        }
        if (worldBox.getItemCount() == 0) {
            worldLine.setText("这个实例还没有存档。");
            return;
        }
        worldBox.setSelectedIndex(0);
        selectWorld();
    }

    private void selectWorld() {
        if (!(worldBox.getSelectedItem() instanceof LevelFile.World selected)) {
            return;
        }
        world = selected;
        worldLine.setText(LevelFile.modeLine(selected));
        modeBox.setSelectedItem(LevelFile.modeName(selected.gameType()));
        hardcoreChip.setSelected(selected.hardcore());
        hardcoreChip.setToolTipText(selected.hardcore()
                ? "极限存档：不能关闭极限、也不能改回普通生存" : "可以升级为极限模式（会变成生存）");
        commandChip.setSelected(selected.allowCommands());
        timeBox.setSelectedItem("不变");
        status.setText("已读取 " + selected.name());
    }

    private void save() {
        if (world == null) {
            status.setText("先选一个存档。");
            return;
        }
        int modeIndex = Math.max(0, modeBox.getSelectedIndex());
        int gameType = MODE_IDS[Math.min(modeIndex, MODE_IDS.length - 1)];
        boolean hardcore = hardcoreChip.isSelected();
        boolean allowCommands = commandChip.isSelected();
        Long time = LevelFile.timePreset(String.valueOf(timeBox.getSelectedItem()));
        try {
            LevelFile.Result result = LevelFile.save(world, gameType, hardcore, allowCommands, time);
            if (result.changes().isEmpty()) {
                status.setText("没有需要改的项。");
                return;
            }
            status.setText("已保存：" + String.join("、", result.changes())
                    + "；备份 " + (result.backup() == null ? "无" : result.backup().getFileName()));
            reload();
        } catch (Exception ex) {
            status.setText("保存失败：" + ex.getMessage());
        }
    }

    private JComponent labelRow(String label, JComboBox<String> box, String[] items) {
        JPanel row = row();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel name = Ui.label(label, Theme.SMALL, Theme.DIM);
        name.setPreferredSize(new Dimension(90, 20));
        row.add(name);
        box.setPreferredSize(new Dimension(180, 30));
        box.setMaximumSize(new Dimension(180, 30));
        if (box.getItemCount() == 0) {
            for (String item : items) {
                box.addItem(item);
            }
        }
        row.add(box);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private JLabel note(String text) {
        JLabel label = Ui.label("· " + text, Theme.TINY, Theme.DIM);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(1, 0, 1, 0));
        return label;
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
