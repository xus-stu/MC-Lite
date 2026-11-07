package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 26.x 播放器：圆角双栏布局。 */
final class IslandPlayerScreen26 extends Screen {

    private final PlaybackService service;
    private final IslandConfig config;
    private final Runnable saveConfig;
    private final McliteIsland26Client client;

    private List<Track> tracks = List.of();
    private int scroll;
    private int listTop;
    private int listBottom;
    private int panelX;
    private int panelY;
    private final int rowHeight = 20;
    private String status = "";
    private int refreshTicks;

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
        service.reloadLibrary();
        tracks = service.playlist().tracks();
        int w = 700;
        int h = 470;
        panelX = (width - w) / 2;
        panelY = (height - h) / 2;
        int left = panelX + 20;
        int row1 = panelY + 392;
        int row2 = panelY + 422;

        addRenderableWidget(UiButton26.primary("播放", left, row1, 60, 24, () -> {
            service.togglePlay();
        }));
        addRenderableWidget(UiButton26.ghost("上一首", left + 68, row1, 56, 24, service::previous));
        addRenderableWidget(UiButton26.ghost("下一首", left + 132, row1, 56, 24,
                () -> service.next(true)));
        addRenderableWidget(UiButton26.ghost("模式: " + config.playMode.label(), left + 196,
                row1, 96, 24, () -> {
                    service.cycleMode();
                    saveConfig.run();
                }));
        addRenderableWidget(UiButton26.ghost("刷新", left + 300, row1, 56, 24, () -> {
            service.reloadLibrary();
            tracks = service.playlist().tracks();
            status = "已重新扫描，共 " + tracks.size() + " 首";
        }));
        addRenderableWidget(UiButton26.ghost("关闭", left + 620, row1, 56, 24, this::onClose));

        addRenderableWidget(UiButton26.ghost("B站", left, row2, 56, 24, () -> Minecraft.getInstance()
                .setScreen(new BiliBrowseScreen26(client, service, config))));
        addRenderableWidget(UiButton26.ghost("导入歌词", left + 64, row2, 76, 24,
                () -> Minecraft.getInstance().setScreen(
                        new LrcImportScreen26(service, saveConfig))));
        addRenderableWidget(UiButton26.ghost("设置", left + 148, row2, 56, 24,
                () -> Minecraft.getInstance().setScreen(
                        new IslandConfigScreen26(client, service, config, saveConfig))));
        addRenderableWidget(UiButton26.ghost("音乐目录", left + 212, row2, 76, 24,
                () -> openFolder(service.musicDir())));
        addRenderableWidget(UiButton26.ghost("歌词目录", left + 296, row2, 76, 24,
                () -> openFolder(service.lyricsDir())));

        listTop = panelY + 112;
        listBottom = panelY + 372;
    }

    private void openFolder(Path path) {
        try {
            Files.createDirectories(path);
            java.awt.Desktop.getDesktop().open(path.toFile());
            status = "已打开: " + path;
        } catch (Exception ex) {
            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(path.toString());
            } catch (Exception ignored) {
                // 剪贴板不可用
            }
            status = "打不开目录（路径已复制）: " + path;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        if (--refreshTicks <= 0) {
            tracks = service.playlist().tracks();
            refreshTicks = 20;
        }
        UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY, 700, 470, 20);
        var font = minecraft.font;
        extractor.text(font, "MCLite Island", panelX + 20, panelY + 18, 0xFFF2F5F8, false);
        extractor.text(font, "本地音乐 · B站 · 3D 歌词", panelX + 20, panelY + 40, 0xFF93A0AD, false);
        String count = tracks.size() + " 首";
        extractor.text(font, count, panelX + 680 - font.width(count), panelY + 18, 0xFF93A0AD, false);

        // 左栏队列卡片
        int listX = panelX + 20;
        int listW = 380;
        UiDraw26.panel(extractor, UiDraw26.DARK, listX, panelY + 68, listW, 300, 14);
        extractor.text(font, "播放队列", listX + 12, panelY + 78, 0xFF93A0AD, false);
        int visible = Math.max(1, (listBottom - listTop) / rowHeight);
        int maxScroll = Math.max(0, tracks.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int current = service.playlist().index();
        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            if (index >= tracks.size()) {
                break;
            }
            int y = listTop + i * rowHeight;
            if (index == current) {
                UiDraw26.panel(extractor, UiDraw26.SOFT, listX + 6, y - 2, listW - 12, 18, 8);
            }
            String name = (index + 1) + ". " + tracks.get(index).displayName();
            extractor.text(font, font.plainSubstrByWidth(name, listW - 28), listX + 14, y,
                    index == current ? 0xFF6CC6FF : 0xFFF2F5F8, false);
        }
        if (tracks.isEmpty()) {
            extractor.text(font, "音乐目录为空 · 点「音乐目录」放入 wav/mp3",
                    listX + 14, listTop + 8, 0xFF93A0AD, false);
        }

        // 右栏正在播放
        int nowX = panelX + 412;
        int nowW = 268;
        UiDraw26.panel(extractor, UiDraw26.DARK, nowX, panelY + 68, nowW, 300, 14);
        var snap = service.snapshot();
        extractor.text(font, "正在播放", nowX + 12, panelY + 78, 0xFF93A0AD, false);
        if (snap.hasTrack()) {
            String title = snap.title();
            extractor.text(font, font.plainSubstrByWidth(title, nowW - 24), nowX + 12,
                    panelY + 102, 0xFFF2F5F8, false);
            extractor.text(font, font.plainSubstrByWidth(snap.artist(), nowW - 24), nowX + 12,
                    panelY + 118, 0xFF93A0AD, false);
            String lyric = snap.lyric() == null || snap.lyric().isBlank() ? "♪ 暂无歌词" : snap.lyric();
            extractor.text(font, font.plainSubstrByWidth(lyric, nowW - 24), nowX + 12,
                    panelY + 160, 0xFF6CC6FF, false);
            String next = snap.nextLyric();
            if (next != null && !next.isBlank()) {
                extractor.text(font, font.plainSubstrByWidth(next, nowW - 24), nowX + 12,
                        panelY + 178, 0xFF93A0AD, false);
            }
            long duration = snap.durationMs();
            float p = duration > 0 ? Math.min(1f, snap.positionMs() / (float) duration) : 0f;
            extractor.fill(nowX + 12, panelY + 230, nowX + nowW - 12, panelY + 234, 0x33FFFFFF);
            if (p > 0) {
                extractor.fill(nowX + 12, panelY + 230,
                        nowX + 12 + (int) ((nowW - 24) * p), panelY + 234, 0xFF6CC6FF);
            }
            String pos = format(snap.positionMs());
            String dur = duration > 0 ? format(duration) : "--:--";
            extractor.text(font, pos, nowX + 12, panelY + 240, 0xFF93A0AD, false);
            extractor.text(font, dur, nowX + nowW - 12 - font.width(dur), panelY + 240,
                    0xFF93A0AD, false);
        } else {
            extractor.text(font, "未在播放", nowX + 12, panelY + 110, 0xFF93A0AD, false);
        }

        String message = status.isBlank() && service.lastError() != null
                && !service.lastError().isBlank() ? "音频错误: " + service.lastError() : status;
        if (!message.isBlank()) {
            extractor.text(font, font.plainSubstrByWidth(message, 660), panelX + 20,
                    panelY + 452,
                    message.contains("错误") || message.startsWith("打不开") ? 0xFFFF6B6B
                            : 0xFF93A0AD, false);
        }
        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int listX = panelX + 20;
        if (event.button() == 0 && event.x() >= listX && event.x() <= listX + 380
                && event.y() >= listTop && event.y() < listBottom) {
            int index = scroll + (int) ((event.y() - listTop) / rowHeight);
            if (index >= 0 && index < tracks.size()) {
                service.playAt(index);
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

    private static String format(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
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
