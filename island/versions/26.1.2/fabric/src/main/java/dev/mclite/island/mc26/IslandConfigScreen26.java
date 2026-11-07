package dev.mclite.island.mc26;

import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 模组设置页：圆角卡片 + 自绘按钮，两列布局。 */
final class IslandConfigScreen26 extends Screen {

    private final McliteIsland26Client client;
    private final PlaybackService service;
    private final IslandConfig config;
    private final Runnable saveConfig;

    private int panelX;
    private int panelY;

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
        int w = 640;
        int h = 430;
        panelX = (width - w) / 2;
        panelY = (height - h) / 2;
        int left = panelX + 24;
        int right = panelX + 340;
        int y = panelY + 78;

        addRenderableWidget(UiButton26.ghost(label("灵动岛", config.showIsland), left, y, 150, 22,
                () -> {
                    config.showIsland = !config.showIsland;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost(label("上下文", config.showContext), left, y + 30, 150, 22,
                () -> {
                    config.showContext = !config.showContext;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost(label("JEI 集成", config.useJei), left, y + 60, 150, 22,
                () -> {
                    config.useJei = !config.useJei;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost(label("Jade 集成", config.useJade), left, y + 90, 150, 22,
                () -> {
                    config.useJade = !config.useJade;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost(label("自带探针", config.useOwnProbe), left, y + 120, 150, 22,
                () -> {
                    config.useOwnProbe = !config.useOwnProbe;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost(label("水下混响", config.acousticsSubmersion),
                left, y + 180, 150, 22, () -> {
                    config.acousticsSubmersion = !config.acousticsSubmersion;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost(label("室内混响", config.acousticsEnclosure),
                left, y + 210, 150, 22, () -> {
                    config.acousticsEnclosure = !config.acousticsEnclosure;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost(label("雨声混响", config.acousticsRainfall),
                left, y + 240, 150, 22, () -> {
                    config.acousticsRainfall = !config.acousticsRainfall;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost("3D 歌词（26.x 暂未支持）", left, y + 150, 150, 22,
                () -> {
                    config.threeDLyrics = !config.threeDLyrics;
                    saveConfig.run();
                }));

        addRenderableWidget(UiButton26.ghost("缩小 -", right, y, 64, 22, () -> {
            config.islandScale = Math.max(0.6f, config.islandScale - 0.1f);
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost("放大 +", right + 72, y, 64, 22, () -> {
            config.islandScale = Math.min(2.0f, config.islandScale + 0.1f);
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost("上移", right, y + 30, 64, 22, () -> {
            config.islandY = Math.max(-40, config.islandY - 4);
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost("下移", right + 72, y + 30, 64, 22, () -> {
            config.islandY = Math.min(120, config.islandY + 4);
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost("音量 -", right, y + 60, 64, 22, () -> {
            service.setVolume(config.volume - 0.1f);
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost("音量 +", right + 72, y + 60, 64, 22, () -> {
            service.setVolume(config.volume + 0.1f);
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost(label("场景声学", config.acousticsEnabled),
                right, y + 120, 136, 22, () -> {
                    config.acousticsEnabled = !config.acousticsEnabled;
                    client.acousticsEnabled(config.acousticsEnabled);
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost("强度 -", right, y + 150, 64, 22, () -> {
            config.acousticsStrength = Math.max(0, config.acousticsStrength - 10);
            client.acousticsEnabled(config.acousticsEnabled);
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost("强度 +", right + 72, y + 150, 64, 22, () -> {
            config.acousticsStrength = Math.min(100, config.acousticsStrength + 10);
            client.acousticsEnabled(config.acousticsEnabled);
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost(label("自动歌词", config.autoLyrics),
                right, y + 180, 136, 22, () -> {
                    config.autoLyrics = !config.autoLyrics;
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost("歌词 -100ms", right, y + 210, 64, 22, () -> {
            config.lyricsOffsetMs -= 100;
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost("歌词 +100ms", right + 72, y + 210, 64, 22, () -> {
            config.lyricsOffsetMs += 100;
            saveConfig.run();
        }));
        addRenderableWidget(UiButton26.ghost("居中复位", right, y + 90, 136, 22, () -> {
            config.islandX = 0;
            config.islandY = 0;
            config.islandScale = 1f;
            saveConfig.run();
        }));

        int bottom = panelY + 330;
        addRenderableWidget(UiButton26.primary(
                client.biliCookies().loggedIn() ? "退出 B 站登录" : "登录 B 站",
                left, bottom, 140, 24, () -> {
                    if (client.biliCookies().loggedIn()) {
                        client.logoutBili();
                        Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                                client, service, config, saveConfig));
                    } else {
                        Minecraft.getInstance().setScreen(new BiliLoginScreen26(client,
                                () -> Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                                        client, service, config, saveConfig))));
                    }
                }));
        addRenderableWidget(UiButton26.ghost("音质: " + config.biliQuality.label(),
                left + 152, bottom, 130, 24, () -> {
                    config.biliQuality = config.biliQuality.next();
                    client.biliCache().setQuality(config.biliQuality);
                    saveConfig.run();
                    Minecraft.getInstance().setScreen(new IslandConfigScreen26(
                            client, service, config, saveConfig));
                }));
        addRenderableWidget(UiButton26.ghost("清除 B 站缓存", left + 294, bottom, 130, 24, () -> {
            try {
                client.biliCache().clear();
            } catch (Exception ignored) {
                // 忽略缓存清理失败
            }
        }));
        addRenderableWidget(UiButton26.primary("完成", panelX + 270, panelY + 380, 100, 24,
                this::onClose));
    }

    private static String label(String name, boolean on) {
        return name + ": " + (on ? "开" : "关");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY, 640, 430, 20);
        var font = minecraft.font;
        extractor.text(font, "MCLite Island 设置", panelX + 24, panelY + 20, 0xFFF2F5F8, false);
        String sub = "缩放 " + String.format("%.1f", config.islandScale)
                + " · 音量 " + String.format("%.0f%%", config.volume * 100)
                + " · 位置 " + config.islandX + "," + config.islandY;
        extractor.text(font, sub, panelX + 24, panelY + 42, 0xFF93A0AD, false);
        extractor.text(font, "— 播放 —", panelX + 340, panelY + 42, 0xFF6CC6FF, false);
        String bili = client.biliCookies().loggedIn() ? "B站已登录" : "B站未登录（播放可用）";
        extractor.text(font, bili, panelX + 24, panelY + 290, 0xFF93A0AD, false);
        extractor.text(font, "3D 歌词与 JEI/Jade 均可单独关闭；B 站音频需要 ffmpeg",
                panelX + 24, panelY + 360, 0xFF93A0AD, false);
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
