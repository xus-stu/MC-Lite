package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AccountsDialog extends JDialog {
    private final Config config;
    private final DefaultListModel<AccountStore.Account> model = new DefaultListModel<>();
    private final JList<AccountStore.Account> list = new JList<>(model);
    private final JTextField nameField;
    private final JLabel dirLabel = new JLabel();
    private final Ui.SkinHead skinPreview = new Ui.SkinHead(64);
    private final JLabel skinStatus = new JLabel();
    private String selectedId;

    public AccountsDialog(Frame owner, Config config) {
        super(owner, "登录 / 账户", true);
        this.config = config;
        AccountStore.Data data = AccountStore.load(config.accountsDir());
        selectedId = config.accountId();
        for (AccountStore.Account account : data.accounts()) {
            model.addElement(account);
        }
        if (selectedId == null || selectedId.isBlank()) {
            selectedId = data.selectedId();
        }

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(20, 20, 18, 20));
        setContentPane(root);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = Ui.label("登录 / 账户", Theme.TITLE, Theme.TEXT);
        JLabel sub = Ui.label("创建或切换到离线账户；账户文件保存在下方目录。", Theme.SMALL, Theme.MUTED);
        header.add(title);
        header.add(Ui.vgap(3));
        header.add(sub);
        root.add(header, BorderLayout.NORTH);

        list.setBackground(Theme.CARD);
        list.setSelectionBackground(Theme.SELECTED);
        list.setSelectionForeground(Theme.TEXT);
        list.setFixedCellHeight(34);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((jlist, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel("  " + value.name() + "    ·    离线账户");
            label.setOpaque(true);
            label.setFont(Theme.BODY);
            label.setBackground(isSelected ? Theme.SELECTED : Theme.CARD);
            label.setForeground(isSelected ? Theme.TEXT : Theme.MUTED);
            label.setBorder(new EmptyBorder(4, 8, 4, 8));
            return label;
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.CARD);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSkinPreview();
            }
        });
        JPanel accountArea = new JPanel(new BorderLayout(14, 0));
        accountArea.setOpaque(false);
        accountArea.add(scroll, BorderLayout.CENTER);
        JPanel skinPanel = new JPanel();
        skinPanel.setOpaque(false);
        skinPanel.setLayout(new BoxLayout(skinPanel, BoxLayout.Y_AXIS));
        skinPanel.setBorder(new EmptyBorder(0, 4, 0, 0));
        skinPreview.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        skinPanel.add(skinPreview);
        skinPanel.add(Box.createVerticalStrut(6));
        skinStatus.setFont(Theme.TINY);
        skinStatus.setForeground(Theme.DIM);
        skinStatus.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        skinPanel.add(skinStatus);
        skinPanel.add(Box.createVerticalStrut(6));
        Ui.AccentButton chooseSkin = Ui.smallButton("换上皮肤…", Ui.AccentButton.Style.SECONDARY);
        chooseSkin.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        chooseSkin.addActionListener(e -> chooseSkin());
        skinPanel.add(chooseSkin);
        skinPanel.add(Box.createVerticalStrut(5));
        Ui.AccentButton removeSkin = Ui.smallButton("移除皮肤", Ui.AccentButton.Style.GHOST);
        removeSkin.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        removeSkin.addActionListener(e -> removeSkin());
        skinPanel.add(removeSkin);
        skinPanel.setPreferredSize(new Dimension(130, 0));
        accountArea.add(skinPanel, BorderLayout.EAST);
        root.add(accountArea, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel createRow = new JPanel(new BorderLayout(8, 0));
        createRow.setOpaque(false);
        nameField = Ui.field("输入新的离线用户名");
        nameField.setPreferredSize(new Dimension(240, 32));
        nameField.addActionListener(e -> createAccount());
        Ui.AccentButton create = Ui.smallButton("创建离线账户", Ui.AccentButton.Style.SECONDARY);
        create.addActionListener(e -> createAccount());
        createRow.add(nameField, BorderLayout.CENTER);
        createRow.add(create, BorderLayout.EAST);
        createRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        bottom.add(createRow);
        bottom.add(Ui.vgap(10));

        JPanel dirRow = new JPanel(new BorderLayout(8, 0));
        dirRow.setOpaque(false);
        dirLabel.setFont(Theme.MONO);
        dirLabel.setForeground(Theme.DIM);
        dirLabel.setText(shorten(config.accountsDir().toString()));
        dirLabel.setToolTipText(config.accountsDir().toString());
        dirRow.add(dirLabel, BorderLayout.CENTER);
        Ui.AccentButton chooseDir = Ui.smallButton("修改保存目录", Ui.AccentButton.Style.GHOST);
        chooseDir.addActionListener(e -> chooseDirectory());
        dirRow.add(chooseDir, BorderLayout.EAST);
        bottom.add(dirRow);
        bottom.add(Ui.vgap(12));

        JPanel actions = new JPanel(new GridLayout(1, 3, 8, 0));
        actions.setOpaque(false);
        Ui.AccentButton use = Ui.smallButton("使用所选账户", Ui.AccentButton.Style.PRIMARY);
        use.addActionListener(e -> useSelected());
        Ui.AccentButton delete = Ui.smallButton("删除", Ui.AccentButton.Style.DANGER);
        delete.addActionListener(e -> deleteSelected());
        Ui.AccentButton close = Ui.smallButton("关闭", Ui.AccentButton.Style.GHOST);
        close.addActionListener(e -> dispose());
        actions.add(use);
        actions.add(delete);
        actions.add(close);
        bottom.add(actions);
        bottom.add(Ui.vgap(8));
        JLabel skinNote = new JLabel("<html><body style='width:500px'>皮肤文件会保存到账户目录；"
                + "同时写入游戏目录的 skin.png 和 CustomSkinLoader/LocalSkin。"
                + "原版离线模式本身不读取自定义皮肤，需要安装皮肤模组。</body></html>");
        skinNote.setFont(Theme.TINY);
        skinNote.setForeground(Theme.DIM);
        bottom.add(skinNote);
        root.add(bottom, BorderLayout.SOUTH);

        selectCurrent();
        setSize(600, 540);
        setLocationRelativeTo(owner);
    }

    private void selectCurrent() {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(selectedId)) {
                list.setSelectedIndex(i);
                return;
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        updateSkinPreview();
    }

    private void updateSkinPreview() {
        AccountStore.Account account = list.getSelectedValue();
        if (account == null) {
            skinPreview.setSkin(null);
            skinStatus.setText("未选择账户");
            return;
        }
        java.awt.Image skin = SkinStore.load(config, account.id());
        skinPreview.setSkin(skin);
        skinStatus.setText(skin == null ? "默认皮肤" : "自定义皮肤");
    }

    private void chooseSkin() {
        AccountStore.Account account = list.getSelectedValue();
        if (account == null) {
            return;
        }
        Path selected = Ui.chooseFile(this, "选择 64×64 / 64×32 的 PNG 皮肤",
                config.accountsDir(), "png");
        if (selected == null) {
            return;
        }
        try {
            SkinStore.save(config, account.id(), selected);
            updateSkinPreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "皮肤无效",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSkin() {
        AccountStore.Account account = list.getSelectedValue();
        if (account == null) {
            return;
        }
        SkinStore.remove(config, account.id());
        updateSkinPreview();
    }

    private void createAccount() {
        String name = nameField.getText().trim();
        if (!Config.isValidUsername(name)) {
            JOptionPane.showMessageDialog(this, "用户名必须为 3-16 位字母、数字或下划线。",
                    "用户名无效", JOptionPane.ERROR_MESSAGE);
            return;
        }
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).name().equalsIgnoreCase(name)) {
                JOptionPane.showMessageDialog(this, "已经存在同名账户。", "重复账户",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        AccountStore.Account account = AccountStore.create(name);
        model.addElement(account);
        selectedId = account.id();
        list.setSelectedIndex(model.size() - 1);
        nameField.setText("");
        persist();
    }

    private void useSelected() {
        AccountStore.Account account = list.getSelectedValue();
        if (account == null) {
            return;
        }
        selectedId = account.id();
        config.accountId(account.id());
        config.username(account.name());
        config.save();
        persist();
        dispose();
    }

    private void deleteSelected() {
        AccountStore.Account account = list.getSelectedValue();
        if (account == null) {
            return;
        }
        if (model.size() <= 1) {
            JOptionPane.showMessageDialog(this, "至少保留一个账户。", "无法删除",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "删除账户 “" + account.name() + "”？",
                "删除账户", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        model.removeElement(account);
        selectedId = model.isEmpty() ? "" : model.get(0).id();
        selectCurrent();
        persist();
    }

    private void chooseDirectory() {
        Path dir = Ui.chooseDirectory(this, "选择账户保存目录", config.accountsDir());
        if (dir == null) {
            return;
        }
        config.accountsDir(dir);
        config.save();
        AccountStore.Data data = AccountStore.load(dir);
        model.clear();
        for (AccountStore.Account account : data.accounts()) {
            model.addElement(account);
        }
        selectedId = data.selectedId();
        selectCurrent();
        dirLabel.setText(shorten(dir.toString()));
        dirLabel.setToolTipText(dir.toString());
        persist();
    }

    private void persist() {
        List<AccountStore.Account> accounts = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            accounts.add(model.get(i));
        }
        AccountStore.save(config.accountsDir(), new AccountStore.Data(accounts, selectedId));
    }

    private static String shorten(String text) {
        return text.length() <= 58 ? text : "…" + text.substring(text.length() - 57);
    }
}
