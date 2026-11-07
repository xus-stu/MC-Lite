package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlayMode;
import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 26.x 播放器：720x460 双栏卡片。
 *
 * <p>静态底图由 {@link UISurface26} 烘焙，这里只画动态内容：
 * 队列行、歌词、进度、按钮。按钮全部使用 {@link UiButton26}，没有原版 Button。</p>
 */
final class IslandPlayerScreen26 extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int DIM = 0xFF93A0AD;
    private static final int MUTED = 0xFF6B7885;
    private static final int ACCENT = 0xFF8FD3FF;
    private static final int ERROR = 0xFFFF8A8A;
    private static final String HINT = "选择左侧曲目 · 拖动进度条快进 · M 打开/关闭";

    private final PlaybackService service;
    private final IslandConfig config;
    private final Runnable saveConfig;
    private final McliteIsland26Client client;

    private List<Track> tracks = List.of();
    private String[] rowText = new String[0];
    private String countText = "0 首";
    private int scroll;
    private int refreshTicks;
    private String status = "";

    private int panelX;
    private int panelY;
    private int listX;
    private int listTop;
    private int listW;
    private int listBottom;
    private UISurface26.Surface background;

    private UiButton26 playButton;
    private UiButton26 modeButton;

    private final UiSlider26 progress = new UiSlider26();
    private final UiSlider26 volumeSlider = new UiSlider26();
    private boolean seeking;
    private boolean volumeDragging;
    private long seekTargetMs;

    private final UiText26 nowTitle = new UiText26();
    private final UiText26 nowArtist = new UiText26();
    private final UiText26 nowLyric = new UiText26();
    private final UiText26 nextLyric = new UiText26();
    private final UiText26 statusLine = new UiText26();

    private int shownVolume = -1;
    private String volumeText = "70%";
    private String volumeLabel = "音量 70%";
    private long shownPositionSecond = Long.MIN_VALUE;
    private long shownDurationSecond = Long.MIN_VALUE;
    private String positionText = "0:00";
    private String durationText = "--:--";
    private String lastError = "";
    private String errorLine = "";
    private PlayMode shownMode;

    IslandPlayerScreen26(PlaybackService service, IslandConfig config, Runnable saveConfig,
                         McliteIsland26Client client) {
        super(Component.literal("MCLite Island"));
        this.service = service;
        this.config = config;
        this.saveConfig = saveConfig;
        this.client = client;
    }

    @Override
    protected void init() {
        panelX = Math.max(8, (width - UiPaint26.PLAYER_W) / 2);
        panelY = Math.max(8, (height - UiPaint26.PLAYER_H) / 2);
        seeking = false;
        volumeDragging = false;
        progress.release();
        volumeSlider.release();
        background = UISurface26.get("player", UiPaint26.PLAYER_W, UiPaint26.PLAYER_H,
                UiPaint26::player);

        listX = panelX + UiPaint26.PLAYER_LEFT_X + 8;
        listW = UiPaint26.PLAYER_LEFT_W - 16;
        listTop = panelY + UiPaint26.PLAYER_LEFT_Y + 36;
        listBottom = panelY + UiPaint26.PLAYER_LEFT_Y + UiPaint26.PLAYER_LEFT_H - 10;

        if (service.playlist().isEmpty()) {
            service.reloadLibrary();
        }
        tracks = service.playlist().tracks();
        rebuildRowText();
        int currentIndex = service.playlist().index();
        scroll = Math.max(0, currentIndex - 4);

        int barY = panelY + UiPaint26.PLAYER_BAR_Y + 10;
        int x = panelX + UiPaint26.PLAYER_BAR_X + 16;
        playButton = addRenderableWidget(UiButton26.primary("播放", x, barY, 66, 30, () -> {
            service.togglePlay();
            syncTransport();
        }));
        addRenderableWidget(UiButton26.ghost("上一首", x + 74, barY, 52, 30, () -> {
            service.previous();
            syncTransport();
        }));
        addRenderableWidget(UiButton26.ghost("下一首", x + 132, barY, 52, 30, () -> {
            service.next(true);
            syncTransport();
        }));
        modeButton = addRenderableWidget(UiButton26.ghost(modeLabel(config.playMode),
                x + 190, barY, 94, 30, () -> {
                    service.cycleMode();
                    saveConfig.run();
                    syncTransport();
                }));
        addRenderableWidget(UiButton26.ghost("刷新", x + 292, barY, 52, 30, this::rescan));

        int right = x + 360;
        addRenderableWidget(UiButton26.ghost("B站", right, barY, 52, 30,
                () -> Minecraft.getInstance().setScreen(
                        new BiliBrowseScreen26(client, service, config))));
        addRenderableWidget(UiButton26.ghost("设置", right + 60, barY, 52, 30,
                () -> Minecraft.getInstance().setScreen(
                        new IslandConfigScreen26(client, service, config, saveConfig))));
        addRenderableWidget(UiButton26.ghost("导入歌词", right + 120, barY, 78, 30,
                () -> Minecraft.getInstance().setScreen(
                        new LrcImportScreen26(service, saveConfig))));
        addRenderableWidget(UiButton26.ghost("打开目录", right + 206, barY, 78, 30,
                () -> openFolder(service.musicDir())));

        addRenderableWidget(UiButton26.ghost("关闭", panelX + 644, panelY + 18, 54, 24,
                this::onClose));

        syncTransport();
        updateVolumeText();
    }

    private static String modeLabel(PlayMode mode) {
        return switch (mode) {
            case SEQUENTIAL -> "顺序播放";
            case RANDOM -> "随机播放";
            case LOOP_ONE -> "单曲循环";
            case LOOP_ALL -> "列表循环";
        };
    }

    private void syncTransport() {
        IslandSnapshot snapshot = service.snapshot();
        String label = snapshot.paused() ? "继续" : snapshot.playing() ? "暂停" : "播放";
        if (playButton != null) {
            playButton.setLabel(label);
        }
        if (modeButton != null && shownMode != config.playMode) {
            shownMode = config.playMode;
            modeButton.setLabel(modeLabel(shownMode));
        }
    }

    private void updateVolumeText() {
        int percent = Math.round(config.volume * 100f);
        if (percent != shownVolume) {
            shownVolume = percent;
            volumeText = percent + "%";
            volumeLabel = "音量 " + volumeText;
        }
    }

    private void rescan() {
        service.reloadLibrary();
        refreshTracks();
        status = "已重新扫描，共 " + tracks.size() + " 首";
    }

    private void refreshTracks() {
        List<Track> latest = service.playlist().tracks();
        if (latest.equals(tracks)) {
            if (rowText.length != tracks.size()) {
                rebuildRowText();
            }
            return;
        }
        tracks = latest;
        rebuildRowText();
    }

    private void rebuildRowText() {
        countText = tracks.size() + " 首";
        rowText = new String[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            String base = (i + 1) + ". " + tracks.get(i).displayName();
            rowText[i] = UiText26.fit(minecraft.font, base, listW - 30);
        }
    }

    @Override
    public void tick() {
        if (--refreshTicks <= 0) {
            refreshTicks = 10;
            refreshTracks();
            syncTransport();
        }
        updateVolumeText();
    }

    private void openFolder(Path path) {
        try {
            Files.createDirectories(path);
            java.awt.Desktop.getDesktop().open(path.toFile());
            status = "已打开目录: " + path;
        } catch (Exception failure) {
            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(path.toString());
            } catch (Exception ignored) {
                // 剪贴板也不可用时只提示路径写入失败
            }
            status = "打不开目录（路径已复制）: " + path;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        Font font = minecraft.font;
        if (background != null) {
            UISurface26.draw(extractor, background, panelX, panelY);
        } else {
            UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY,
                    UiPaint26.PLAYER_W, UiPaint26.PLAYER_H, 20);
            UiDraw26.panel(extractor, UiDraw26.DARK, panelX + UiPaint26.PLAYER_LEFT_X,
                    panelY + UiPaint26.PLAYER_LEFT_Y, UiPaint26.PLAYER_LEFT_W,
                    UiPaint26.PLAYER_LEFT_H, 14);
            UiDraw26.panel(extractor, UiDraw26.DARK, panelX + UiPaint26.PLAYER_RIGHT_X,
                    panelY + UiPaint26.PLAYER_RIGHT_Y, UiPaint26.PLAYER_RIGHT_W,
                    UiPaint26.PLAYER_RIGHT_H, 14);
        }

        IslandSnapshot snapshot = service.snapshot();
        updateVolumeText();

        drawHeader(extractor, font);
        drawQueue(extractor, font, mouseX, mouseY, snapshot);
        drawNowPlaying(extractor, font, mouseX, mouseY, snapshot);
        drawFooter(extractor, font, snapshot);

        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor extractor, Font font) {
        drawScaledText(extractor, font, "MCLite Island",
                panelX + 44, panelY + 16, 1.25f, TEXT);
        extractor.text(font, "本地音乐 · B站 · 歌词", panelX + 44, panelY + 37, DIM, false);
        UiDraw26.textRight(extractor, font, countText, panelX + 632, panelY + 20, DIM);
    }

    private void drawQueue(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY,
                           IslandSnapshot snapshot) {
        int cardX = panelX + UiPaint26.PLAYER_LEFT_X;
        int cardY = panelY + UiPaint26.PLAYER_LEFT_Y;
        int cardW = UiPaint26.PLAYER_LEFT_W;
        extractor.text(font, "播放队列", cardX + 16, cardY + 12, DIM, false);
        UiDraw26.textRight(extractor, font, countText, cardX + cardW - 16, cardY + 12, MUTED);

        if (tracks.isEmpty()) {
            drawQueueEmpty(extractor, font, cardX, cardY, cardW);
            return;
        }

        int visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, tracks.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int current = service.playlist().index();

        for (int row = 0; row < visible; row++) {
            int index = scroll + row;
            if (index >= tracks.size()) {
                break;
            }
            int rowY = listTop + row * ROW_HEIGHT;
            boolean hover = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 2;
            boolean playing = index == current;
            if (playing || hover) {
                UiDraw26.panel(extractor, playing ? UiDraw26.HOVER : UiDraw26.SOFT,
                        listX + 2, rowY - 1, listW - 10, ROW_HEIGHT - 2);
            }
            if (playing) {
                extractor.fill(cardX + 14, rowY + 5, cardX + 16, rowY + 19, 0xFF6CC6FF);
            }
            int color = playing ? ACCENT : hover ? TEXT : 0xFFD7DEE6;
            extractor.text(font, rowText[index], listX + 12, rowY + 3, color, false);
        }

        if (maxScroll > 0) {
            int trackX = listX + listW - 3;
            int viewH = listBottom - listTop;
            extractor.fill(trackX, listTop, trackX + 3, listBottom, 0x14FFFFFF);
            int thumbH = Math.max(18, viewH * visible / tracks.size());
            int thumbY = listTop + (int) ((viewH - thumbH) * (scroll / (float) maxScroll));
            extractor.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0x55FFFFFF);
        }

    }

    private void drawQueueEmpty(GuiGraphicsExtractor extractor, Font font, int cardX, int cardY,
                                int cardW) {
        int centerX = cardX + cardW / 2;
        drawScaledText(extractor, font, "♪", centerX - 12, cardY + 78, 2.4f, 0x40FFFFFF);
        UiDraw26.textCentered(extractor, font, "音乐目录为空", centerX, cardY + 128, DIM);
        UiDraw26.textCentered(extractor, font, "点底部「打开目录」放入 mp3 / wav / flac",
                centerX, cardY + 146, MUTED);
    }

    private void drawNowPlaying(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY,
                                IslandSnapshot snapshot) {
        int cardX = panelX + UiPaint26.PLAYER_RIGHT_X;
        int cardY = panelY + UiPaint26.PLAYER_RIGHT_Y;
        int cardW = UiPaint26.PLAYER_RIGHT_W;
        extractor.text(font, "正在播放", cardX + 16, cardY + 12, DIM, false);

        // 封面占位
        int artX = cardX + 16;
        int artY = cardY + 34;
        drawScaledText(extractor, font, "♪", artX + 21, artY + 20, 2.3f, 0xE6FFFFFF);

        int textX = cardX + 96;
        int textW = cardW - 112;
        if (snapshot.hasTrack()) {
            nowTitle.set(font, snapshot.title(), Math.round(textW / 1.16f));
            drawScaledText(extractor, font, nowTitle.text(), textX, cardY + 36, 1.16f, TEXT);
            nowArtist.set(font, snapshot.artist(), textW);
            extractor.text(font, nowArtist.text(), textX, cardY + 56, DIM, false);
        } else {
            extractor.text(font, "未在播放", textX, cardY + 38, TEXT, false);
            extractor.text(font, "按 M 打开播放器", textX, cardY + 56, DIM, false);
        }

        extractor.fill(cardX + 16, cardY + 112, cardX + cardW - 16, cardY + 113, 0x1AFFFFFF);
        extractor.text(font, "歌词", cardX + 16, cardY + 122, MUTED, false);

        if (snapshot.hasTrack()) {
            String lyric = snapshot.lyric() == null ? "" : snapshot.lyric();
            boolean hasLyric = !lyric.isBlank();
            nowLyric.set(font, hasLyric ? lyric : "暂无歌词", cardW - 32);
            extractor.text(font, nowLyric.text(), cardX + 16, cardY + 140,
                    hasLyric ? ACCENT : MUTED, false);
            String next = snapshot.nextLyric() == null ? "" : snapshot.nextLyric();
            if (!next.isBlank()) {
                nextLyric.set(font, next, cardW - 32);
                extractor.text(font, nextLyric.text(), cardX + 16, cardY + 158, DIM, false);
            }
        } else {
            extractor.text(font, "在左侧选一首开始播放", cardX + 16, cardY + 140, MUTED, false);
        }

        long duration = snapshot.durationMs();
        long position = seeking ? seekTargetMs : snapshot.positionMs();
        float fraction = duration > 0L
                ? Math.max(0f, Math.min(1f, position / (float) duration)) : 0f;
        progress.bounds(cardX + 16, cardY + 190, cardW - 32, 16);
        boolean progressHover = progress.contains(mouseX, mouseY);
        progress.render(extractor, fraction, progressHover);
        updateTimeLabels(position, duration);
        extractor.text(font, positionText, cardX + 16, cardY + 212, DIM, false);
        UiDraw26.textRight(extractor, font, durationText, cardX + cardW - 16, cardY + 212, DIM);

        extractor.text(font, "音量", cardX + 16, cardY + 232, MUTED, false);
        UiDraw26.textRight(extractor, font, volumeText, cardX + cardW - 16, cardY + 232, DIM);
        volumeSlider.bounds(cardX + 16, cardY + 248, cardW - 32, 16);
        volumeSlider.render(extractor, config.volume, volumeSlider.contains(mouseX, mouseY));
    }

    private void drawFooter(GuiGraphicsExtractor extractor, Font font, IslandSnapshot snapshot) {
        String message = status;
        if (message.isBlank()) {
            String error = service.lastError();
            if (error != null && !error.isBlank()) {
                if (!error.equals(lastError)) {
                    lastError = error;
                    errorLine = "音频错误: " + error;
                }
                message = errorLine;
            }
        }
        String shown;
        int color;
        if (message.isBlank()) {
            shown = HINT;
            color = MUTED;
        } else {
            shown = message;
            color = message.startsWith("音频错误") || message.startsWith("打不开") ? ERROR : DIM;
        }
        statusLine.set(font, shown, 520);
        extractor.text(font, statusLine.text(), panelX + 38, panelY + 380, color, false);

        UiDraw26.textRight(extractor, font, volumeLabel, panelX + 682, panelY + 380, MUTED);
    }

    private void updateTimeLabels(long position, long durationMs) {
        long positionSecond = Math.max(0L, position) / 1000L;
        if (positionSecond != shownPositionSecond) {
            shownPositionSecond = positionSecond;
            positionText = format(positionSecond * 1000L);
        }
        long durationSecond = durationMs > 0L ? durationMs / 1000L : -1L;
        if (durationSecond != shownDurationSecond) {
            shownDurationSecond = durationSecond;
            durationText = durationSecond >= 0L ? format(durationSecond * 1000L) : "--:--";
        }
    }

    private static String format(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private static void drawScaledText(GuiGraphicsExtractor extractor, Font font, String text,
                                       int x, int y, float scale, int color) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        extractor.text(font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            IslandSnapshot snapshot = service.snapshot();
            long duration = snapshot.durationMs();
            if (duration > 0L && progress.mouseClicked(event.x(), event.y())) {
                seeking = true;
                updateSeek(event.x());
                return true;
            }
            if (volumeSlider.mouseClicked(event.x(), event.y())) {
                volumeDragging = true;
                updateVolume(event.x());
                return true;
            }
            if (event.x() >= listX && event.x() <= listX + listW
                    && event.y() >= listTop && event.y() < listBottom) {
                int row = (int) ((event.y() - listTop) / ROW_HEIGHT);
                int index = scroll + row;
                if (index >= 0 && index < tracks.size()) {
                    service.playAt(index);
                    status = "播放: " + tracks.get(index).displayName();
                    syncTransport();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void updateSeek(double mouseX) {
        long duration = service.snapshot().durationMs();
        if (duration <= 0L) {
            return;
        }
        seekTargetMs = (long) (progress.fractionAt(mouseX) * duration);
    }

    private void updateVolume(double mouseX) {
        float value = volumeSlider.fractionAt(mouseX);
        service.setVolume(value);
        updateVolumeText();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (seeking) {
            updateSeek(event.x());
            return true;
        }
        if (volumeDragging) {
            updateVolume(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (seeking) {
            service.seek(seekTargetMs);
            seeking = false;
            progress.release();
            syncTransport();
            return true;
        }
        if (volumeDragging) {
            volumeDragging = false;
            volumeSlider.release();
            saveConfig.run();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= listX && mouseX <= listX + listW
                && mouseY >= listTop && mouseY <= listBottom) {
            scroll -= (int) Math.signum(deltaY) * 2;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        if (seeking) {
            service.seek(seekTargetMs);
        }
        service.saveState();
        saveConfig.run();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
