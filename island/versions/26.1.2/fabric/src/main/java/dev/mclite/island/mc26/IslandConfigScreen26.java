package dev.mclite.island.mc26;

import dev.mclite.island.bili.BiliQuality;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlayMode;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 26.x 设置页：左侧分类 + 右侧滚动内容，顶部实时灵动岛预览。
 *
 * <p>开关/滑杆/分段/操作按钮全部自绘，不依赖原版 Button 或 JSlider。
 * 静态底图由 {@link UISurface26} 烘焙，滚动内容只画文字和小控件。</p>
 */
final class IslandConfigScreen26 extends Screen {

    private static final String[] CATEGORIES = {"播放", "歌词", "场景声学", "灵动岛", "集成"};
    private static final String[] PLAY_MODE_LABELS = {"顺序", "随机", "单曲", "列表"};
    private static final String[] BILI_QUALITY_LABELS = {"自动", "64K", "132K", "192K"};
    private static final int TEXT = 0xFFF2F5F8;
    private static final int DIM = 0xFF93A0AD;
    private static final int MUTED = 0xFF6B7885;
    private static final int ACCENT = 0xFF8FD3FF;
    private static final int ERROR = 0xFFFF8A8A;
    private static final String IDLE_STATUS = "改动会立即保存";

    private final McliteIsland26Client client;
    private final PlaybackService service;
    private final IslandConfig config;
    private final Runnable saveConfig;

    private int panelX;
    private int panelY;
    private UISurface26.Surface background;
    private int category;
    private int scroll;
    private int footerTicks;
    private String footerStatus = IDLE_STATUS;
    private String categoryText = "分类 1/5 · 滚轮翻页";
    private IslandSnapshot snapshot;

    private final List<Row> rows = new ArrayList<>();
    private final UiButton26[] navButtons = new UiButton26[CATEGORIES.length];
    private final UiText26 footerCache = new UiText26();
    private Row activeDrag;

    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;

    IslandConfigScreen26(McliteIsland26Client client, PlaybackService service,
                         IslandConfig config, Runnable saveConfig) {
        super(Component.literal("MCLite Island 设置"));
        this.client = client;
        this.service = service;
        this.config = config;
        this.saveConfig = saveConfig;
    }

    @Override
    protected void init() {
        panelX = Math.max(8, (width - UiPaint26.SETTINGS_W) / 2);
        panelY = Math.max(8, (height - UiPaint26.SETTINGS_H) / 2);
        activeDrag = null;
        background = UISurface26.get("settings", UiPaint26.SETTINGS_W, UiPaint26.SETTINGS_H,
                UiPaint26::settings);

        viewportX = panelX + UiPaint26.SET_VIEW_X;
        viewportY = panelY + UiPaint26.SET_VIEW_Y;
        viewportW = UiPaint26.SET_VIEW_W;
        viewportH = UiPaint26.SET_VIEW_H;

        int navX = panelX + UiPaint26.SET_NAV_X + 12;
        int navY = panelY + UiPaint26.SET_NAV_Y + 18;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int index = i;
            navButtons[i] = addRenderableWidget(UiButton26.ghost(CATEGORIES[i], navX,
                    navY + i * 42, 156, 32, () -> setCategory(index)));
        }
        addRenderableWidget(UiButton26.primary("完成",
                panelX + UiPaint26.SET_DONE_X, panelY + UiPaint26.SET_DONE_Y,
                UiPaint26.SET_DONE_W, UiPaint26.SET_DONE_H, this::onClose));

        setCategory(category);
    }

    private void setCategory(int index) {
        category = Math.max(0, Math.min(CATEGORIES.length - 1, index));
        scroll = 0;
        categoryText = "分类 " + (category + 1) + "/" + CATEGORIES.length + " · 滚轮翻页";
        buildRows();
        for (int i = 0; i < navButtons.length; i++) {
            if (navButtons[i] != null) {
                navButtons[i].setStyle(i == category ? UiButton26.Style.SOFT : UiButton26.Style.GHOST);
            }
        }
    }

    // =====================================================================
    // 分类内容
    // =====================================================================

    private void buildRows() {
        rows.clear();
        switch (category) {
            case 0 -> buildPlayRows();
            case 1 -> buildLyricRows();
            case 2 -> buildAcousticRows();
            case 3 -> buildIslandRows();
            default -> buildIntegrationRows();
        }
    }

    private void buildPlayRows() {
        rows.add(new SliderRow("音量", "0 - 100%", 0f, 100f,
                () -> config.volume * 100f,
                value -> service.setVolume(value / 100f),
                value -> Math.round(value) + "%"));
        rows.add(new SegmentRow("播放模式", "顺序 / 随机 / 单曲 / 列表", PLAY_MODE_LABELS,
                () -> config.playMode.ordinal(),
                index -> {
                    config.playMode = PlayMode.values()[index];
                    service.playlist().mode(config.playMode);
                }));
        rows.add(new InfoRow("当前曲目", "正在播放",
                () -> snapshot != null && snapshot.hasTrack() ? snapshot.title() : "未在播放"));
        rows.add(new ActionRow("音乐库", "重新扫描本地音乐目录", "重新扫描", false, () -> {
            service.reloadLibrary();
            footerStatus = "已重新扫描，共 " + service.playlist().size() + " 首";
            footerTicks = 80;
        }));
    }

    private void buildLyricRows() {
        rows.add(new ToggleRow("自动歌词", "本地没有 .lrc 时按歌名在线匹配", () -> config.autoLyrics,
                value -> config.autoLyrics = value));
        rows.add(new SliderRow("歌词偏移", "正数让歌词提前出现", -1000f, 1000f,
                () -> config.lyricsOffsetMs,
                value -> config.lyricsOffsetMs = Math.round(value / 50f) * 50L,
                value -> {
                    long ms = Math.round(value / 50f) * 50L;
                    return (ms > 0 ? "+" : "") + ms + " ms";
                }));
        rows.add(new ToggleRow("3D 歌词", "在世界空间里显示当前歌词", () -> config.threeDLyrics,
                value -> config.threeDLyrics = value));
        rows.add(new SliderRow("3D 距离", "歌词离镜头多远（格）", 1f, 8f,
                () -> config.lyricsDistance,
                value -> config.lyricsDistance = Math.round(value * 10f) / 10f,
                value -> String.format(java.util.Locale.ROOT, "%.1f", value)));
        rows.add(new SliderRow("3D 缩放", "0.5 - 3.0x", 0.5f, 3f,
                () -> config.lyricsScale,
                value -> config.lyricsScale = Math.round(value * 10f) / 10f,
                value -> String.format(java.util.Locale.ROOT, "%.1fx", value)));
        rows.add(new SliderRow("3D 垂直偏移", "负数往下", -1f, 1f,
                () -> config.lyricsVerticalOffset,
                value -> config.lyricsVerticalOffset = Math.round(value * 20f) / 20f,
                value -> String.format(java.util.Locale.ROOT, "%.2f", value)));
        rows.add(new SliderRow("3D 行数", "1 - 4 行", 1f, 4f,
                () -> config.lyricsMaxLines,
                value -> config.lyricsMaxLines = Math.round(value),
                value -> Math.round(value) + " 行"));
        rows.add(new ActionRow("当前歌词", "重新读取当前曲目的 .lrc", "重新加载", false, () -> {
            service.reloadLyrics();
            footerStatus = "已重新加载歌词";
            footerTicks = 80;
        }));
    }

    private void buildAcousticRows() {
        rows.add(new ToggleRow("场景声学", "根据水下 / 室内 / 雨天调整混响", () -> config.acousticsEnabled,
                value -> {
                    config.acousticsEnabled = value;
                    client.acousticsEnabled(value);
                }));
        rows.add(new ToggleRow("水下", "潜入水中时压低高频", () -> config.acousticsSubmersion,
                value -> config.acousticsSubmersion = value));
        rows.add(new ToggleRow("室内", "看不到天空时收干一点", () -> config.acousticsEnclosure,
                value -> config.acousticsEnclosure = value));
        rows.add(new ToggleRow("雨天", "下雨时增加一点空间感", () -> config.acousticsRainfall,
                value -> config.acousticsRainfall = value));
        rows.add(new SliderRow("混响强度", "0 - 100", 0f, 100f,
                () -> config.acousticsStrength,
                value -> config.acousticsStrength = Math.round(value),
                value -> Math.round(value) + "%"));
    }

    private void buildIslandRows() {
        rows.add(new ToggleRow("显示灵动岛", "屏幕顶部居中显示播放胶囊", () -> config.showIsland,
                value -> config.showIsland = value));
        rows.add(new SliderRow("缩放", "0.5x - 2.0x", 50f, 200f,
                () -> config.islandScale * 100f,
                value -> config.islandScale = Math.round(value / 10f) / 10f,
                value -> String.format(java.util.Locale.ROOT, "%.1fx", value / 100f)));
        rows.add(new SliderRow("水平偏移", "0 - 400 px", 0f, 400f,
                () -> config.islandX,
                value -> config.islandX = Math.round(value),
                value -> Math.round(value) + " px"));
        rows.add(new SliderRow("垂直偏移", "0 - 200 px", 0f, 200f,
                () -> config.islandY,
                value -> config.islandY = Math.round(value),
                value -> Math.round(value) + " px"));
        rows.add(new ActionRow("位置与缩放", "恢复居中、1.0x", "重置", false, () -> {
            config.islandX = 0;
            config.islandY = 0;
            config.islandScale = 1f;
        }));
    }

    private void buildIntegrationRows() {
        rows.add(new ToggleRow("上下文信息", "总开关；还要打开下面至少一项才会采集",
                () -> config.showContext, value -> {
                    config.showContext = value;
                    if (!value) {
                        config.useJei = false;
                        config.useJade = false;
                        config.useOwnProbe = false;
                    }
                }));
        rows.add(new ToggleRow("JEI 物品信息", "悬停 JEI 物品列表时显示在灵动岛", () -> config.useJei,
                value -> {
                    config.useJei = value;
                    if (value) {
                        config.showContext = true;
                    }
                }));
        rows.add(new ToggleRow("Jade 目标提示", "悬停方块/实体时显示目标名称", () -> config.useJade,
                value -> {
                    config.useJade = value;
                    if (value) {
                        config.showContext = true;
                    }
                }));
        rows.add(new ToggleRow("准星方块/实体", "没装 JEI/Jade 时的自带探针", () -> config.useOwnProbe,
                value -> {
                    config.useOwnProbe = value;
                    if (value) {
                        config.showContext = true;
                    }
                }));
        rows.add(new InfoRow("B站账号", "登录状态",
                () -> client.biliCookies().loggedIn() ? "已登录" : "未登录"));
        rows.add(new ActionRow("B站登录", "扫码登录或退出当前账号",
                client.biliCookies().loggedIn() ? "退出登录" : "扫码登录", false, () -> {
                    if (client.biliCookies().loggedIn()) {
                        client.logoutBili();
                        footerStatus = "已退出 B 站";
                        footerTicks = 80;
                    } else {
                        Minecraft.getInstance().setScreen(new BiliLoginScreen26(client, () -> {
                            footerStatus = "登录成功";
                            footerTicks = 80;
                        }, IslandConfigScreen26.this));
                    }
                }));
        rows.add(new SegmentRow("B站音质", "在线音频档位", BILI_QUALITY_LABELS,
                () -> config.biliQuality.ordinal(),
                index -> {
                    config.biliQuality = BiliQuality.values()[index];
                    client.biliCache().setQuality(config.biliQuality);
                }));
        rows.add(new ActionRow("B站缓存", "清空在线音频缓存", "清除缓存", false, () -> {
            try {
                client.biliCache().clear();
                footerStatus = "B站缓存已清空";
            } catch (Exception failure) {
                footerStatus = "清空失败: " + failure.getMessage();
            }
            footerTicks = 80;
        }));
        rows.add(new InfoRow("说明", "JEI / Jade 自己的悬浮窗不由本模组控制",
                () -> "仅影响灵动岛"));
    }

    // =====================================================================
    // 渲染
    // =====================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        Font font = minecraft.font;
        snapshot = service.snapshot();
        if (background != null) {
            UISurface26.draw(extractor, background, panelX, panelY);
        } else {
            UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY,
                    UiPaint26.SETTINGS_W, UiPaint26.SETTINGS_H, 20);
            UiDraw26.panel(extractor, UiDraw26.DARK, panelX + UiPaint26.SET_NAV_X,
                    panelY + UiPaint26.SET_NAV_Y, UiPaint26.SET_NAV_W,
                    UiPaint26.SET_NAV_H, 14);
            UiDraw26.panel(extractor, UiDraw26.DARK, panelX + UiPaint26.SET_CONTENT_X,
                    panelY + UiPaint26.SET_CONTENT_Y, UiPaint26.SET_CONTENT_W,
                    UiPaint26.SET_CONTENT_H, 14);
        }

        // 顶部实时预览（设置页没有独立页头，预览条就是页头）
        extractor.text(font, "设置 · 灵动岛预览", panelX + 36, panelY + 28, DIM, false);
        UiDraw26.textRight(extractor, font, config.showIsland ? "实时" : "已隐藏",
                panelX + 724, panelY + 28, config.showIsland ? ACCENT : MUTED);
        IslandHud26.renderPreview(extractor, config, service,
                panelX + UiPaint26.SET_PREVIEW_W / 2 + UiPaint26.SET_PREVIEW_X,
                panelY + UiPaint26.SET_PREVIEW_Y + UiPaint26.SET_PREVIEW_H / 2);

        // 标题 + 分类内容
        drawScaledText(extractor, font, CATEGORIES[category],
                viewportX, panelY + UiPaint26.SET_CONTENT_Y + 12, 1.15f, TEXT);
        drawRows(extractor, font, mouseX, mouseY);

        // 底部状态
        String footer = footerStatus;
        int footerColor = footer.contains("失败") || footer.contains("打不开")
                ? ERROR : MUTED;
        footerCache.set(font, footer, 420);
        extractor.text(font, footerCache.text(), panelX + 24, panelY + 454, footerColor, false);
        extractor.text(font, categoryText, panelX + 470, panelY + 454, MUTED, false);

        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private void drawRows(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        int total = totalHeight();
        int maxScroll = Math.max(0, total - viewportH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        extractor.enableScissor(viewportX, viewportY, viewportX + viewportW,
                viewportY + viewportH);
        int y = viewportY - scroll;
        int rowW = viewportW - 12;
        for (Row row : rows) {
            row.x = viewportX;
            row.y = y;
            row.w = rowW;
            if (y + row.height > viewportY && y < viewportY + viewportH) {
                row.render(extractor, font, mouseX, mouseY);
                if (row != rows.get(rows.size() - 1)) {
                    extractor.fill(viewportX + 14, y + row.height - 1,
                            viewportX + rowW - 14, y + row.height, 0x10FFFFFF);
                }
            }
            y += row.height;
        }
        extractor.disableScissor();

        if (maxScroll > 0) {
            int trackX = viewportX + viewportW - 4;
            extractor.fill(trackX, viewportY, trackX + 3, viewportY + viewportH, 0x14FFFFFF);
            int thumbH = Math.max(20, viewportH * viewportH / total);
            int thumbY = viewportY + (int) ((viewportH - thumbH) * (scroll / (float) maxScroll));
            extractor.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0x55FFFFFF);
        }
    }

    private int totalHeight() {
        int total = 0;
        for (Row row : rows) {
            total += row.height;
        }
        return total;
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
    public void tick() {
        if (footerTicks > 0 && --footerTicks == 0) {
            footerStatus = IDLE_STATUS;
        }
    }

    // =====================================================================
    // 输入
    // =====================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= viewportX && event.x() < viewportX + viewportW
                && event.y() >= viewportY && event.y() < viewportY + viewportH) {
            for (Row row : rows) {
                if (row.contains(event.x(), event.y()) && row.click(event.x(), event.y())) {
                    if (row.dragging()) {
                        activeDrag = row;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeDrag != null) {
            activeDrag.drag(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (activeDrag != null) {
            boolean changed = activeDrag.release();
            activeDrag = null;
            if (changed) {
                commit();
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= panelX + UiPaint26.SET_CONTENT_X
                && mouseX < panelX + UiPaint26.SET_CONTENT_X + UiPaint26.SET_CONTENT_W
                && mouseY >= panelY + UiPaint26.SET_CONTENT_Y
                && mouseY < panelY + UiPaint26.SET_CONTENT_Y + UiPaint26.SET_CONTENT_H) {
            scroll -= (int) Math.signum(deltaY) * 32;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, totalHeight() - viewportH)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void commit() {
        saveConfig.run();
        footerStatus = "已保存";
        footerTicks = 60;
    }

    @Override
    public void onClose() {
        service.saveState();
        saveConfig.run();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================
    // 自绘行
    // =====================================================================

    private abstract class Row {
        final String label;
        final String desc;
        int height;
        int x;
        int y;
        int w;
        boolean interactive = true;

        Row(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }

        void render(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
            if (interactive && contains(mouseX, mouseY)) {
                UiDraw26.panel(extractor, UiDraw26.HOVER, x + 2, y + 2, w - 4, height - 4);
            }
            int labelY = desc == null ? y + (height - 8) / 2 : y + 7;
            extractor.text(font, label, x + 14, labelY, TEXT, false);
            if (desc != null) {
                extractor.text(font, desc, x + 14, y + 23, MUTED, false);
            }
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + height;
        }

        boolean click(double mouseX, double mouseY) {
            return false;
        }

        boolean drag(double mouseX, double mouseY) {
            return false;
        }

        boolean release() {
            return false;
        }

        boolean dragging() {
            return false;
        }
    }

    private final class ToggleRow extends Row {
        private final BoolGetter getter;
        private final BoolSetter setter;

        ToggleRow(String label, String desc, BoolGetter getter, BoolSetter setter) {
            super(label, desc);
            this.getter = getter;
            this.setter = setter;
            this.height = 50;
        }

        @Override
        void render(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
            super.render(extractor, font, mouseX, mouseY);
            boolean on = getter.get();
            int tx = x + w - 50;
            int ty = y + (height - 18) / 2;
            UiDraw26.panel(extractor, on ? UiDraw26.ON : UiDraw26.OFF, tx, ty, 36, 18);
            UiDraw26.panel(extractor, UiDraw26.WHITE, on ? tx + 20 : tx + 2, ty + 3, 12, 12);
        }

        @Override
        boolean click(double mouseX, double mouseY) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            setter.set(!getter.get());
            commit();
            return true;
        }
    }

    private final class SliderRow extends Row {
        private final FloatGetter getter;
        private final FloatSetter setter;
        private final FloatFormat format;
        private final float min;
        private final float max;
        private final UiSlider26 slider = new UiSlider26();
        private String valueText = "";
        private int shownBucket = Integer.MIN_VALUE;

        SliderRow(String label, String desc, float min, float max,
                  FloatGetter getter, FloatSetter setter, FloatFormat format) {
            super(label, desc);
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            this.format = format;
            this.height = 58;
        }

        @Override
        void render(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
            super.render(extractor, font, mouseX, mouseY);
            float value = Math.max(min, Math.min(max, getter.get()));
            int bucket = Math.round((value - min) / (max - min) * 1000f);
            if (bucket != shownBucket) {
                shownBucket = bucket;
                valueText = format.format(value);
            }
            UiDraw26.textRight(extractor, font, valueText, x + w - 16, y + 7, DIM);
            slider.bounds(x + 14, y + 34, w - 28, 16);
            slider.render(extractor, (value - min) / (max - min),
                    slider.contains(mouseX, mouseY));
        }

        @Override
        boolean click(double mouseX, double mouseY) {
            if (!slider.contains(mouseX, mouseY)) {
                return false;
            }
            slider.mouseClicked(mouseX, mouseY);
            apply(slider.fractionAt(mouseX));
            return true;
        }

        @Override
        boolean drag(double mouseX, double mouseY) {
            if (!slider.isDragging()) {
                return false;
            }
            apply(slider.fractionAt(mouseX));
            return true;
        }

        @Override
        boolean release() {
            boolean changed = slider.isDragging();
            slider.release();
            return changed;
        }

        @Override
        boolean dragging() {
            return slider.isDragging();
        }

        private void apply(float fraction) {
            float value = min + Math.max(0f, Math.min(1f, fraction)) * (max - min);
            setter.set(value);
        }
    }

    private final class SegmentRow extends Row {
        private final String[] options;
        private final IntGetter getter;
        private final IntSetter setter;

        SegmentRow(String label, String desc, String[] options, IntGetter getter, IntSetter setter) {
            super(label, desc);
            this.options = options;
            this.getter = getter;
            this.setter = setter;
            this.height = 64;
        }

        @Override
        void render(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
            super.render(extractor, font, mouseX, mouseY);
            int bx = x + 14;
            int by = y + 34;
            int bw = w - 28;
            int bh = 24;
            UiDraw26.panel(extractor, UiDraw26.DARK, bx, by, bw, bh);
            int active = Math.max(0, Math.min(options.length - 1, getter.get()));
            int segment = bw / options.length;
            for (int i = 0; i < options.length; i++) {
                int sx = bx + i * segment;
                int sw = i == options.length - 1 ? bx + bw - sx : segment;
                boolean hover = mouseX >= sx && mouseX < sx + sw
                        && mouseY >= by && mouseY < by + bh;
                if (i == active) {
                    UiDraw26.panel(extractor, UiDraw26.ACCENT, sx + 2, by + 2, sw - 4, bh - 4);
                } else if (hover) {
                    UiDraw26.panel(extractor, UiDraw26.HOVER, sx + 2, by + 2, sw - 4, bh - 4);
                }
                String option = options[i];
                UiDraw26.textCentered(extractor, font, option, sx + sw / 2, by + 8,
                        i == active ? 0xFF0E1216 : TEXT);
            }
        }

        @Override
        boolean click(double mouseX, double mouseY) {
            int bx = x + 14;
            int by = y + 34;
            int bw = w - 28;
            if (mouseX < bx || mouseX >= bx + bw || mouseY < by || mouseY >= by + 24) {
                return false;
            }
            int index = (int) ((mouseX - bx) / (bw / (float) options.length));
            setter.set(Math.max(0, Math.min(options.length - 1, index)));
            commit();
            return true;
        }
    }

    private final class ActionRow extends Row {
        private final String buttonLabel;
        private final boolean primary;
        private final Runnable action;

        ActionRow(String label, String desc, String buttonLabel, boolean primary, Runnable action) {
            super(label, desc);
            this.buttonLabel = buttonLabel;
            this.primary = primary;
            this.action = action;
            this.height = 54;
        }

        @Override
        void render(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
            super.render(extractor, font, mouseX, mouseY);
            int bx = x + w - 126;
            int by = y + 14;
            boolean hover = mouseX >= bx && mouseX < bx + 112
                    && mouseY >= by && mouseY < by + 26;
            var texture = primary ? (hover ? UiDraw26.WHITE : UiDraw26.ACCENT)
                    : (hover ? UiDraw26.SOFT : UiDraw26.HOVER);
            UiDraw26.panel(extractor, texture, bx, by, 112, 26);
            UiDraw26.textCentered(extractor, font, buttonLabel, bx + 56, by + 9,
                    primary || hover ? TEXT : DIM);
        }

        @Override
        boolean click(double mouseX, double mouseY) {
            int bx = x + w - 126;
            int by = y + 14;
            if (mouseX < bx || mouseX >= bx + 112 || mouseY < by || mouseY >= by + 26) {
                return false;
            }
            action.run();
            saveConfig.run();
            if (footerTicks <= 0) {
                footerStatus = "已保存";
                footerTicks = 60;
            }
            return true;
        }
    }

    private final class InfoRow extends Row {
        private final Supplier<String> value;
        private final UiText26 cache = new UiText26();

        InfoRow(String label, String desc, Supplier<String> value) {
            super(label, desc);
            this.value = value;
            this.height = 44;
            this.interactive = false;
        }

        @Override
        void render(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
            super.render(extractor, font, mouseX, mouseY);
            cache.set(font, value.get(), Math.max(80, w / 2 - 20));
            UiDraw26.textRight(extractor, font, cache.text(), x + w - 16,
                    desc == null ? y + (height - 8) / 2 : y + 7, DIM);
        }
    }

    private interface BoolGetter {
        boolean get();
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private interface FloatGetter {
        float get();
    }

    private interface FloatSetter {
        void set(float value);
    }

    private interface FloatFormat {
        String format(float value);
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }
}
