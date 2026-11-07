package dev.mclite.island.mc26;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.PlaybackService;
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
        service = new PlaybackService(gameDir, config, new JavaSoundEngine());
        service.setVolume(config.volume);

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
            client.setScreen(new IslandPlayerScreen26(service, config, this::saveConfig));
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
