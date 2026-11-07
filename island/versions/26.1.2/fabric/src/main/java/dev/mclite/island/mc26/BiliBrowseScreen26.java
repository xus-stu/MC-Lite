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

/**
 * B 站浏览：透明背景 + 队列式列表，搜索/收藏夹/点歌。
 *
 * <p>不再画 720x460 的大卡片和文字胶囊按钮；标题、头像、搜索框、列表
 * 直接浮在游戏上。搜索框底图按 GUI Scale 烘焙，圆角在 3x 下保持平滑。</p>
 */
final class BiliBrowseScreen26 extends Screen {

    private static final int ROW_HEIGHT = 44;
    private static final int COVER_W = 56;
    private static final int COVER_H = 32;
    private static final int TEXT = 0xFFF5F7FA;
    private static final int DIM = 0xFFC9D1DB;
    private static final int MUTED = 0xFF8D99A6;
    private static final int ACCENT = 0xFFF0A3C2;
    private static final int ERROR = 0xFFFF8585;

    private static final int PANEL_W = 720;
    private static final int PANEL_H = 460;
    private static final int SEARCH_W = 470;
    private static final int SEARCH_H = 30;

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
    private volatile boolean profileResolved;
    private long profileRetryAt;
    private boolean favoritesTab;
    private int scroll;

    private int panelX;
    private int panelY;
    private int listX;
    private int listTop;
    private int listW;
    private int listBottom;
    private UISurface26.Surface searchIdle;
    private UISurface26.Surface searchFocus;
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
        panelX = Math.max(8, (width - PANEL_W) / 2);
        panelY = Math.max(8, (height - PANEL_H) / 2);

        listX = panelX + 28;
        listW = PANEL_W - 56;
        listTop = panelY + 112;
        listBottom = panelY + 424;

        searchIdle = UISurface26.get("bili/search", SEARCH_W, SEARCH_H,
                (g, w, h) -> UiPaint26.searchField(g, w, h, false), UiScale26.current());
        searchFocus = UISurface26.get("bili/search/focus", SEARCH_W, SEARCH_H,
                (g, w, h) -> UiPaint26.searchField(g, w, h, true), UiScale26.current());

        searchBox = new EditBox(minecraft.font, panelX + 40, panelY + 70, SEARCH_W - 24, 18,
                text("搜索视频"));
        searchBox.setMaxLength(60);
        searchBox.setBordered(false);
        searchBox.setTextColor(TEXT);
        searchBox.setHint(text("搜索 B 站视频…"));
        addRenderableWidget(searchBox);

        status = client.biliCookies().loggedIn() ? "已登录，可搜索或打开收藏夹" : "未登录，在线播放仍可用";
        requestProfile();
    }

    private void requestProfile() {
        if (profileResolved || profileLoading || !client.biliCookies().loggedIn()
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
                profileResolved = true;
            } catch (Exception ignored) {
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
        int textWidth = listW - 70 - 16;
        for (int i = 0; i < videos.size(); i++) {
            BiliApi.Video video = videos.get(i);
            String author = video.author() == null ? "" : video.author();
            String meta = author + " · " + formatDuration(video.durationSeconds());
            built[i] = new Row(video,
                    UiText26.fit(font, video.title(), textWidth),
                    UiText26.fit(font, meta, textWidth));
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
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                  float delta) {
        // 完全透明
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        Font font = minecraft.font;
        drawHeader(extractor, font, mouseX, mouseY);
        drawSearch(extractor, font, mouseX, mouseY);
        drawRows(extractor, font, mouseX, mouseY);
        if (loading) {
            UiDraw26.textCentered(extractor, font, "加载中…",
                    panelX + PANEL_W / 2, panelY + 260, MUTED);
        }
        String message = status;
        int color = message.startsWith("搜索失败") || message.startsWith("收藏夹读取失败")
                || message.startsWith("播放失败") ? ERROR : MUTED;
        statusCache.set(font, message, PANEL_W - 56);
        extractor.text(font, statusCache.text(), panelX + 28, panelY + 438, color, true);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        drawScaledText(extractor, font, "B 站音乐", panelX + 28, panelY + 12, 1.35f, TEXT);
        extractor.text(font, "搜索 · 收藏夹 · 点歌", panelX + 28, panelY + 35, MUTED, true);

        boolean loggedIn = client.biliCookies().loggedIn();
        if (loggedIn && !avatarUrl.isBlank()) {
            BiliImage26.Entry avatar = BiliImage26.get(avatarUrl, 22, 22, true);
            int avatarX = panelX + 500;
            int avatarY = panelY + 14;
            extractor.fill(avatarX, avatarY, avatarX + 22, avatarY + 22, 0x661B2632);
            BiliImage26.draw(extractor, avatar, avatarX, avatarY, 22, 22);
            if (avatar == null) {
                String initial = avatarName.isBlank() ? "B" : avatarName.substring(0, 1);
                extractor.text(font, initial, avatarX + 8, avatarY + 7, ACCENT, true);
            }
            String name = UiText26.fit(font, avatarName.isBlank() ? "已登录" : avatarName, 70);
            extractor.text(font, name, avatarX + 28, avatarY + 7, 0xFFF5F7FA, true);
        } else {
            String account = loggedIn ? "已登录" : "未登录";
            extractor.text(font, account, panelX + 500, panelY + 21,
                    loggedIn ? ACCENT : MUTED, true);
        }
        if (loggedIn && avatarUrl.isBlank()) {
            requestProfile();
        }

        drawAction(extractor, font, loggedIn ? "重新登录" : "登录",
                panelX + 596, panelY + 21, 54, mouseX, mouseY);
        drawAction(extractor, font, "返回", panelX + 660, panelY + 21, 32, mouseX, mouseY);
    }

    private void drawSearch(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        UISurface26.draw(extractor, searchBox != null && searchBox.isFocused()
                ? searchFocus : searchIdle, panelX + 28, panelY + 62);
        drawAction(extractor, font, "搜索", panelX + 510, panelY + 72, 36, mouseX, mouseY);
        drawAction(extractor, font, "收藏夹", panelX + 560, panelY + 72, 48, mouseX, mouseY);
        extractor.fill(panelX + 28, panelY + 100, panelX + PANEL_W - 28, panelY + 101,
                0x1AFFFFFF);
    }

    private void drawAction(GuiGraphicsExtractor extractor, Font font, String label,
                            int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x - 4 && mouseX <= x + w + 4
                && mouseY >= y - 4 && mouseY <= y + 16;
        extractor.text(font, label, x, y, hover ? ACCENT : DIM, true);
        if (hover) {
            extractor.fill(x, y + 13, x + font.width(label), y + 14, ACCENT);
        }
    }

    private void drawRows(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        Row[] list = rows;
        if (list.length == 0) {
            if (!loading) {
                String empty = favoritesTab ? "收藏夹为空" : "没有结果";
                UiDraw26.textCentered(extractor, font, empty, panelX + PANEL_W / 2,
                        panelY + 250, MUTED);
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
                    && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 4;
            boolean playing = current != null && current.bvid() != null
                    && current.bvid().equals(item.video().bvid());
            if (hover || playing) {
                extractor.fill(listX, y, listX + listW, y + ROW_HEIGHT - 4,
                        playing ? 0x22F0A3C2 : 0x14FFFFFF);
            }
            if (playing) {
                extractor.fill(listX, y + 6, listX + 2, y + ROW_HEIGHT - 10, ACCENT);
            }
            int coverX = listX + 10;
            int coverY = y + (ROW_HEIGHT - COVER_H) / 2;
            BiliImage26.Entry cover = BiliImage26.get(item.video().cover(),
                    COVER_W, COVER_H, false);
            if (cover == null) {
                extractor.fill(coverX, coverY, coverX + COVER_W, coverY + COVER_H, 0x661C2632);
                int cx = coverX + COVER_W / 2;
                int cy = coverY + COVER_H / 2;
                for (int i = 0; i < 8; i += 2) {
                    int half = Math.max(1, 5 - i / 2);
                    extractor.fill(cx - 3 + i, cy - half, cx + i, cy + half, 0xAA4A6178);
                }
            } else {
                BiliImage26.draw(extractor, cover, coverX, coverY, COVER_W, COVER_H);
            }
            int textX = coverX + COVER_W + 12;
            extractor.text(font, item.title(), textX, y + 8,
                    playing ? ACCENT : TEXT, true);
            extractor.text(font, item.meta(), textX, y + 22, 0xFFC9D1DB, true);
        }

        if (maxScroll > 0) {
            int trackX = listX + listW - 2;
            int viewH = listBottom - listTop;
            extractor.fill(trackX, listTop, trackX + 2, listBottom, 0x14FFFFFF);
            int thumbH = Math.max(20, viewH * visible / list.length);
            int thumbY = listTop + (int) ((viewH - thumbH) * (scroll / (float) maxScroll));
            extractor.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x66F0A3C2);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (hit(event.x(), event.y(), panelX + 660, panelY + 21, 32, 16)) {
                onClose();
                return true;
            }
            if (hit(event.x(), event.y(), panelX + 596, panelY + 21, 54, 16)) {
                Minecraft.getInstance().setScreen(
                        new BiliLoginScreen26(client, () -> status = "登录成功", this));
                return true;
            }
            if (hit(event.x(), event.y(), panelX + 510, panelY + 72, 36, 16)) {
                search();
                return true;
            }
            if (hit(event.x(), event.y(), panelX + 560, panelY + 72, 48, 16)) {
                loadFavorites();
                return true;
            }
            if (event.x() >= listX && event.x() < listX + listW
                    && event.y() >= listTop && event.y() < listBottom) {
                int index = scroll + (int) ((event.y() - listTop) / ROW_HEIGHT);
                Row[] list = rows;
                if (index >= 0 && index < list.length) {
                    play(list[index].video());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x - 4 && mx <= x + w + 4 && my >= y - 4 && my <= y + h;
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
        extractor.text(font, text, 0, 0, color, true);
        pose.popMatrix();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
