package dev.mclite.island.mc26;

import dev.mclite.island.bili.BiliApi;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** B 站浏览：搜索 / 收藏夹，点歌后加入播放队列并立即播放。 */
final class BiliBrowseScreen26 extends Screen {

    private final McliteIsland26Client client;
    private final PlaybackService service;
    private final IslandConfig config;

    private EditBox searchBox;
    private final List<BiliApi.Video> results = new ArrayList<>();
    private volatile List<BiliApi.Video> loaded = List.of();
    private volatile String status = "输入关键字搜索，或打开收藏夹";
    private volatile boolean loading;
    private boolean favoritesTab;
    private int scroll;
    private int listTop = 92;
    private int listBottom;

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
        int left = width / 2 - 320;
        searchBox = new EditBox(minecraft.font, left, 52, 300, 20, text("搜索视频"));
        searchBox.setMaxLength(60);
        searchBox.setHint(text("搜索 B 站视频…"));
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(text("搜索"), b -> search())
                .bounds(left + 308, 52, 52, 20).build());
        addRenderableWidget(Button.builder(text("收藏夹"), b -> loadFavorites())
                .bounds(left + 368, 52, 64, 20).build());
        addRenderableWidget(Button.builder(text("登录"), b -> Minecraft.getInstance()
                        .setScreen(new BiliLoginScreen26(client, () -> status = "登录成功")))
                .bounds(left + 440, 52, 52, 20).build());
        addRenderableWidget(Button.builder(text("返回"), b -> onClose())
                .bounds(left + 500, 52, 52, 20).build());
        listBottom = height - 40;
        status = client.biliCookies().loggedIn() ? "已登录，可搜索或打开收藏夹" : "未登录，在线播放仍可用";
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
                    loaded = videos;
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
                    loaded = all;
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

    private void play(BiliApi.Video video) {
        status = "解析 " + video.bvid() + " …";
        new Thread(() -> {
            try {
                BiliApi.View view = client.bili().view(video.bvid());
                BiliApi.AudioStream stream = client.bili().playUrl(view.bvid(), view.cid(),
                        config.biliQuality);
                Track track = Track.remote(view.bvid(), view.cid(), stream.audioId(),
                        stream.url(), view.title(), view.owner(), view.durationSeconds() * 1000L);
                Minecraft.getInstance().execute(() -> {
                    service.playlist().replace(List.of(track));
                    service.playAt(0);
                    status = "正在播放: " + view.title();
                    onClose();
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> status = "播放失败: " + ex.getMessage());
            }
        }, "mclite-island-bili-play").start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        var font = minecraft.font;
        int left = width / 2 - 320;
        int right = width / 2 + 320;
        UiDraw26.panel(extractor, UiDraw26.CARD, left - 12, 40, right - left + 24, height - 60, 16);
        String title = "B 站音乐";
        extractor.text(font, title, left, 22, 0xFFF2F5F8, false);
        String account = client.biliCookies().loggedIn() ? "已登录" : "未登录";
        extractor.text(font, account, right - font.width(account), 22,
                client.biliCookies().loggedIn() ? 0xFF6CC6FF : 0xFF93A0AD, false);

        List<BiliApi.Video> list = loaded;
        int rowHeight = 22;
        int visible = Math.max(1, (listBottom - listTop) / rowHeight);
        int maxScroll = Math.max(0, list.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            if (index >= list.size()) {
                break;
            }
            BiliApi.Video video = list.get(index);
            int y = listTop + i * rowHeight;
            extractor.fill(left - 4, y - 2, right, y + rowHeight - 4, 0x18FFFFFF);
            String text = video.title();
            extractor.text(font, font.plainSubstrByWidth(text, right - left - 130),
                    left + 4, y, 0xFFF2F5F8, false);
            String meta = video.author() + " · " + formatDuration(video.durationSeconds());
            extractor.text(font, meta, right - font.width(meta) - 6, y, 0xFF93A0AD, false);
        }
        if (list.isEmpty() && !loading) {
            String empty = favoritesTab ? "收藏夹为空" : "没有结果";
            extractor.text(font, empty, left + (right - left - font.width(empty)) / 2,
                    listTop + 20, 0xFF93A0AD, false);
        }
        extractor.text(font, status, left, height - 32,
                status.startsWith("失败") || status.contains("失败") ? 0xFFFF6B6B : 0xFF93A0AD, false);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.y() >= listTop && event.y() < listBottom) {
            int index = scroll + (int) ((event.y() - listTop) / 22);
            List<BiliApi.Video> list = loaded;
            if (index >= 0 && index < list.size()) {
                play(list.get(index));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll -= (int) Math.signum(deltaY) * 4;
        return true;
    }

    private static String formatDuration(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static void fillRounded(GuiGraphicsExtractor extractor, int x, int y,
                                    int width, int height, int radius, int color) {
        int safe = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int edge = Math.min(row, height - 1 - row);
            int inset = 0;
            if (edge < safe) {
                int dx = safe - edge;
                inset = safe - (int) Math.round(Math.sqrt(Math.max(0, safe * safe - dx * dx)));
            }
            extractor.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
