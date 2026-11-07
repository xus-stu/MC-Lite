package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 模组设置页：灵动岛开关/位置、JEI/Jade 集成、音量与歌词。 */
final class IslandConfigScreen26 extends Screen {

    private final McliteIsland26Client client;
    private final PlaybackService service;
    private final IslandConfig config;
    private final Runnable saveConfig;

    private Button jeiButton;
    private Button jadeButton;
    private Button contextButton;
    private Button islandButton;

    IslandConfigScreen26(McliteIsland26Client client, PlaybackService service,
                         IslandConfig config, Runnable saveConfig) {
        super(Component.literal("MCLite Island 设置"));
        this.client = client;
        this.service = service;
        this.config = config;
        this.saveConfig = saveConfig;
    }

    private static Component text(String value) {
        return Component.literal(value == null ? "" : value);
    }

    @Override
    protected void init() {
        int left = width / 2 - 160;
        int y = 64;
        islandButton = addRenderableWidget(Button.builder(text("灵动岛"), b -> {
            config.showIsland = !config.showIsland;
            refresh();
        }).bounds(left, y, 150, 20).build());
        contextButton = addRenderableWidget(Button.builder(text("上下文信息"), b -> {
            config.showContext = !config.showContext;
            refresh();
        }).bounds(left + 170, y, 150, 20).build());
        jeiButton = addRenderableWidget(Button.builder(text("JEI 集成"), b -> {
            config.useJei = !config.useJei;
            refresh();
        }).bounds(left, y + 28, 150, 20).build());
        jadeButton = addRenderableWidget(Button.builder(text("Jade 集成"), b -> {
            config.useJade = !config.useJade;
            refresh();
        }).bounds(left + 170, y + 28, 150, 20).build());

        addRenderableWidget(Button.builder(text("缩小 -"), b -> {
            config.islandScale = Math.max(0.6f, config.islandScale - 0.1f);
            saveConfig.run();
        }).bounds(left, y + 64, 70, 20).build());
        addRenderableWidget(Button.builder(text("放大 +"), b -> {
            config.islandScale = Math.min(2.0f, config.islandScale + 0.1f);
            saveConfig.run();
        }).bounds(left + 78, y + 64, 70, 20).build());
        addRenderableWidget(Button.builder(text("上移"), b -> {
            config.islandY = Math.max(-40, config.islandY - 4);
            saveConfig.run();
        }).bounds(left + 166, y + 64, 70, 20).build());
        addRenderableWidget(Button.builder(text("下移"), b -> {
            config.islandY = Math.min(120, config.islandY + 4);
            saveConfig.run();
        }).bounds(left + 244, y + 64, 70, 20).build());

        addRenderableWidget(Button.builder(text("音量 -"), b -> {
            service.setVolume(config.volume - 0.1f);
            saveConfig.run();
        }).bounds(left, y + 96, 70, 20).build());
        addRenderableWidget(Button.builder(text("音量 +"), b -> {
            service.setVolume(config.volume + 0.1f);
            saveConfig.run();
        }).bounds(left + 78, y + 96, 70, 20).build());
        addRenderableWidget(Button.builder(text("3D 歌词"), b -> {
            config.threeDLyrics = !config.threeDLyrics;
            refresh();
        }).bounds(left + 166, y + 96, 70, 20).build());
        addRenderableWidget(Button.builder(text("居中复位"), b -> {
            config.islandX = 0;
            config.islandY = 0;
            config.islandScale = 1.0f;
            saveConfig.run();
        }).bounds(left + 244, y + 96, 70, 20).build());

        addRenderableWidget(Button.builder(text("B站登录/退出"), b -> {
            if (client.biliCookies().loggedIn()) {
                client.logoutBili();
                refresh();
            } else {
                Minecraft.getInstance().setScreen(new BiliLoginScreen26(client, this::refresh));
            }
        }).bounds(left, y + 128, 150, 20).build());
        addRenderableWidget(Button.builder(text("音质:" + config.biliQuality.label()), b -> {
            config.biliQuality = config.biliQuality.next();
            client.biliCache().setQuality(config.biliQuality);
            saveConfig.run();
            refresh();
        }).bounds(left + 170, y + 128, 150, 20).build());
        addRenderableWidget(Button.builder(text("清除 B 站缓存"), b -> {
            try {
                client.biliCache().clear();
                refresh();
            } catch (Exception ignored) {
                // 缓存清理失败保留目录
            }
        }).bounds(left, y + 156, 150, 20).build());

        addRenderableWidget(Button.builder(text("完成"), b -> onClose())
                .bounds(width / 2 - 40, height - 40, 80, 20).build());
        refresh();
    }

    private void refresh() {
        if (islandButton != null) {
            islandButton.setMessage(text("灵动岛: " + (config.showIsland ? "开" : "关")));
        }
        if (contextButton != null) {
            contextButton.setMessage(text("上下文: " + (config.showContext ? "开" : "关")));
        }
        if (jeiButton != null) {
            jeiButton.setMessage(text("JEI: " + (config.useJei ? "开" : "关")));
        }
        if (jadeButton != null) {
            jadeButton.setMessage(text("Jade: " + (config.useJade ? "开" : "关")));
        }
        if (client.biliCookies().loggedIn()) {
            // 登录状态在渲染文本里展示，这里只保证按钮文案可读
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        var font = minecraft.font;
        extractor.text(font, "MCLite Island 设置", 16, 16, 0xFFFFFFFF, false);
        extractor.text(font, "缩放 " + String.format("%.1f", config.islandScale)
                + " · 音量 " + String.format("%.0f%%", config.volume * 100)
                + " · 位置 " + config.islandX + "," + config.islandY, 16, 36, 0xFF9AA4B0, false);
        String bili = client.biliCookies().loggedIn()
                ? "B站: 已登录 · 音质 " + config.biliQuality.label()
                : "B站: 未登录（在线播放可用，收藏夹需登录）";
        extractor.text(font, bili, 16, 168, 0xFF9AA4B0, false);
        extractor.text(font, "3D 歌词在 26.x 暂未支持，开关会保存但不会渲染", 16, height - 64, 0xFF9AA4B0, false);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
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
