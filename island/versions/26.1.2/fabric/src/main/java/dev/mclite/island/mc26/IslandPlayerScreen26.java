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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 26.x 播放器：700x450 的「封面模糊背景 + 队列 + 正在播放 + 悬浮传输栏」。
 *
 * <p>背景和卡片是 {@link PlayerBackdrop26} 按曲目烘焙的一张纹理；每帧只做
 * 1 次 blit、队列行/歌词/进度条和文字。按钮全部自绘，不再用大号文字胶囊，
 * 避免 720p GUI 下溢出和“上世纪控件”的观感。</p>
 */
final class IslandPlayerScreen26 extends Screen {

    private static final int ACCENT = 0xFFFF7FB8;
    private static final int ACCENT_DIM = 0x66FF7FB8;
    private static final int TEXT = 0xFFF5F7FA;
    private static final int DIM = 0xFFA2ACB8;
    private static final int MUTED = 0xFF6E7885;
    private static final int ERROR = 0xFFFF8585;
    private static final String HINT = "M 关闭 · 拖动进度条 · 滚轮翻队列";

    private static final int ROW_HEIGHT = 38;
    private static final int ROW_COVER = 30;

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
    private String lastError = "";
    private String errorLine = "";

    private int panelX;
    private int panelY;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private int listX;
    private int listW;
    private int listTop;
    private int listBottom;

    private UiIconButton26 playButton;
    private final List<Nav> navs = new ArrayList<>();
    private int refreshX;
    private int refreshY;

    private boolean seeking;
    private boolean volumeDragging;
    private long seekTargetMs;

    private final UiText26 nowTitle = new UiText26();
    private final UiText26 nowArtist = new UiText26();
    private final UiText26 nowLyric = new UiText26();
    private final UiText26 nextLyric = new UiText26();
    private final UiText26 statusLine = new UiText26();
    private final UiText26 rowCache = new UiText26();

    private int shownVolume = -1;
    private String volumeText = "70%";
    private long shownPositionSecond = Long.MIN_VALUE;
    private long shownDurationSecond = Long.MIN_VALUE;
    private String positionText = "0:00";
    private String durationText = "--:--";

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
        panelX = Math.max(4, (width - UiPaint26.PLAYER_W) / 2);
        panelY = Math.max(4, (height - UiPaint26.PLAYER_H) / 2);
        seeking = false;
        volumeDragging = false;

        cardX = panelX + UiPaint26.PLAYER_LEFT_X;
        cardY = panelY + UiPaint26.PLAYER_LEFT_Y;
        cardW = UiPaint26.PLAYER_LEFT_W;
        cardH = UiPaint26.PLAYER_LEFT_H;
        listX = cardX + 10;
        listW = cardW - 20;
        listTop = cardY + UiPaint26.PLAYER_QUEUE_HEADER + 8;
        listBottom = cardY + cardH - 10;

        if (service.playlist().isEmpty()) {
            service.reloadLibrary();
        }
        tracks = service.playlist().tracks();
        rebuildRowText();
        int currentIndex = service.playlist().index();
        scroll = Math.max(0, currentIndex - 2);

        navs.clear();
        String[] labels = {"B站", "设置", "导入歌词", "打开目录", "关闭"};
        Runnable[] actions = {
                () -> Minecraft.getInstance().setScreen(
                        new BiliBrowseScreen26(client, service, config)),
                () -> Minecraft.getInstance().setScreen(
                        new IslandConfigScreen26(client, service, config, saveConfig)),
                () -> Minecraft.getInstance().setScreen(
                        new LrcImportScreen26(service, saveConfig)),
                () -> openFolder(service.musicDir()),
                this::onClose
        };
        int cursor = panelX + UiPaint26.PLAYER_W - 24;
        for (int i = labels.length - 1; i >= 0; i--) {
            int w = font.width(labels[i]);
            cursor -= w;
            navs.add(0, new Nav(labels[i], cursor, w, panelY + 20, 18, actions[i]));
            cursor -= 16;
        }

        refreshX = cardX + cardW - 54;
        refreshY = cardY + 9;

        int barY = panelY + UiPaint26.PLAYER_BAR_Y;
        int groupX = panelX + 288;
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

    private record Nav(String label, int x, int y, int w, int h, Runnable action) {
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
    }

    private void updateVolumeText() {
        int percent = Math.round(config.volume * 100f);
        if (percent != shownVolume) {
            shownVolume = percent;
            volumeText = percent + "%";
        }
    }

    private void refreshTracks() {
        List<Track> latest = service.playlist().tracks();
        if (latest.equals(tracks) && rowTitle.length == tracks.size()) {
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
            rowTitle[i] = UiText26.fit(font, track.title(), listW - 62);
            rowArtist[i] = UiText26.fit(font, track.artist().isBlank()
                    ? (track.isRemote() ? "B 站" : "本地文件") : track.artist(), listW - 62);
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
            status = "已打开目录";
        } catch (Exception failure) {
            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(path.toString());
            } catch (Exception ignored) {
                // 剪贴板也不可用时只提示
            }
            status = "打不开目录（路径已复制）";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        Font font = minecraft.font;
        IslandSnapshot snapshot = service.snapshot();
        Track current = service.playlist().current();
        String cover = current == null ? "" : current.cover();
        PlayerBackdrop26.Entry backdrop = PlayerBackdrop26.get(cover);
        if (backdrop != null) {
            PlayerBackdrop26.draw(extractor, backdrop, panelX, panelY);
        } else {
            UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY,
                    UiPaint26.PLAYER_W, UiPaint26.PLAYER_H, 22);
        }

        updateVolumeText();
        drawHeader(extractor, font, mouseX, mouseY);
        drawQueue(extractor, font, mouseX, mouseY, snapshot);
        drawNowPlaying(extractor, font, mouseX, mouseY, snapshot, current);
        drawFooter(extractor, font, snapshot, mouseX, mouseY);

        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        drawScaledText(extractor, font, "MCLite Island", panelX + 28, panelY + 12,
                1.35f, TEXT);
        extractor.text(font, "本地音乐 · B站 · 歌词", panelX + 28, panelY + 35, MUTED, false);
        for (Nav nav : navs) {
            boolean hover = mouseX >= nav.x - 4 && mouseX <= nav.x + nav.w + 4
                    && mouseY >= nav.y - 4 && mouseY <= nav.y + nav.h + 4;
            extractor.text(font, nav.label, nav.x, nav.y, hover ? ACCENT : DIM, false);
            if (hover) {
                extractor.fill(nav.x, nav.y + 13, nav.x + nav.w, nav.y + 14, ACCENT);
            }
        }
    }

    private void drawQueue(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY,
                           IslandSnapshot snapshot) {
        extractor.text(font, "播放队列", cardX + 16, cardY + 11, DIM, false);
        String refresh = "刷新";
        boolean refreshHover = mouseX >= refreshX && mouseX <= refreshX + 40
                && mouseY >= refreshY - 3 && mouseY <= refreshY + 17;
        extractor.text(font, refresh, refreshX, refreshY, refreshHover ? ACCENT : MUTED, false);

        UiDraw26.textRight(extractor, font, countText, refreshX - 12,
                cardY + 11, MUTED);

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
            if (playing) {
                extractor.fill(listX, rowY + 3, listX + 2, rowY + ROW_HEIGHT - 5, ACCENT);
                UiDraw26.panel(extractor, UiDraw26.SOFT, listX + 2, rowY,
                        listW - 2, ROW_HEIGHT - 2, 8);
            } else if (hover) {
                UiDraw26.panel(extractor, UiDraw26.SOFT, listX, rowY,
                        listW, ROW_HEIGHT - 2, 8);
            }
            int coverX = listX + 10;
            int coverY = rowY + (ROW_HEIGHT - ROW_COVER) / 2;
            drawCover(extractor, font, tracks.get(index).cover(), coverX, coverY, ROW_COVER);
            int textX = coverX + ROW_COVER + 10;
            int color = playing ? ACCENT : hover ? TEXT : 0xFFE8EDF3;
            extractor.text(font, rowTitle[index], textX, rowY + 5, color, false);
            extractor.text(font, rowArtist[index], textX, rowY + 19,
                    playing ? 0xD9FF9CCB : MUTED, false);
        }

        if (maxScroll > 0) {
            int trackX = listX + listW - 2;
            int viewH = listBottom - listTop;
            int thumbH = Math.max(18, viewH * visible / tracks.size());
            int thumbY = listTop + (int) ((viewH - thumbH) * (scroll / (float) maxScroll));
            extractor.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x55FFFFFF);
        }
    }

    private void drawQueueEmpty(GuiGraphicsExtractor extractor, Font font) {
        UiDraw26.textCentered(extractor, font, "音乐目录为空", listX + listW / 2, listTop + 60, DIM);
        UiDraw26.textCentered(extractor, font, "点右上「打开目录」放入 mp3 / wav / flac",
                listX + listW / 2, listTop + 78, MUTED);
    }

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
                Math.min(16, Math.max(5, size / 8)));
        drawScaledText(extractor, font, "♪", x + size / 2f - size * 0.17f,
                y + size / 2f - size * 0.30f, Math.max(0.7f, size / 34f), 0x99FFFFFF);
    }

    private void drawNowPlaying(GuiGraphicsExtractor extractor, Font font, int mouseX,
                                int mouseY, IslandSnapshot snapshot, Track current) {
        int rightX = panelX + UiPaint26.PLAYER_RIGHT_X;
        int rightY = panelY + UiPaint26.PLAYER_RIGHT_Y;
        int artX = panelX + UiPaint26.PLAYER_ART_X;
        int artY = panelY + UiPaint26.PLAYER_ART_Y;
        int artSize = UiPaint26.PLAYER_ART_SIZE;

        drawCover(extractor, font, current == null ? "" : current.cover(), artX, artY, artSize);

        int textX = artX + artSize + 16;
        int textW = rightX + UiPaint26.PLAYER_RIGHT_W - 16 - textX;
        if (snapshot.hasTrack()) {
            nowTitle.set(font, snapshot.title(), Math.round(textW / 1.3f));
            drawScaledText(extractor, font, nowTitle.text(), textX, artY + 4, 1.3f, TEXT);
            nowArtist.set(font, snapshot.artist().isBlank() ? "未知艺术家" : snapshot.artist(),
                    textW);
            extractor.text(font, nowArtist.text(), textX, artY + 28, DIM, false);
            String source = current != null && current.isRemote() ? "B 站音频" : "本地文件";
            extractor.text(font, source, textX, artY + 46, MUTED, false);
        } else {
            drawScaledText(extractor, font, "未在播放", textX, artY + 4, 1.3f, TEXT);
            extractor.text(font, "从左侧队列选一首开始", textX, artY + 28, DIM, false);
        }

        extractor.text(font, "歌词", rightX + 16, rightY + 154, MUTED, false);
        int centerX = rightX + UiPaint26.PLAYER_RIGHT_W / 2;
        if (snapshot.hasTrack()) {
            String lyric = snapshot.lyric() == null ? "" : snapshot.lyric();
            boolean hasLyric = !lyric.isBlank();
            nowLyric.set(font, hasLyric ? lyric : "暂无歌词", UiPaint26.PLAYER_RIGHT_W - 36);
            int lyricWidth = Math.round(nowLyric.width() * 1.18f);
            drawScaledText(extractor, font, nowLyric.text(), centerX - lyricWidth / 2f,
                    rightY + 170, 1.18f, hasLyric ? 0xFFFFE3F0 : MUTED);
            String next = snapshot.nextLyric() == null ? "" : snapshot.nextLyric();
            if (!next.isBlank()) {
                nextLyric.set(font, next, UiPaint26.PLAYER_RIGHT_W - 36);
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
        int progressX = rightX + 16;
        int progressY = rightY + 232;
        int progressW = UiPaint26.PLAYER_RIGHT_W - 32;
        drawSlider(extractor, progressX, progressY, progressW, fraction,
                mouseX >= progressX - 4 && mouseX <= progressX + progressW + 4
                        && mouseY >= progressY - 8 && mouseY <= progressY + 12);
        updateTimeLabels(position, duration);
        extractor.text(font, positionText, progressX, progressY + 16, MUTED, false);
        UiDraw26.textRight(extractor, font, durationText, progressX + progressW,
                progressY + 16, MUTED);

        extractor.text(font, "音量", rightX + 16, rightY + 276, MUTED, false);
        UiDraw26.textRight(extractor, font, volumeText, rightX + UiPaint26.PLAYER_RIGHT_W - 16,
                rightY + 276, DIM);
        int volumeX = rightX + 52;
        int volumeW = UiPaint26.PLAYER_RIGHT_W - 100;
        drawSlider(extractor, volumeX, rightY + 280, volumeW, config.volume,
                mouseX >= volumeX - 4 && mouseX <= volumeX + volumeW + 4
                        && mouseY >= rightY + 272 && mouseY <= rightY + 292);
    }

    private void drawSlider(GuiGraphicsExtractor extractor, int x, int y, int w, float value,
                            boolean hot) {
        extractor.fill(x, y, x + w, y + 3, 0x33FFFFFF);
        int filled = x + Math.round(w * Math.max(0f, Math.min(1f, value)));
        extractor.fill(x, y, filled, y + 3, ACCENT);
        if (hot) {
            extractor.fill(filled - 2, y - 2, filled + 2, y + 5, 0xFFFFE3F0);
        }
    }

    private void drawFooter(GuiGraphicsExtractor extractor, Font font, IslandSnapshot snapshot,
                            int mouseX, int mouseY) {
        boolean modeHover = mouseX >= panelX + 34 && mouseX <= panelX + 130
                && mouseY >= panelY + 402 && mouseY <= panelY + 432;
        if (modeHover) {
            extractor.fill(panelX + 34, panelY + 428, panelX + 130, panelY + 430, ACCENT_DIM);
        }
        extractor.text(font, modeLabel(config.playMode), panelX + 40, panelY + 412,
                modeHover ? ACCENT : DIM, false);

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
        statusLine.set(font, shown, 230);
        UiDraw26.textRight(extractor, font, statusLine.text(),
                panelX + UiPaint26.PLAYER_W - 28, panelY + 412, color);
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
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
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
            for (Nav nav : navs) {
                if (event.x() >= nav.x - 4 && event.x() <= nav.x + nav.w + 4
                        && event.y() >= nav.y - 4 && event.y() <= nav.y + nav.h + 4) {
                    nav.action.run();
                    return true;
                }
            }
            if (event.x() >= refreshX && event.x() <= refreshX + 40
                    && event.y() >= refreshY - 3 && event.y() <= refreshY + 17) {
                service.reloadLibrary();
                refreshTracks();
                status = "已重新扫描，共 " + tracks.size() + " 首";
                return true;
            }
            if (event.x() >= panelX + 34 && event.x() <= panelX + 130
                    && event.y() >= panelY + 402 && event.y() <= panelY + 432) {
                service.cycleMode();
                saveConfig.run();
                syncTransport();
                return true;
            }

            IslandSnapshot snapshot = service.snapshot();
            long duration = snapshot.durationMs();
            int progressX = panelX + UiPaint26.PLAYER_ART_X + UiPaint26.PLAYER_ART_SIZE + 16;
            int progressY = panelY + UiPaint26.PLAYER_RIGHT_Y + 232;
            int progressW = UiPaint26.PLAYER_RIGHT_W - 32;
            if (duration > 0L && event.x() >= progressX - 4
                    && event.x() <= progressX + progressW + 4
                    && event.y() >= progressY - 8 && event.y() <= progressY + 12) {
                seeking = true;
                updateSeek(event.x(), progressX, progressW, duration);
                return true;
            }
            int volumeX = panelX + UiPaint26.PLAYER_RIGHT_X + 52;
            int volumeY = panelY + UiPaint26.PLAYER_RIGHT_Y + 272;
            int volumeW = UiPaint26.PLAYER_RIGHT_W - 100;
            if (event.x() >= volumeX - 4 && event.x() <= volumeX + volumeW + 4
                    && event.y() >= volumeY && event.y() <= volumeY + 20) {
                volumeDragging = true;
                updateVolume(event.x(), volumeX, volumeW);
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

    private void updateSeek(double mouseX, int sliderX, int sliderW, long duration) {
        float fraction = sliderW <= 0 ? 0f
                : (float) ((mouseX - sliderX) / (double) sliderW);
        seekTargetMs = (long) (Math.max(0f, Math.min(1f, fraction)) * duration);
    }

    private void updateVolume(double mouseX, int sliderX, int sliderW) {
        float fraction = sliderW <= 0 ? 0f
                : (float) ((mouseX - sliderX) / (double) sliderW);
        service.setVolume(Math.max(0f, Math.min(1f, fraction)));
        updateVolumeText();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (seeking) {
            updateSeek(event.x(),
                    panelX + UiPaint26.PLAYER_ART_X + UiPaint26.PLAYER_ART_SIZE + 16,
                    UiPaint26.PLAYER_RIGHT_W - 32, service.snapshot().durationMs());
            return true;
        }
        if (volumeDragging) {
            updateVolume(event.x(),
                    panelX + UiPaint26.PLAYER_RIGHT_X + 52,
                    UiPaint26.PLAYER_RIGHT_W - 100);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (seeking) {
            service.seek(seekTargetMs);
            seeking = false;
            syncTransport();
            return true;
        }
        if (volumeDragging) {
            volumeDragging = false;
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
