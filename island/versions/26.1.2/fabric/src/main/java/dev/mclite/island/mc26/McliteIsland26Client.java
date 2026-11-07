package dev.mclite.island.mc26;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.bili.BiliApi;
import dev.mclite.island.bili.BiliAudioCache;
import dev.mclite.island.bili.BiliCookies;
import dev.mclite.island.audio.AcousticEnvironment;
import dev.mclite.island.audio.ReverbEffect;
import dev.mclite.island.core.audio.JavaSoundEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.file.Files;
import java.nio.file.Path;

/** 26.1.2 客户端入口：新 HUD 元素注册 + 新 KeyMapping.Category。 */
public final class McliteIsland26Client implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mclite-island", "keys"));

    private IslandConfig config;
    private PlaybackService service;
    private Path configFile;

    private BiliApi bili;
    private BiliCookies biliCookies;
    private BiliAudioCache biliCache;
    private JavaSoundEngine audioEngine;
    private final ReverbEffect reverb = new ReverbEffect();
    private int environmentTicks;
    private Path biliCookieFile;

    private KeyMapping keyOpen;
    private KeyMapping keyToggle3d;
    private KeyMapping keyPrev;
    private KeyMapping keyNext;
    private KeyMapping keyVolumeUp;
    private KeyMapping keyVolumeDown;
    private KeyMapping keyPause;

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        configFile = gameDir.resolve("config").resolve("mclite-island.json");
        config = IslandConfig.load(configFile);
        try {
            Files.createDirectories(config.resolveMusicDir(gameDir));
            Files.createDirectories(config.resolveLyricsDir(gameDir));
        } catch (Exception ignored) {
            // 空歌单时界面会提示
        }
        audioEngine = new JavaSoundEngine();
        service = new PlaybackService(gameDir, config, audioEngine);
        service.setVolume(config.volume);

        biliCookieFile = gameDir.resolve("config").resolve("mclite-island").resolve("bilibili.json");
        biliCookies = BiliCookies.load(biliCookieFile);
        bili = new BiliApi(biliCookies);
        biliCache = new BiliAudioCache(gameDir.resolve("mclite-island").resolve("cache").resolve("bili"),
                bili, config.biliQuality);
        service.setRemoteResolver(biliCache);
        service.setOnlineLyricsProvider(track -> track.isRemote() && track.bvid() != null
                ? bili.subtitleLrc(track.bvid(), track.cid()) : null);
        audioEngine.setEffect(config.acousticsEnabled ? reverb : null);

        registerKeys();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mclite-island", "island"),
                (extractor, delta) -> IslandHud26.render(extractor, config, service));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> shutdown());
    }

    private void registerKeys() {
        keyOpen = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcliteisland.open", InputConstants.Type.KEYSYM, 77, CATEGORY));
        keyToggle3d = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcliteisland.toggle3d", InputConstants.Type.KEYSYM, 86, CATEGORY));
        keyPrev = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcliteisland.previous", InputConstants.Type.KEYSYM, 266, CATEGORY));
        keyNext = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcliteisland.next", InputConstants.Type.KEYSYM, 267, CATEGORY));
        keyVolumeUp = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcliteisland.volume_up", InputConstants.Type.KEYSYM, 334, CATEGORY));
        keyVolumeDown = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcliteisland.volume_down", InputConstants.Type.KEYSYM, 333, CATEGORY));
        keyPause = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcliteisland.pause", InputConstants.Type.KEYSYM, 332, CATEGORY));
    }

    private void onTick(Minecraft client) {
        while (keyOpen.consumeClick()) {
            client.setScreen(new IslandPlayerScreen26(service, config, this::saveConfig, this));
        }
        while (keyToggle3d.consumeClick()) {
            config.threeDLyrics = !config.threeDLyrics;
            saveConfig();
        }
        while (keyPrev.consumeClick()) {
            service.previous();
        }
        while (keyNext.consumeClick()) {
            service.next(true);
        }
        while (keyVolumeUp.consumeClick()) {
            service.setVolume(config.volume + 0.05f);
            saveConfig();
        }
        while (keyVolumeDown.consumeClick()) {
            service.setVolume(config.volume - 0.05f);
            saveConfig();
        }
        while (keyPause.consumeClick()) {
            service.togglePlay();
        }
        if (--environmentTicks <= 0) {
            environmentTicks = 20;
            updateAcoustics(client);
        }
        if (client.level == null) {
            service.clearContext();
        } else if (config.showContext) {
            updateContext(client);
        } else {
            service.clearContext();
        }
    }

    private void updateContext(Minecraft client) {
        // JEI 列表悬停优先；JEI 缺失时连类都不会去解析。
        if (config.useJei && FabricLoader.getInstance().isModLoaded("jei")) {
            String hovered = jeiHovered();
            if (hovered != null) {
                int split = hovered.indexOf('|');
                String name = split >= 0 ? hovered.substring(0, split) : hovered;
                String namespace = split >= 0 ? hovered.substring(split + 1) : "";
                service.setContext(name, namespace.isBlank() ? "JEI" : "JEI · " + namespace);
                return;
            }
        }
        if (config.useJade && FabricLoader.getInstance().isModLoaded("jade")) {
            String jade = JadeCompat.targetLine();
            if (jade != null) {
                service.setContext(jade, "Jade");
                return;
            }
        }
        if (!config.useOwnProbe) {
            service.clearContext();
            return;
        }
        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            var state = client.level.getBlockState(blockHit.getBlockPos());
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            service.setContext(state.getBlock().getName().getString(),
                    id == null ? "" : id.getNamespace());
            return;
        }
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            String subtitle = entity instanceof LivingEntity living
                    ? String.format("%.0f / %.0f", living.getHealth(), living.getMaxHealth()) : "";
            service.setContext(entity.getType().getDescription().getString(), subtitle);
            return;
        }
        service.clearContext();
    }

    private static java.lang.reflect.Method jeiHoverMethod;

    /**
     * 用反射调用 JEI 桥，避免主类常量池直接引用 JeiCompat——
     * 这样即使 JEI 没装/入口点被提前校验，也不会 NoClassDefFoundError。
     */
    private static String jeiHovered() {
        try {
            if (jeiHoverMethod == null) {
                Class<?> bridge = Class.forName("dev.mclite.island.mc26.JeiCompat");
                java.lang.reflect.Method method = bridge.getDeclaredMethod("hoveredIngredient");
                method.setAccessible(true);
                jeiHoverMethod = method;
            }
            return (String) jeiHoverMethod.invoke(null);
        } catch (Throwable ignored) {
            // JEI 版本不匹配时静默降级到准星探针
            return null;
        }
    }

    BiliApi bili() {
        return bili;
    }

    BiliCookies biliCookies() {
        return biliCookies;
    }

    Path biliCookieFile() {
        return biliCookieFile;
    }

    BiliAudioCache biliCache() {
        return biliCache;
    }

    void acousticsEnabled(boolean enabled) {
        audioEngine.setEffect(enabled ? reverb : null);
    }

    void logoutBili() {
        biliCookies.clear();
        try {
            java.nio.file.Files.deleteIfExists(biliCookieFile);
        } catch (Exception ignored) {
            // 文件删不掉也不影响内存登出
        }
        bili.setCookies(biliCookies);
    }

    private void updateAcoustics(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        var player = client.player;
        boolean submerged = config.acousticsSubmersion && player.isUnderWater();
        boolean raining = config.acousticsRainfall
                && client.level.isRainingAt(player.blockPosition());
        boolean enclosed = config.acousticsEnclosure
                && !client.level.canSeeSky(player.blockPosition());
        reverb.setEnvironment(new AcousticEnvironment(submerged, enclosed, raining, enclosed ? 0.6f : 1f),
                Math.max(0, Math.min(100, config.acousticsStrength)) / 100f);
        audioEngine.setEffect(config.acousticsEnabled ? reverb : null);
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (Exception ignored) {
            // 保存失败不影响播放
        }
    }

    private void shutdown() {
        service.shutdown();
        saveConfig();
    }
}
