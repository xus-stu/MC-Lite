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
 * 26.x 播放器：900x560 的「队列 + 正在播放 + 底部传输栏」布局。
 *
 * <p>静态底图由 {@link UISurface26} 烘焙；每帧只画队列行、封面 blit、歌词、
 * 进度和传输按钮。封面/头像走 {@link BiliImage26} 的异步缓存，本地曲目用
 * 渐变占位，不做任何逐帧缩放或离屏画布。</p>
 */
final class IslandPlayerScreen26 extends Screen {

    private static final int ROW_HEIGHT = 34;
    private static final int ROW_COVER = 26;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int DIM = 0xFF93A0AD;
    private static final int MUTED = 0xFF6B7885;
    private static final int ACCENT = 0xFF8FD3FF;
    private static final int ERROR = 0xFFFF8A8A;
    private static final String HINT = "M 关闭 · 拖动进度条快进 · 滚轮翻队列";

    private final PlaybackService service;
    private final IslandConfig config;
    private final Runnable saveConfig;
    private final McliteIsland26Client client;

    private List<Track> tracks = List.of();
    private String[] rowTitle = new String[0];
    private String[] rowArtist = new String[0];
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
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private UISurface26.Surface background;

    private UiIconButton26 playButton;
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

        cardX = panelX + UiPaint26.PLAYER_LEFT_X;
        cardY = panelY + UiPaint26.PLAYER_LEFT_Y;
        cardW = UiPaint26.PLAYER_LEFT_W;
        cardH = UiPaint26.PLAYER_LEFT_H;
        listX = cardX + 10;
        listW = cardW - 20;
        listTop = cardY + UiPaint26.PLAYER_QUEUE_HEADER + 6;
        listBottom = cardY + cardH - 10;

        if (service.playlist().isEmpty()) {
            service.reloadLibrary();
        }
        tracks = service.playlist().tracks();
        rebuildRowText();
        int currentIndex = service.playlist().index();
        scroll = Math.max(0, currentIndex - 4);

        int headerY = panelY + 15;
        addRenderableWidget(UiButton26.ghost("B站", panelX + 460, headerY, 46, 26,
                () -> Minecraft.getInstance().setScreen(
                        new BiliBrowseScreen26(client, service, config))));
        addRenderableWidget(UiButton26.ghost("设置", panelX + 512, headerY, 48, 26,
                () -> Minecraft.getInstance().setScreen(
                        new IslandConfigScreen26(client, service, config, saveConfig))));
        addRenderableWidget(UiButton26.ghost("导入歌词", panelX + 566, headerY, 52, 26,
                () -> Minecraft.getInstance().setScreen(
                        new LrcImportScreen26(service, saveConfig))));
        addRenderableWidget(UiButton26.ghost("打开目录", panelX + 624, headerY, 52, 26,
                () -> openFolder(service.musicDir())));
        addRenderableWidget(UiButton26.ghost("关闭", panelX + 682, headerY, 42, 26,
                this::onClose));
        addRenderableWidget(UiButton26.ghost("刷新", cardX + cardW - 54, cardY + 7, 44, 22,
                this::rescan));

        int barY = panelY + UiPaint26.PLAYER_BAR_Y;
        modeButton = addRenderableWidget(UiButton26.ghost(modeLabel(config.playMode),
                panelX + 32, barY + 9, 96, 34, () -> {
                    service.cycleMode();
                    saveConfig.run();
                    syncTransport();
                }));

        int groupX = panelX + 308;
        addRenderableWidget(new UiIconButton26(UiIconButton26.Icon.PREV,
                groupX, barY + 9, 34, 34, false, () -> {
                    service.previous();
                    syncTransport();
                }));
        playButton = addRenderableWidget(new UiIconButton26(UiIconButton26.Icon.PLAY,
                groupX + 42, barY + 6, 40, 40, true, () -> {
                    service.togglePlay();
                    syncTransport();
                }));
        addRenderableWidget(new UiIconButton26(UiIconButton26.Icon.NEXT,
                groupX + 90, barY + 9, 34, 34, false, () -> {
                    service.next(true);
                    syncTransport();
                }));

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
        if (playButton != null) {
            playButton.setIcon(snapshot.playing() && !snapshot.paused()
                    ? UiIconButton26.Icon.PAUSE : UiIconButton26.Icon.PLAY);
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
            if (rowTitle.length != tracks.size()) {
                rebuildRowText();
            }
            return;
        }
        tracks = latest;
        rebuildRowText();
    }

    private void rebuildRowText() {
        countText = tracks.size() + " 首";
        rowTitle = new String[tracks.size()];
        rowArtist = new String[tracks.size()];
        Font font = minecraft != null ? minecraft.font : null;
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            if (font == null) {
                rowTitle[i] = track.title();
                rowArtist[i] = track.artist();
                continue;
            }
            rowTitle[i] = UiText26.fit(font, track.title(), listW - 58);
            rowArtist[i] = UiText26.fit(font, track.artist().isBlank()
                    ? (track.isRemote() ? "B 站" : "本地文件") : track.artist(), listW - 58);
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
                panelX + 40, panelY + 10, 1.3f, TEXT);
        extractor.text(font, "本地音乐 · B站 · 歌词", panelX + 40, panelY + 33, DIM, false);
    }

    private void drawQueue(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY,
                           IslandSnapshot snapshot) {
        extractor.text(font, "播放队列", cardX + 14, cardY + 12, TEXT, false);
        UiDraw26.textRight(extractor, font, countText, cardX + cardW - 64, cardY + 12, MUTED);

        if (tracks.isEmpty()) {
            drawQueueEmpty(extractor, font);
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
                        listX, rowY - 1, listW, ROW_HEIGHT - 2, 8);
            }
            if (playing) {
                extractor.fill(cardX + 2, rowY + 5, cardX + 4, rowY + ROW_HEIGHT - 7,
                        0xFF6CC6FF);
            }
            int coverX = listX + 8;
            int coverY = rowY + (ROW_HEIGHT - ROW_COVER) / 2;
            drawCover(extractor, font, tracks.get(index).cover(), coverX, coverY, ROW_COVER);
            int textX = coverX + ROW_COVER + 10;
            int color = playing ? ACCENT : hover ? TEXT : 0xFFD7DEE6;
            extractor.text(font, rowTitle[index], textX, rowY + 3, color, false);
            extractor.text(font, rowArtist[index], textX, rowY + 17,
                    playing ? 0xFFB9E2FF : MUTED, false);
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

    private void drawQueueEmpty(GuiGraphicsExtractor extractor, Font font) {
        drawScaledText(extractor, font, "♪", cardX + cardW / 2 - 14, cardY + 128,
                2.6f, 0x30FFFFFF);
        UiDraw26.textCentered(extractor, font, "音乐目录为空",
                cardX + cardW / 2, cardY + 184, DIM);
        UiDraw26.textCentered(extractor, font, "点右上「打开目录」放入 mp3 / wav / flac",
                cardX + cardW / 2, cardY + 202, MUTED);
    }

    /** 30/148 的封面：有 B 站封面就异步加载，本地曲目用九宫格软底 + 音符。 */
    private void drawCover(GuiGraphicsExtractor extractor, Font font, String cover,
                           int x, int y, int size) {
        if (cover != null && !cover.isBlank()) {
            BiliImage26.Entry entry = BiliImage26.get(cover, size, size, false);
            if (entry != null) {
                BiliImage26.draw(extractor, entry, x, y, size, size);
                return;
            }
        }
        UiDraw26.panel(extractor, UiDraw26.SOFT, x, y, size, size,
                Math.min(18, Math.max(6, size / 8)));
        drawScaledText(extractor, font, "♪", x + size / 2f - size * 0.17f,
                y + size / 2f - size * 0.27f, Math.max(0.7f, size / 30f), 0xCCFFFFFF);
    }

    private void drawNowPlaying(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY,
                                IslandSnapshot snapshot) {
        int rightX = panelX + UiPaint26.PLAYER_RIGHT_X;
        int rightY = panelY + UiPaint26.PLAYER_RIGHT_Y;
        int rightW = UiPaint26.PLAYER_RIGHT_W;
        int artX = panelX + UiPaint26.PLAYER_ART_X;
        int artY = panelY + UiPaint26.PLAYER_ART_Y;
        int artSize = UiPaint26.PLAYER_ART_SIZE;
        Track current = service.playlist().current();

        drawCover(extractor, font, current == null ? "" : current.cover(),
                artX, artY, artSize);

        int textX = artX + artSize + 16;
        int textW = rightX + rightW - 18 - textX;
        if (snapshot.hasTrack()) {
            nowTitle.set(font, snapshot.title(), Math.round(textW / 1.2f));
            drawScaledText(extractor, font, nowTitle.text(), textX, artY + 6, 1.2f, TEXT);
            nowArtist.set(font, snapshot.artist().isBlank() ? "未知艺术家" : snapshot.artist(),
                    textW);
            extractor.text(font, nowArtist.text(), textX, artY + 30, DIM, false);
            String source = current != null && current.isRemote() ? "B 站音频" : "本地文件";
            extractor.text(font, source, textX, artY + 48, MUTED, false);
        } else {
            drawScaledText(extractor, font, "未在播放", textX, artY + 6, 1.2f, TEXT);
            extractor.text(font, "从左侧队列选一首开始", textX, artY + 30, DIM, false);
        }

        extractor.text(font, "歌词", rightX + 18, rightY + 154, MUTED, false);
        int centerX = rightX + rightW / 2;
        if (snapshot.hasTrack()) {
            String lyric = snapshot.lyric() == null ? "" : snapshot.lyric();
            boolean hasLyric = !lyric.isBlank();
            nowLyric.set(font, hasLyric ? lyric : "暂无歌词", rightW - 36);
            int lyricWidth = Math.round(nowLyric.width() * 1.1f);
            drawScaledText(extractor, font, nowLyric.text(), centerX - lyricWidth / 2,
                    rightY + 170, 1.1f, hasLyric ? 0xFFEAF6FF : MUTED);
            String next = snapshot.nextLyric() == null ? "" : snapshot.nextLyric();
            if (!next.isBlank()) {
                nextLyric.set(font, next, rightW - 36);
                int nextWidth = nextLyric.width();
                extractor.text(font, nextLyric.text(), centerX - nextWidth / 2,
                        rightY + 196, DIM, false);
            }
        } else {
            UiDraw26.textCentered(extractor, font, "在左侧选一首歌，歌词会显示在这里",
                    centerX, rightY + 178, MUTED);
        }

        long duration = snapshot.durationMs();
        long position = seeking ? seekTargetMs : snapshot.positionMs();
        float fraction = duration > 0L
                ? Math.max(0f, Math.min(1f, position / (float) duration)) : 0f;
        int progressX = rightX + 18;
        int progressY = rightY + 240;
        int progressW = rightW - 36;
        progress.bounds(progressX, progressY, progressW, 16);
        progress.render(extractor, fraction, progress.contains(mouseX, mouseY));
        updateTimeLabels(position, duration);
        extractor.text(font, positionText, progressX, progressY + 22, DIM, false);
        UiDraw26.textRight(extractor, font, durationText,
                progressX + progressW, progressY + 22, DIM);

        extractor.text(font, "音量", rightX + 18, rightY + 286, MUTED, false);
        UiDraw26.textRight(extractor, font, volumeText,
                rightX + rightW - 18, rightY + 286, DIM);
        volumeSlider.bounds(rightX + 70, rightY + 288, rightW - 116, 12);
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
        statusLine.set(font, shown, 360);
        UiDraw26.textRight(extractor, font, statusLine.text(),
                panelX + UiPaint26.PLAYER_BAR_X + UiPaint26.PLAYER_BAR_W - 14,
                panelY + UiPaint26.PLAYER_BAR_Y + 23, color);
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
                                       float x, float y, float scale, int color) {
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
