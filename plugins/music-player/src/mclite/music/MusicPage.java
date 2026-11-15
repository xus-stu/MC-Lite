package mclite.music;

import mclite.Theme;
import mclite.ThemePreset;
import mclite.Ui;
import mclite.plugin.Host;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 音乐播放器：搜索 / 播放列表 / 设置 三个子页 + 常驻播放条。 */
final class MusicPage {

    private final Host host;
    private final Bili bili;
    private final Player player;
    private final Path cacheDir;

    private final JLabel account = Ui.label("", Theme.SMALL, Theme.MUTED);
    private final JLabel status = Ui.label("", Theme.TINY, Theme.DIM);
    private final JLabel nowPlaying = Ui.label("未播放", Theme.SMALL, Theme.TEXT);
    private final JPanel results = column();
    private final JPanel queueRows = column();
    private final JTextField keyword = new JTextField();
    private final Ui.AccentButton playPause =
            Ui.smallButton("播放", Ui.AccentButton.Style.PRIMARY);
    private final JLabel reverbLabel = Ui.label("", Theme.TINY, Theme.DIM);

    private JPanel root;
    private CardLayout cardsLayout;
    private JPanel cards;
    private Ui.SegmentButton searchTab;
    private Ui.SegmentButton queueTab;
    private Ui.SegmentButton settingsTab;
    private String style = "follow";

    MusicPage(Host host, Bili bili, Player player) {
        this.host = host;
        this.bili = bili;
        this.player = player;
        this.cacheDir = host.pluginDataDir();
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
                SwingUtilities.invokeLater(MusicPage.this::refreshQueueImpl);
            }
        });
    }

    JPanel build() {
        loadSettings();
        ThemePreset preset = stylePreset(style);
        if (preset != null) {
            return withPalette(preset);
        }
        return buildRoot();
    }

    private JPanel withPalette(ThemePreset preset) {
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

    private JPanel buildRoot() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(true);
        page.setBackground(Theme.BG);

        JPanel top = new JPanel(new BorderLayout(0, 0));
        top.setOpaque(false);
        top.add(header(), BorderLayout.NORTH);
        top.add(subtabs(), BorderLayout.SOUTH);
        page.add(top, BorderLayout.NORTH);

        cardsLayout = new CardLayout();
        cards = new Ui.FadePanel(cardsLayout);
        cards.setOpaque(false);
        cards.add(searchPage(), "search");
        cards.add(queuePage(), "queue");
        cards.add(settingsPage(), "settings");
        page.add(cards, BorderLayout.CENTER);
        page.add(playerBar(), BorderLayout.SOUTH);

        root = page;
        String initial = System.getProperty("mclite.musicTab", "search");
        if (!"search".equals(initial)) {
            SwingUtilities.invokeLater(() -> selectTab(initial));
        }
        refreshAccount();
        refreshQueue();
        updateReverbLabel();
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
        info.add(Ui.label("B 站音乐 · 队列播放 · 列表/单曲/随机/顺序循环 · 混响", Theme.TINY, Theme.DIM));
        header.add(info, BorderLayout.WEST);
        return header;
    }

    private JComponent subtabs() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(6, 14, 0, 14));
        JPanel tabs = new JPanel();
        tabs.setOpaque(false);
        tabs.setLayout(new BoxLayout(tabs, BoxLayout.X_AXIS));
        searchTab = new Ui.SegmentButton("搜索");
        queueTab = new Ui.SegmentButton("播放列表");
        settingsTab = new Ui.SegmentButton("设置");
        ButtonGroup group = new ButtonGroup();
        group.add(searchTab);
        group.add(queueTab);
        group.add(settingsTab);
        searchTab.setSelected(true);
        searchTab.addActionListener(e -> selectTab("search"));
        queueTab.addActionListener(e -> selectTab("queue"));
        settingsTab.addActionListener(e -> selectTab("settings"));
        tabs.add(searchTab);
        tabs.add(queueTab);
        tabs.add(settingsTab);
        bar.add(tabs, BorderLayout.WEST);
        return bar;
    }

    private void selectTab(String name) {
        if (cards == null || cardsLayout == null) {
            return;
        }
        cardsLayout.show(cards, name);
        searchTab.setSelected("search".equals(name));
        queueTab.setSelected("queue".equals(name));
        settingsTab.setSelected("settings".equals(name));
        if (cards instanceof Ui.FadePanel fade) {
            fade.fadeIn();
        }
    }

    // ---------------------------------------------------------------- 子页

    private JComponent searchPage() {
        JPanel body = column();
        body.setBorder(new EmptyBorder(18, 24, 22, 24));
        body.add(accountCard());
        body.add(Ui.vgap(12));

        Ui.RoundedPanel card = card();
        card.add(Ui.label("搜索", Theme.BODY_BOLD, Theme.TEXT));
        card.add(Ui.vgap(8));
        JPanel row = row();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        keyword.setPreferredSize(new Dimension(380, 30));
        keyword.setMaximumSize(new Dimension(380, 30));
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
        card.add(Ui.vgap(10));
        card.add(results);
        body.add(card);
        body.add(Box.createVerticalGlue());
        return scroll(body);
    }

    private JComponent queuePage() {
        JPanel body = column();
        body.setBorder(new EmptyBorder(18, 24, 22, 24));
        Ui.RoundedPanel card = card();
        JPanel head = row();
        head.add(Ui.label("播放列表", Theme.BODY_BOLD, Theme.TEXT));
        head.add(Box.createHorizontalGlue());
        Ui.AccentButton clear = Ui.smallButton("清空队列", Ui.AccentButton.Style.GHOST);
        clear.addActionListener(e -> {
            player.clear();
            status.setText("队列已清空。");
        });
        head.add(clear);
        card.add(head);
        card.add(Ui.vgap(10));
        card.add(queueRows);
        body.add(card);
        body.add(Box.createVerticalGlue());
        return scroll(body);
    }

    private JComponent settingsPage() {
        JPanel body = column();
        body.setBorder(new EmptyBorder(18, 24, 22, 24));

        Ui.RoundedPanel styleCard = card();
        styleCard.add(Ui.label("界面样式", Theme.BODY_BOLD, Theme.TEXT));
        styleCard.add(Ui.vgap(4));
        styleCard.add(Ui.label("默认跟随启动器当前主题；也可以只给这个插件单独选一套。",
                Theme.TINY, Theme.DIM));
        styleCard.add(Ui.vgap(8));
        JComboBox<String> styleBox = new Ui.DarkCombo<>();
        styleBox.setFont(Theme.SMALL);
        styleBox.addItem("跟随启动器");
        for (ThemePreset preset : ThemePreset.ALL) {
            styleBox.addItem(preset.name());
        }
        styleBox.setSelectedItem("follow".equals(style) ? "跟随启动器" : style);
        styleBox.setPreferredSize(new Dimension(260, 30));
        styleBox.setMaximumSize(new Dimension(260, 30));
        styleBox.addActionListener(e -> {
            String chosen = String.valueOf(styleBox.getSelectedItem());
            saveSetting("style.txt", "跟随启动器".equals(chosen) ? "follow" : chosen);
            style = "跟随启动器".equals(chosen) ? "follow" : chosen;
            SwingUtilities.invokeLater(this::rebuild);
        });
        styleCard.add(styleBox);
        body.add(styleCard);
        body.add(Ui.vgap(12));

        Ui.RoundedPanel reverbCard = card();
        reverbCard.add(Ui.label("混响", Theme.BODY_BOLD, Theme.TEXT));
        reverbCard.add(Ui.vgap(4));
        reverbCard.add(Ui.label("给播放加房间感：轻/中/强；关闭即干声。", Theme.TINY, Theme.DIM));
        reverbCard.add(Ui.vgap(8));
        JComboBox<String> reverbBox = new Ui.DarkCombo<>();
        reverbBox.setFont(Theme.SMALL);
        for (Reverb.Preset preset : Reverb.Preset.values()) {
            reverbBox.addItem(preset.label);
        }
        reverbBox.setSelectedItem(player.reverb().preset().label);
        reverbBox.setPreferredSize(new Dimension(260, 30));
        reverbBox.setMaximumSize(new Dimension(260, 30));
        reverbBox.addActionListener(e -> {
            String chosen = String.valueOf(reverbBox.getSelectedItem());
            for (Reverb.Preset preset : Reverb.Preset.values()) {
                if (preset.label.equals(chosen)) {
                    player.reverb().preset(preset);
                    saveSetting("reverb.txt", preset.name());
                    updateReverbLabel();
                    status.setText("混响：" + preset.label);
                }
            }
        });
        reverbCard.add(reverbBox);
        body.add(reverbCard);
        body.add(Ui.vgap(12));

        Ui.RoundedPanel accountCard = card();
        accountCard.add(Ui.label("登录", Theme.BODY_BOLD, Theme.TEXT));
        accountCard.add(Ui.vgap(4));
        account.setAlignmentX(Component.LEFT_ALIGNMENT);
        accountCard.add(account);
        accountCard.add(Ui.vgap(8));
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
        accountCard.add(row);
        body.add(accountCard);
        body.add(Box.createVerticalGlue());
        return scroll(body);
    }

    private JComponent accountCard() {
        Ui.RoundedPanel card = card();
        card.add(Ui.label("账户", Theme.BODY_BOLD, Theme.TEXT));
        card.add(Ui.vgap(4));
        account.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(account);
        card.add(Ui.vgap(6));
        JLabel hint = Ui.label("搜索不需要登录；高音质播放建议登录，或到「设置」里从游戏实例同步。",
                Theme.TINY, Theme.DIM);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(hint);
        return card;
    }

    private JComponent playerBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.CARD);
        bar.setBorder(new EmptyBorder(8, 16, 8, 16));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(nowPlaying);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(Ui.vgap(2));
        info.add(status);
        bar.add(info, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        Ui.AccentButton prev = Ui.smallButton("上一首", Ui.AccentButton.Style.GHOST);
        prev.addActionListener(e -> player.previous());
        playPause.addActionListener(e -> player.toggle());
        Ui.AccentButton next = Ui.smallButton("下一首", Ui.AccentButton.Style.GHOST);
        next.addActionListener(e -> player.next(true));
        Ui.AccentButton stop = Ui.smallButton("停止", Ui.AccentButton.Style.GHOST);
        stop.addActionListener(e -> player.stop());
        controls.add(prev);
        controls.add(Ui.hgap(6));
        controls.add(playPause);
        controls.add(Ui.hgap(6));
        controls.add(next);
        controls.add(Ui.hgap(6));
        controls.add(stop);
        controls.add(Ui.hgap(12));
        controls.add(reverbLabel);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    // ---------------------------------------------------------------- 数据

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
                    List<Bili.Video> videos = get();
                    if (videos.isEmpty()) {
                        status.setText("没有结果。");
                    } else {
                        for (Bili.Video video : videos) {
                            results.add(resultRow(video));
                            results.add(Ui.vgap(4));
                        }
                        status.setText("找到 " + videos.size() + " 个结果。");
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
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.add(Art.thumb(cacheDir, video.cover(), 40), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.label(video.title(), Theme.SMALL, Theme.TEXT));
        text.add(Ui.label(video.author() + " · " + mmss(video.seconds()), Theme.TINY, Theme.DIM));
        row.add(text, BorderLayout.CENTER);

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
        refreshQueueImpl();
    }

    private void refreshQueueImpl() {
        if (queueRows == null) {
            return;
        }
        queueRows.removeAll();
        List<Bili.Song> songs = player.queue();
        for (int i = 0; i < songs.size(); i++) {
            Bili.Song song = songs.get(i);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            row.add(Art.thumb(cacheDir, song.cover(), 36), BorderLayout.WEST);
            JLabel title = Ui.label((i + 1) + ". " + song.title(), Theme.SMALL,
                    i == player.index() ? Theme.ACCENT : Theme.TEXT);
            row.add(title, BorderLayout.CENTER);
            Ui.AccentButton play = Ui.smallButton("播放", Ui.AccentButton.Style.GHOST);
            final int index = i;
            play.addActionListener(e -> player.play(index));
            row.add(play, BorderLayout.EAST);
            queueRows.add(row);
            queueRows.add(Ui.vgap(3));
        }
        if (songs.isEmpty()) {
            queueRows.add(Ui.label("队列是空的：去「搜索」加几首吧。", Theme.TINY, Theme.DIM));
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

    private void updateReverbLabel() {
        reverbLabel.setText("混响 " + player.reverb().preset().label);
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
                    status.setText("登录链接已复制并打开浏览器；扫码/确认后自动完成。");
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

    // ---------------------------------------------------------------- 设置存取

    private void loadSettings() {
        style = readSetting("style.txt", "follow");
        String reverbName = readSetting("reverb.txt", null);
        if (reverbName != null) {
            for (Reverb.Preset preset : Reverb.Preset.values()) {
                if (preset.name().equals(reverbName)) {
                    player.reverb().preset(preset);
                }
            }
        }
    }

    private String readSetting(String name, String fallback) {
        try {
            Path file = host.pluginDataDir().resolve(name);
            if (Files.isRegularFile(file)) {
                String value = Files.readString(file).trim();
                return value.isEmpty() ? fallback : value;
            }
        } catch (Exception ignored) {
            // 读取失败用默认值。
        }
        return fallback;
    }

    private void saveSetting(String name, String value) {
        try {
            Files.writeString(host.pluginDataDir().resolve(name), value);
        } catch (Exception ignored) {
            // 存不了只影响下次启动。
        }
    }

    private static ThemePreset stylePreset(String name) {
        if (name == null || name.isBlank() || "follow".equals(name)) {
            return null;
        }
        for (ThemePreset preset : ThemePreset.ALL) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 小工具

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
        return panel;
    }

    private static JPanel row() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JComponent scroll(JPanel body) {
        JScrollPane scroll = Ui.scroll(body);
        return scroll;
    }
}
