package dev.mclite.island.mc26;

import dev.mclite.island.bili.BiliApi;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

/** B 站浏览：搜索 / 收藏夹，点歌后加入播放队列并立即播放。 */
final class BiliBrowseScreen26 extends Screen {

    private static final int ROW_HEIGHT = 32;
    private static final int COVER_W = 44;
    private static final int COVER_H = 25;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int DIM = 0xFF93A0AD;
    private static final int MUTED = 0xFF6B7885;
    private static final int ACCENT = 0xFF8FD3FF;
    private static final int ERROR = 0xFFFF8A8A;

    private final McliteIsland26Client client;
    private final PlaybackService service;
    private final IslandConfig config;

    private EditBox searchBox;
    private volatile Row[] rows = new Row[0];
    private volatile String status = "输入关键字搜索，或打开收藏夹";
    private volatile boolean loading;
    private volatile String avatarUrl = "";
    private volatile String avatarName = "";
    private volatile boolean profileLoading;
    private long profileRetryAt;
    private boolean favoritesTab;
    private int scroll;

    private int panelX;
    private int panelY;
    private int listX;
    private int listTop;
    private int listW;
    private int listBottom;
    private UISurface26.Surface background;
    private final UiText26 statusCache = new UiText26();

    private record Row(BiliApi.Video video, String title, String meta) {
    }

    BiliBrowseScreen26(McliteIsland26Client client, PlaybackService service, IslandConfig config) {
        super(Component.literal("B 站"));
        this.client = client;
        this.service = service;
        this.config = config;
    }

    private static Component text(String value) {
        return Component.literal(value == null ? "" : value);
    }

    @Override
    protected void init() {
        panelX = Math.max(8, (width - UiPaint26.BILI_W) / 2);
        panelY = Math.max(8, (height - UiPaint26.BILI_H) / 2);
        background = UISurface26.get("bili", UiPaint26.BILI_W, UiPaint26.BILI_H,
                UiPaint26::bili);

        listX = panelX + UiPaint26.BILI_LIST_X + 10;
        listW = UiPaint26.BILI_LIST_W - 20;
        listTop = panelY + UiPaint26.BILI_LIST_Y + 14;
        listBottom = panelY + UiPaint26.BILI_LIST_Y + UiPaint26.BILI_LIST_H - 12;

        searchBox = new EditBox(minecraft.font, panelX + 36, panelY + 64, 496, 20,
                text("搜索视频"));
        searchBox.setMaxLength(60);
        searchBox.setBordered(false);
        searchBox.setTextColor(TEXT);
        searchBox.setHint(text("搜索 B 站视频…"));
        addRenderableWidget(searchBox);
        addRenderableWidget(UiButton26.primary("搜索", panelX + 542, panelY + 62, 54, 24,
                this::search));
        addRenderableWidget(UiButton26.ghost("收藏夹", panelX + 602, panelY + 62, 70, 24,
                this::loadFavorites));
        addRenderableWidget(UiButton26.ghost(
                client.biliCookies().loggedIn() ? "重新登录" : "登录",
                panelX + 582, panelY + 18, 54, 24,
                () -> Minecraft.getInstance().setScreen(
                        new BiliLoginScreen26(client, () -> status = "登录成功", this))));
        addRenderableWidget(UiButton26.ghost("返回", panelX + 644, panelY + 18, 54, 24,
                this::onClose));

        status = client.biliCookies().loggedIn() ? "已登录，可搜索或打开收藏夹" : "未登录，在线播放仍可用";
        requestProfile();
    }

    /** 头像/昵称单独拉一次，失败就继续显示“已登录”，不阻塞界面。 */
    private void requestProfile() {
        if (profileLoading || !client.biliCookies().loggedIn()
                || System.currentTimeMillis() < profileRetryAt) {
            return;
        }
        profileLoading = true;
        new Thread(() -> {
            try {
                BiliApi.Profile profile = client.bili().profile();
                if (!profile.face().isBlank()) {
                    avatarUrl = profile.face();
                }
                if (!profile.name().isBlank()) {
                    avatarName = profile.name();
                }
            } catch (Exception ignored) {
                // 头像只是装饰；登录态刚写入时 cookie 可能还没同步，稍后重试
                profileRetryAt = System.currentTimeMillis() + 5_000L;
            } finally {
                profileLoading = false;
            }
        }, "mclite-island-bili-profile").start();
    }

    private void search() {
        String keyword = searchBox == null ? "" : searchBox.getValue().trim();
        if (keyword.isEmpty()) {
            status = "先输入关键字";
            return;
        }
        loading = true;
        status = "搜索中…";
        new Thread(() -> {
            try {
                List<BiliApi.Video> videos = client.bili().search(keyword, 1);
                Minecraft.getInstance().execute(() -> {
                    setLoaded(videos);
                    favoritesTab = false;
                    loading = false;
                    status = "找到 " + videos.size() + " 个视频";
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> {
                    loading = false;
                    status = "搜索失败: " + ex.getMessage();
                });
            }
        }, "mclite-island-bili-search").start();
    }

    private void loadFavorites() {
        if (!client.biliCookies().loggedIn()) {
            status = "先登录 B 站";
            return;
        }
        loading = true;
        status = "读取收藏夹…";
        new Thread(() -> {
            try {
                List<BiliApi.Video> all = new ArrayList<>();
                for (BiliApi.FavFolder folder : client.bili().favFolders()) {
                    all.addAll(client.bili().favItems(folder.id(), 1));
                    if (all.size() >= 60) {
                        break;
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    setLoaded(all);
                    favoritesTab = true;
                    loading = false;
                    status = "收藏夹共 " + all.size() + " 首";
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> {
                    loading = false;
                    status = "收藏夹读取失败: " + ex.getMessage();
                });
            }
        }, "mclite-island-bili-fav").start();
    }

    private void setLoaded(List<BiliApi.Video> videos) {
        Row[] built = new Row[videos.size()];
        Font font = minecraft.font;
        int titleWidth = listW - 62 - 18;
        for (int i = 0; i < videos.size(); i++) {
            BiliApi.Video video = videos.get(i);
            String author = video.author() == null ? "" : video.author();
            String meta = author + " · " + formatDuration(video.durationSeconds());
            built[i] = new Row(video,
                    UiText26.fit(font, video.title(), titleWidth),
                    UiText26.fit(font, meta, listW - 62 - 18));
        }
        rows = built;
        scroll = 0;
    }

    private void play(BiliApi.Video video) {
        status = "解析 " + video.bvid() + " …";
        new Thread(() -> {
            try {
                BiliApi.View view = client.bili().view(video.bvid());
                BiliApi.AudioStream stream = client.bili().playUrl(view.bvid(), view.cid(),
                        config.biliQuality);
                Track track = Track.remote(view.bvid(), view.cid(), stream.audioId(),
                        stream.url(), view.title(), view.owner(),
                        view.durationSeconds() * 1000L, video.cover());
                Minecraft.getInstance().execute(() -> {
                    service.playlist().replace(List.of(track));
                    service.playAt(0);
                    status = "正在播放: " + view.title();
                    // 如果用户已经切到别的界面，不要把新界面一起关掉
                    if (Minecraft.getInstance().screen == BiliBrowseScreen26.this) {
                        onClose();
                    }
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> status = "播放失败: " + ex.getMessage());
            }
        }, "mclite-island-bili-play").start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        Font font = minecraft.font;
        if (background != null) {
            UISurface26.draw(extractor, background, panelX, panelY);
        } else {
            UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY,
                    UiPaint26.BILI_W, UiPaint26.BILI_H, 20);
            UiDraw26.panel(extractor, UiDraw26.DARK, panelX + UiPaint26.BILI_LIST_X,
                    panelY + UiPaint26.BILI_LIST_Y, UiPaint26.BILI_LIST_W,
                    UiPaint26.BILI_LIST_H, 14);
        }

        drawScaledText(extractor, font, "B 站音乐", panelX + 44, panelY + 16, 1.25f, TEXT);
        extractor.text(font, "搜索 · 收藏夹 · 点歌", panelX + 44, panelY + 37, DIM, false);
        boolean loggedIn = client.biliCookies().loggedIn();
        if (loggedIn && !avatarUrl.isBlank()) {
            BiliImage26.Entry avatar = BiliImage26.get(avatarUrl, 22, 22, true);
            int avatarX = panelX + 494;
            int avatarY = panelY + 16;
            extractor.fill(avatarX, avatarY, avatarX + 22, avatarY + 22, 0xFF1B2632);
            BiliImage26.draw(extractor, avatar, avatarX, avatarY, 22, 22);
            if (avatar == null) {
                String initial = avatarName.isBlank() ? "B" : avatarName.substring(0, 1);
                extractor.text(font, initial, avatarX + 8, avatarY + 7, ACCENT, false);
            }
            String name = UiText26.fit(font, avatarName.isBlank() ? "已登录" : avatarName, 54);
            extractor.text(font, name, avatarX + 28, avatarY + 7, ACCENT, false);
        } else {
            String account = loggedIn ? "已登录" : "未登录";
            UiDraw26.textRight(extractor, font, account, panelX + 566, panelY + 22,
                    loggedIn ? ACCENT : DIM);
        }
        if (loggedIn && avatarUrl.isBlank()) {
            requestProfile();
        }

        drawRows(extractor, font, mouseX, mouseY);
        if (loading) {
            String tip = "加载中…";
            UiDraw26.textCentered(extractor, font, tip,
                    panelX + UiPaint26.BILI_W / 2, panelY + UiPaint26.BILI_H / 2 - 24, DIM);
        }

        String message = status;
        int color = message.startsWith("搜索失败") || message.startsWith("收藏夹读取失败")
                || message.startsWith("播放失败") ? ERROR : DIM;
        statusCache.set(font, message, 640);
        extractor.text(font, statusCache.text(), panelX + 24, panelY + 414, color, false);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private void drawRows(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        Row[] list = rows;
        if (list.length == 0) {
            if (!loading) {
                String empty = favoritesTab ? "收藏夹为空" : "没有结果";
                UiDraw26.textCentered(extractor, font, empty,
                        panelX + UiPaint26.BILI_W / 2, listTop + 40, MUTED);
            }
            return;
        }

        int visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, list.length - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        Track current = service.playlist().current();

        for (int row = 0; row < visible; row++) {
            int index = scroll + row;
            if (index >= list.length) {
                break;
            }
            Row item = list[index];
            int y = listTop + row * ROW_HEIGHT;
            boolean hover = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 2;
            int coverY = y + (ROW_HEIGHT - COVER_H) / 2;
            int coverX = listX + 6;
            boolean playing = current != null && current.bvid() != null
                    && current.bvid().equals(item.video().bvid());
            if (hover || playing) {
                UiDraw26.panel(extractor, playing ? UiDraw26.SOFT : UiDraw26.HOVER,
                        listX + 2, y - 1, listW - 8, ROW_HEIGHT - 2);
            }
            if (playing) {
                extractor.fill(listX + 1, y + 5, listX + 3, y + ROW_HEIGHT - 7, 0xFF6CC6FF);
            }
            BiliImage26.Entry cover = BiliImage26.get(item.video().cover(),
                    COVER_W, COVER_H, false);
            if (cover == null) {
                extractor.fill(coverX, coverY, coverX + COVER_W, coverY + COVER_H, 0xFF1C2632);
                extractor.fill(coverX + 16, coverY + 8, coverX + 28, coverY + 9, 0xFF33465A);
            } else {
                BiliImage26.draw(extractor, cover, coverX, coverY, COVER_W, COVER_H);
            }
            int textX = coverX + COVER_W + 10;
            extractor.text(font, item.title(), textX, y + 4,
                    playing ? ACCENT : TEXT, false);
            extractor.text(font, item.meta(), textX, y + 17, DIM, false);
        }

        if (maxScroll > 0) {
            int trackX = listX + listW - 3;
            int viewH = listBottom - listTop;
            extractor.fill(trackX, listTop, trackX + 3, listBottom, 0x14FFFFFF);
            int thumbH = Math.max(18, viewH * visible / list.length);
            int thumbY = listTop + (int) ((viewH - thumbH) * (scroll / (float) maxScroll));
            extractor.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0x55FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= listX && event.x() < listX + listW
                && event.y() >= listTop && event.y() < listBottom) {
            int index = scroll + (int) ((event.y() - listTop) / ROW_HEIGHT);
            Row[] list = rows;
            if (index >= 0 && index < list.length) {
                play(list[index].video());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox != null && searchBox.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            search();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listTop && mouseY <= listBottom) {
            scroll -= (int) Math.signum(deltaY) * 3;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private static String formatDuration(long seconds) {
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
    public boolean isPauseScreen() {
        return false;
    }
}
