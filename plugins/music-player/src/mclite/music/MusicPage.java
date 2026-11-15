package mclite.music;

import mclite.Theme;
import mclite.Ui;
import mclite.plugin.Host;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/** 音乐播放器页面。 */
final class MusicPage {

    private final Host host;
    private final Bili bili;
    private final Player player;

    private final JLabel account = Ui.label("", Theme.SMALL, Theme.MUTED);
    private final JLabel status = Ui.label("", Theme.TINY, Theme.DIM);
    private final JPanel results = column();
    private final JPanel queueRows = column();
    private final JTextField keyword = new JTextField();
    private final Ui.AccentButton playPause =
            Ui.smallButton("播放", Ui.AccentButton.Style.PRIMARY);
    private final JLabel nowPlaying = Ui.label("", Theme.SMALL, Theme.TEXT);
    private JPanel root;
    private String style = "follow";

    private String style() {
        if (style == null || style.isBlank()) {
            style = "follow";
        }
        return style;
    }

    private void saveStyle(String value) {
        style = value;
        try {
            java.nio.file.Files.writeString(host.pluginDataDir().resolve("style.txt"), value);
        } catch (Exception ignored) {
            // 存不了就只影响下次启动。
        }
    }

    private void loadStyle() {
        try {
            java.nio.file.Path file = host.pluginDataDir().resolve("style.txt");
            if (java.nio.file.Files.isRegularFile(file)) {
                style = java.nio.file.Files.readString(file).trim();
            }
        } catch (Exception ignored) {
            style = "follow";
        }
    }

    MusicPage(Host host, Bili bili, Player player) {
        this.host = host;
        this.bili = bili;
        this.player = player;
        player.listener(new Player.Listener() {
            @Override
            public void onTrack(Bili.Song song, int index) {
                SwingUtilities.invokeLater(() -> {
                    nowPlaying.setText("正在播放：" + song.title());
                    refreshQueue();
                    playPause.setText("暂停");
                });
            }

            @Override
            public void onState(boolean playing) {
                SwingUtilities.invokeLater(() -> playPause.setText(playing ? "暂停" : "播放"));
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> status.setText(message));
            }

            @Override
            public void onQueueChanged() {
                SwingUtilities.invokeLater(MusicPage.this::refreshQueue);
            }
        });
    }

    JPanel build() {
        loadStyle();
        if (!"follow".equals(style())) {
            mclite.ThemePreset preset = stylePreset(style());
            if (preset != null) {
                return withPalette(preset);
            }
        }
        return buildRoot();
    }

    private JPanel withPalette(mclite.ThemePreset preset) {
        java.awt.Color bg = Theme.BG;
        java.awt.Color card = Theme.CARD;
        java.awt.Color accent = Theme.ACCENT;
        java.awt.Color text = Theme.TEXT;
        try {
            Theme.BG = preset.background();
            Theme.CARD = preset.card();
            Theme.ACCENT = preset.accent();
            Theme.TEXT = preset.text();
            return buildRoot();
        } finally {
            Theme.BG = bg;
            Theme.CARD = card;
            Theme.ACCENT = accent;
            Theme.TEXT = text;
        }
    }

    private static mclite.ThemePreset stylePreset(String name) {
        for (mclite.ThemePreset preset : mclite.ThemePreset.ALL) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        return null;
    }

    private void rebuild() {
        if (root == null) {
            return;
        }
        JPanel next = build();
        root.removeAll();
        root.add(next, BorderLayout.CENTER);
        root.revalidate();
        root.repaint();
    }

    private JPanel buildRoot() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);
        page.add(header(), BorderLayout.NORTH);

        JPanel body = column();
        body.setBorder(new EmptyBorder(20, 26, 28, 26));
        body.add(accountCard());
        body.add(Ui.vgap(14));
        body.add(searchCard());
        body.add(Ui.vgap(14));
        body.add(queueCard());
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = Ui.scroll(body);
        page.add(scroll, BorderLayout.CENTER);
        root = page;
        refreshAccount();
        refreshQueue();
        return page;
    }

    private JComponent header() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Theme.CARD);
        header.setBorder(new EmptyBorder(9, 14, 9, 14));
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(Ui.label("音乐播放器", Theme.H1, Theme.TEXT));
        info.add(Ui.vgap(3));
        info.add(Ui.label("B 站音乐 · 队列播放 · 列表/单曲/随机/顺序循环", Theme.TINY, Theme.DIM));
        header.add(info, BorderLayout.WEST);
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        JLabel label = Ui.label("样式", Theme.TINY, Theme.DIM);
        right.add(label);
        right.add(Ui.hgap(6));
        javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>();
        combo.setFont(Theme.SMALL);
        combo.addItem("跟随启动器");
        for (mclite.ThemePreset preset : mclite.ThemePreset.ALL) {
            combo.addItem(preset.name());
        }
        combo.setSelectedItem("follow".equals(style()) ? "跟随启动器" : style());
        combo.addActionListener(e -> {
            String chosen = String.valueOf(combo.getSelectedItem());
            saveStyle("跟随启动器".equals(chosen) ? "follow" : chosen);
            SwingUtilities.invokeLater(this::rebuild);
        });
        right.add(combo);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent accountCard() {
        Ui.RoundedPanel card = card();
        card.add(Ui.label("账户", Theme.BODY_BOLD, Theme.TEXT));
        card.add(Ui.vgap(6));
        account.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(account);
        card.add(Ui.vgap(8));
        JPanel row = row();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        Ui.AccentButton login = Ui.smallButton("扫码登录", Ui.AccentButton.Style.PRIMARY);
        login.addActionListener(e -> login());
        Ui.AccentButton sync = Ui.smallButton("从游戏实例同步", Ui.AccentButton.Style.SECONDARY);
        sync.addActionListener(e -> {
            bili.syncFromInstance(host.instanceDir());
            refreshAccount();
            status.setText(bili.loggedIn() ? "已从游戏实例同步登录。" : "游戏实例里没有找到 mclite-island 登录。");
        });
        row.add(login);
        row.add(Ui.hgap(8));
        row.add(sync);
        card.add(row);
        return card;
    }

    private JComponent searchCard() {
        Ui.RoundedPanel card = card();
        card.add(Ui.label("搜索", Theme.BODY_BOLD, Theme.TEXT));
        card.add(Ui.vgap(8));
        JPanel row = row();
        keyword.setPreferredSize(new Dimension(360, 28));
        keyword.setMaximumSize(new Dimension(360, 28));
        keyword.setFont(Theme.BODY);
        keyword.setForeground(Theme.TEXT);
        keyword.setBackground(Theme.CARD_ALT);
        keyword.setBorder(new EmptyBorder(4, 10, 4, 10));
        keyword.addActionListener(e -> search());
        row.add(keyword);
        row.add(Ui.hgap(8));
        Ui.AccentButton find = Ui.smallButton("搜索", Ui.AccentButton.Style.PRIMARY);
        find.addActionListener(e -> search());
        row.add(find);
        card.add(row);
        card.add(Ui.vgap(8));
        card.add(results);
        return card;
    }

    private JComponent queueCard() {
        Ui.RoundedPanel card = card();
        card.add(Ui.label("播放队列", Theme.BODY_BOLD, Theme.TEXT));
        card.add(Ui.vgap(6));
        nowPlaying.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(nowPlaying);
        card.add(Ui.vgap(8));

        JPanel controls = row();
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        Ui.AccentButton prev = Ui.smallButton("上一首", Ui.AccentButton.Style.GHOST);
        prev.addActionListener(e -> player.previous());
        playPause.addActionListener(e -> player.toggle());
        Ui.AccentButton next = Ui.smallButton("下一首", Ui.AccentButton.Style.GHOST);
        next.addActionListener(e -> player.next(true));
        Ui.AccentButton stop = Ui.smallButton("停止", Ui.AccentButton.Style.GHOST);
        stop.addActionListener(e -> player.stop());
        controls.add(prev);
        controls.add(Ui.hgap(8));
        controls.add(playPause);
        controls.add(Ui.hgap(8));
        controls.add(next);
        controls.add(Ui.hgap(8));
        controls.add(stop);
        card.add(controls);
        card.add(Ui.vgap(8));

        JPanel modes = row();
        modes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (Player.Mode mode : Player.Mode.values()) {
            Ui.SegmentButton chip = new Ui.SegmentButton(mode.label);
            chip.setSelected(player.mode() == mode);
            chip.addActionListener(e -> {
                player.mode(mode);
                status.setText("循环模式：" + mode.label);
            });
            group.add(chip);
            modes.add(chip);
            modes.add(Ui.hgap(6));
        }
        card.add(modes);
        card.add(Ui.vgap(8));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(status);
        card.add(Ui.vgap(8));
        card.add(queueRows);
        return card;
    }

    private void login() {
        status.setText("正在获取二维码…");
        new javax.swing.SwingWorker<Bili.Qr, Void>() {
            @Override
            protected Bili.Qr doInBackground() throws Exception {
                return bili.qrGenerate();
            }

            @Override
            protected void done() {
                try {
                    Bili.Qr qr = get();
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(qr.url()), null);
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(qr.url()));
                    }
                    status.setText("登录链接已复制并打开浏览器；扫码/确认后会自动完成。");
                    poll(qr);
                } catch (Exception ex) {
                    status.setText("登录初始化失败：" + ex.getMessage());
                }
            }
        }.execute();
    }

    private void poll(Bili.Qr qr) {
        new javax.swing.SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<String> cookies = new ArrayList<>();
                for (int i = 0; i < 90; i++) {
                    int code = bili.qrPoll(qr, cookies);
                    if (code == 0) {
                        return null;
                    }
                    if (code == 86038) {
                        throw new IllegalStateException("二维码已过期");
                    }
                    Thread.sleep(2000);
                }
                throw new IllegalStateException("等待登录超时");
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshAccount();
                    status.setText("登录成功。");
                } catch (Exception ex) {
                    status.setText("登录未完成：" + ex.getMessage());
                }
            }
        }.execute();
    }

    private void search() {
        String text = keyword.getText() == null ? "" : keyword.getText().trim();
        if (text.isEmpty()) {
            status.setText("先输入关键词。");
            return;
        }
        status.setText("搜索中…");
        new javax.swing.SwingWorker<List<Bili.Video>, Void>() {
            @Override
            protected List<Bili.Video> doInBackground() throws Exception {
                return bili.search(text, 1);
            }

            @Override
            protected void done() {
                results.removeAll();
                try {
                    List<Bili.Video> songs = get();
                    if (songs.isEmpty()) {
                        status.setText("没有结果。");
                    } else {
                        for (Bili.Video video : songs) {
                            results.add(resultRow(video));
                            results.add(Ui.vgap(3));
                        }
                        status.setText("找到 " + songs.size() + " 个结果。");
                    }
                } catch (Exception ex) {
                    status.setText("搜索失败：" + ex.getMessage());
                }
                results.revalidate();
                results.repaint();
            }
        }.execute();
    }

    private JComponent resultRow(Bili.Video video) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.add(Ui.label(video.title(), Theme.SMALL, Theme.TEXT), BorderLayout.CENTER);
        row.add(Ui.label(video.author() + " · " + mmss(video.seconds()),
                Theme.TINY, Theme.DIM), BorderLayout.WEST);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        Ui.AccentButton add = Ui.smallButton("加入队列", Ui.AccentButton.Style.GHOST);
        add.addActionListener(e -> enqueue(video, false));
        Ui.AccentButton play = Ui.smallButton("播放", Ui.AccentButton.Style.SECONDARY);
        play.addActionListener(e -> enqueue(video, true));
        actions.add(add);
        actions.add(Ui.hgap(6));
        actions.add(play);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private void enqueue(Bili.Video video, boolean playNow) {
        status.setText("解析播放地址…");
        new javax.swing.SwingWorker<Bili.Song, Void>() {
            @Override
            protected Bili.Song doInBackground() throws Exception {
                return bili.resolve(video);
            }

            @Override
            protected void done() {
                try {
                    Bili.Song song = get();
                    int index = player.queue().size();
                    player.enqueue(List.of(song));
                    if (playNow) {
                        player.play(index);
                    }
                    status.setText(playNow ? "开始播放。" : "已加入队列。");
                } catch (Exception ex) {
                    status.setText("解析失败：" + ex.getMessage());
                }
            }
        }.execute();
    }

    private void refreshQueue() {
        queueRows.removeAll();
        List<Bili.Song> songs = player.queue();
        for (int i = 0; i < songs.size(); i++) {
            Bili.Song song = songs.get(i);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            JLabel title = Ui.label((i + 1) + ". " + song.title(),
                    Theme.SMALL, i == player.index() ? Theme.ACCENT : Theme.TEXT);
            row.add(title, BorderLayout.CENTER);
            Ui.AccentButton play = Ui.smallButton("播放", Ui.AccentButton.Style.GHOST);
            final int index = i;
            play.addActionListener(e -> player.play(index));
            row.add(play, BorderLayout.EAST);
            queueRows.add(row);
            queueRows.add(Ui.vgap(2));
        }
        if (songs.isEmpty()) {
            queueRows.add(Ui.label("队列是空的：搜一首歌加入吧。", Theme.TINY, Theme.DIM));
        }
        queueRows.revalidate();
        queueRows.repaint();
    }

    private void refreshAccount() {
        Bili.Profile profile = bili.profile();
        account.setText(profile.login()
                ? "已登录：" + profile.name()
                : "未登录（可以扫码，或从游戏实例同步）");
        account.setForeground(profile.login() ? Theme.TEXT : Theme.MUTED);
    }

    private static String mmss(long seconds) {
        if (seconds <= 0) {
            return "--:--";
        }
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static Ui.RoundedPanel card() {
        Ui.RoundedPanel panel = new Ui.RoundedPanel(Theme.CARD, Theme.BORDER, 14);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(14, 16, 16, 16));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
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
