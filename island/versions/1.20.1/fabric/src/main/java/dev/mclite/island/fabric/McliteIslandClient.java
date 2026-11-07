package dev.mclite.island.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.audio.JavaSoundEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.file.Files;
import java.nio.file.Path;

/** 客户端入口：接线、按键、HUD、3D 歌词。 */
public final class McliteIslandClient implements ClientModInitializer {

    private static final String CATEGORY = "key.mcliteisland.category";
    private static McliteIslandClient instance;

    private IslandConfig config;
    private PlaybackService service;
    private Path configFile;

    private KeyMapping keyOpen;
    private KeyMapping keyToggle3d;
    private KeyMapping keyPrev;
    private KeyMapping keyNext;
    private KeyMapping keyVolumeUp;
    private KeyMapping keyVolumeDown;
    private KeyMapping keyPause;

    public static McliteIslandClient get() {
        return instance;
    }

    public IslandConfig config() {
        return config;
    }

    public PlaybackService service() {
        return service;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        Minecraft client = Minecraft.getInstance();
        Path gameDir = FabricLoader.getInstance().getGameDir();
        configFile = gameDir.resolve("config").resolve("mclite-island.json");
        config = IslandConfig.load(configFile);

        try {
            Files.createDirectories(config.resolveMusicDir(gameDir));
            Files.createDirectories(config.resolveLyricsDir(gameDir));
        } catch (Exception ignored) {
            // 目录建不出来时播放器会提示空歌单
        }

        service = new PlaybackService(gameDir, config, new JavaSoundEngine());
        service.setVolume(config.volume);

        registerKeys();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> IslandHud.render(graphics, config, service));
        WorldRenderEvents.LAST.register(ctx -> Lyrics3DRenderer.render(ctx, config, service));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(c -> shutdown());
    }

    private void registerKeys() {
        keyOpen = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mcliteisland.open", InputConstants.Type.KEYSYM, 77, CATEGORY));
        keyToggle3d = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mcliteisland.toggle3d", InputConstants.Type.KEYSYM, 86, CATEGORY));
        keyPrev = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mcliteisland.previous", InputConstants.Type.KEYSYM, 266, CATEGORY));
        keyNext = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mcliteisland.next", InputConstants.Type.KEYSYM, 267, CATEGORY));
        keyVolumeUp = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mcliteisland.volume_up", InputConstants.Type.KEYSYM, 334, CATEGORY));
        keyVolumeDown = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mcliteisland.volume_down", InputConstants.Type.KEYSYM, 333, CATEGORY));
        keyPause = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mcliteisland.pause", InputConstants.Type.KEYSYM, 332, CATEGORY));
    }

    private void onTick(Minecraft client) {
        while (keyOpen.consumeClick()) {
            client.setScreen(new IslandPlayerScreen(service, config, this::saveConfig));
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
        if (client.level != null && config.showContext) {
            updateContext(client);
        }
    }

    private void updateContext(Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            var state = client.level.getBlockState(blockHit.getBlockPos());
            String name = state.getBlock().getName().getString();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String subtitle = id == null ? "" : id.getNamespace();
            service.setContext(name, subtitle);
            return;
        }
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            String name = entity.getType().getDescription().getString();
            String subtitle = entity instanceof LivingEntity living
                    ? String.format("%.0f / %.0f", living.getHealth(), living.getMaxHealth())
                    : "";
            service.setContext(name, subtitle);
            return;
        }
        service.clearContext();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (Exception ignored) {
            // 保存失败不阻塞播放
        }
    }

    private void shutdown() {
        service.shutdown();
        saveConfig();
    }

    /** 给屏幕用的状态文本。 */
    static Component text(String value) {
        return Component.literal(value == null ? "" : value);
    }
}
