package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** 26.x 播放器界面：extractRenderState 新渲染模型。 */
final class IslandPlayerScreen26 extends Screen {

    private final PlaybackService service;
    private final IslandConfig config;
    private final Runnable saveConfig;

    private List<Track> tracks;
    private int scroll;
    private int listTop;
    private int listBottom;
    private final int rowHeight = 14;
    private String status = "";
    private int refreshTicks;
    private Button playButton;
    private Button modeButton;
    private Button lyricsButton;

    IslandPlayerScreen26(PlaybackService service, IslandConfig config, Runnable saveConfig) {
        super(Component.literal("MCLite Island"));
        this.service = service;
        this.config = config;
        this.saveConfig = saveConfig;
        this.tracks = service.playlist().tracks();
    }

    private static Component text(String value) {
        return Component.literal(value == null ? "" : value);
    }

    @Override
    protected void init() {
        int left = width / 2 - 200;
        int right = width / 2 + 200;
        int row1 = height - 30;
        int row2 = height - 54;

        playButton = addRenderableWidget(Button.builder(text("播放"), b -> {
            service.togglePlay();
            refreshLabels();
        }).bounds(left, row1, 64, 20).build());
        addRenderableWidget(Button.builder(text("上一首"), b -> {
            service.previous();
            refreshLabels();
        }).bounds(left + 68, row1, 52, 20).build());
        addRenderableWidget(Button.builder(text("下一首"), b -> {
            service.next(true);
            refreshLabels();
        }).bounds(left + 124, row1, 52, 20).build());
        modeButton = addRenderableWidget(Button.builder(text("模式"), b -> {
            service.cycleMode();
            refreshLabels();
            saveConfig.run();
        }).bounds(left + 180, row1, 64, 20).build());
        lyricsButton = addRenderableWidget(Button.builder(text("3D 歌词"), b -> {
            config.threeDLyrics = !config.threeDLyrics;
            refreshLabels();
            saveConfig.run();
        }).bounds(left + 248, row1, 68, 20).build());
        addRenderableWidget(Button.builder(text("导入 LRC"), b -> importLyrics())
                .bounds(left + 320, row1, 80, 20).build());
        addRenderableWidget(Button.builder(text("关闭"), b -> onClose())
                .bounds(right - 40, row1, 40, 20).build());

        addRenderableWidget(Button.builder(text("音乐目录"), b -> openFolder(service.musicDir()))
                .bounds(left, row2, 64, 20).build());
        addRenderableWidget(Button.builder(text("歌词目录"), b -> openFolder(service.lyricsDir()))
                .bounds(left + 68, row2, 64, 20).build());
        int x = left + 140;
        addRenderableWidget(Button.builder(text("←"), b -> moveIsland(-8, 0)).bounds(x, row2, 20, 20).build());
        addRenderableWidget(Button.builder(text("→"), b -> moveIsland(8, 0)).bounds(x + 22, row2, 20, 20).build());
        addRenderableWidget(Button.builder(text("↑"), b -> moveIsland(0, -6)).bounds(x + 44, row2, 20, 20).build());
        addRenderableWidget(Button.builder(text("↓"), b -> moveIsland(0, 6)).bounds(x + 66, row2, 20, 20).build());
        addRenderableWidget(Button.builder(text("缩小"), b -> scaleIsland(-0.1f)).bounds(x + 92, row2, 40, 20).build());
        addRenderableWidget(Button.builder(text("放大"), b -> scaleIsland(0.1f)).bounds(x + 136, row2, 40, 20).build());
        refreshLabels();
    }

    private void refreshLabels() {
        if (playButton != null) {
            var snap = service.snapshot();
            playButton.setMessage(text(snap.playing() && !snap.paused() ? "暂停" : "播放"));
        }
        if (modeButton != null) {
            modeButton.setMessage(text("模式:" + config.playMode.label()));
        }
        if (lyricsButton != null) {
            lyricsButton.setMessage(text(config.threeDLyrics ? "3D 歌词:开" : "3D 歌词:关"));
        }
    }

    private void moveIsland(int dx, int dy) {
        config.islandX = Math.max(0, config.islandX + dx);
        config.islandY = Math.max(0, config.islandY + dy);
        saveConfig.run();
    }

    private void scaleIsland(float delta) {
        config.islandScale = Math.max(0.5f, Math.min(3f, config.islandScale + delta));
        saveConfig.run();
    }

    private void openFolder(Path path) {
        try {
            Files.createDirectories(path);
        } catch (Exception ignored) {
            // 目录建不出来时只提示
        }
        try {
            java.awt.Desktop.getDesktop().open(path.toFile());
        } catch (Exception ignored) {
            // 打不开目录时状态栏会提示
        }
    }

    private void importLyrics() {
        Track current = service.playlist().current();
        if (current == null) {
            status = "先播放一首歌，再导入同名 LRC";
            return;
        }
        Thread thread = new Thread(() -> {
            FileDialog dialog = new FileDialog((Frame) null, "选择 LRC 歌词", FileDialog.LOAD);
            dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".lrc"));
            dialog.setVisible(true);
            String name = dialog.getFile();
            String directory = dialog.getDirectory();
            if (name == null || directory == null) {
                return;
            }
            try {
                Path source = Path.of(directory, name);
                String stem = current.fileName();
                int dot = stem.lastIndexOf('.');
                stem = dot > 0 ? stem.substring(0, dot) : stem;
                Path target = service.lyricsDir().resolve(stem + ".lrc");
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                service.invalidateLyrics(current.file());
                Minecraft.getInstance().execute(() -> status = "已导入歌词: " + target.getFileName());
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> status = "导入失败: " + ex.getMessage());
            }
        }, "mclite-island-import-lrc");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        extractBackground(extractor, mouseX, mouseY, delta);
        if (--refreshTicks <= 0) {
            tracks = service.playlist().tracks();
            refreshTicks = 20;
        }
        var font = minecraft.font;

        extractor.text(font, "MCLite Island · 本地音乐", 12, 10, 0xFFFFFFFF, false);
        String hint = "共 " + tracks.size() + " 首 · " + config.playMode.label()
                + (config.threeDLyrics ? " · 3D 歌词开" : "");
        extractor.text(font, hint, 12, 24, 0xFF9AA4B0, false);

        var snap = service.snapshot();
        String now = snap.hasTrack() ? (snap.paused() ? "已暂停 " : "") + snap.title() : "未在播放";
        extractor.text(font, now, 12, 38, 0xFF7CC7FF, false);

        listTop = 56;
        listBottom = height - 64;
        int visible = Math.max(1, (listBottom - listTop) / rowHeight);
        int maxScroll = Math.max(0, tracks.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int currentIndex = service.playlist().index();
        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            if (index >= tracks.size()) {
                break;
            }
            int y = listTop + i * rowHeight;
            if (index == currentIndex) {
                extractor.fill(4, y - 2, width - 4, y + rowHeight - 2, 0x40007CC7);
            }
            String name = (index + 1) + ". " + tracks.get(index).displayName();
            extractor.text(font, font.plainSubstrByWidth(name, width - 32), 8, y, 0xFFE6E6E6, false);
        }

        if (!status.isBlank()) {
            extractor.text(font, status, 12, height - 66, 0xFFFFB74D, false);
        }
        if (snap.lyric() != null && !snap.lyric().isBlank()) {
            extractor.text(font, "♪ " + snap.lyric(), 12, height - 78, 0xFFB3E5FC, false);
        }
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        refreshLabels();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.y() >= listTop && event.y() < listBottom) {
            int index = scroll + (int) ((event.y() - listTop) / rowHeight);
            if (index >= 0 && index < tracks.size()) {
                service.playAt(index);
                refreshLabels();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll -= (int) Math.signum(deltaY) * 3;
        return true;
    }

    @Override
    public void onClose() {
        saveConfig.run();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
