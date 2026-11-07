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
    private final McliteIsland26Client client;

    private List<Track> tracks = List.of();
    private int scroll;
    private int listTop;
    private int listBottom;
    private final int rowHeight = 14;
    private String status = "";
    private int refreshTicks;
    private Button playButton;
    private Button modeButton;
    private Button lyricsButton;

    IslandPlayerScreen26(PlaybackService service, IslandConfig config, Runnable saveConfig,
                         McliteIsland26Client client) {
        super(Component.literal("MCLite Island"));
        this.service = service;
        this.config = config;
        this.saveConfig = saveConfig;
        this.client = client;
        this.tracks = service.playlist().tracks();
    }

    private static Component text(String value) {
        return Component.literal(value == null ? "" : value);
    }

    @Override
    protected void init() {
        // 打开界面时热扫描一次，刚放进去的 wav 不用重启
        service.reloadLibrary();
        tracks = service.playlist().tracks();

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
        }).bounds(left + 72, row1, 52, 20).build());
        addRenderableWidget(Button.builder(text("下一首"), b -> {
            service.next(true);
            refreshLabels();
        }).bounds(left + 132, row1, 52, 20).build());
        modeButton = addRenderableWidget(Button.builder(text("模式"), b -> {
            service.cycleMode();
            refreshLabels();
            saveConfig.run();
        }).bounds(left + 192, row1, 64, 20).build());
        addRenderableWidget(Button.builder(text("刷新列表"), b -> {
            service.reloadLibrary();
            tracks = service.playlist().tracks();
            status = "已重新扫描，共 " + tracks.size() + " 首";
            refreshLabels();
        }).bounds(left + 264, row1, 64, 20).build());
        addRenderableWidget(Button.builder(text("关闭"), b -> onClose())
                .bounds(right - 44, row1, 44, 20).build());

        lyricsButton = addRenderableWidget(Button.builder(text("3D 歌词"), b -> {
            config.threeDLyrics = !config.threeDLyrics;
            refreshLabels();
            saveConfig.run();
        }).bounds(left, row2, 64, 20).build());
        addRenderableWidget(Button.builder(text("导入 LRC"), b -> importLyrics())
                .bounds(left + 72, row2, 64, 20).build());
        addRenderableWidget(Button.builder(text("B站"), b -> Minecraft.getInstance()
                        .setScreen(new BiliBrowseScreen26(client, service, config)))
                .bounds(left + 144, row2, 52, 20).build());
        addRenderableWidget(Button.builder(text("设置"), b -> Minecraft.getInstance()
                .setScreen(new IslandConfigScreen26(client, service, config, saveConfig)))
                .bounds(left + 200, row2, 52, 20).build());
        addRenderableWidget(Button.builder(text("音乐目录"), b -> openFolder(service.musicDir()))
                .bounds(left + 256, row2, 64, 20).build());
        addRenderableWidget(Button.builder(text("歌词目录"), b -> openFolder(service.lyricsDir()))
                .bounds(left + 324, row2, 64, 20).build());

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

    private void openFolder(Path path) {
        try {
            Files.createDirectories(path);
            java.awt.Desktop.getDesktop().open(path.toFile());
        } catch (Exception ignored) {
            status = "打开目录失败: " + path;
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
            dialog.dispose();
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
        // 父类已经做过 extractBackground（含模糊），子类不能再调
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

        if (status.isBlank() && service.lastError() != null && !service.lastError().isBlank()) {
            status = "音频错误: " + service.lastError();
        }
        if (!status.isBlank()) {
            extractor.text(font, status, 12, height - 66, 0xFFFFB74D, false);
        }
        String lyricInfo = snap.lyric() != null && !snap.lyric().isBlank()
                ? "♪ " + snap.lyric() : "歌词: 无（同名 .lrc 或导入）";
        extractor.text(font, font.plainSubstrByWidth(lyricInfo, width - 24), 12, height - 78,
                snap.lyric() != null && !snap.lyric().isBlank() ? 0xFFB3E5FC : 0xFF9AA4B0, false);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
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
