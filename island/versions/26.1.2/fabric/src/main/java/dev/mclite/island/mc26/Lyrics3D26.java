package dev.mclite.island.mc26;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 世界空间 3D 歌词：面向摄像机的 billboard 文字。
 *
 * <p>挂在 {@code LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES} 上，直接写
 * Fabric 提供的 bufferSource，不碰全局立即缓冲，也不做后处理。</p>
 */
final class Lyrics3D26 {

    private static final int LIGHT = 0xF000F0;

    private Lyrics3D26() {
    }

    static void render(LevelRenderContext context, IslandConfig config, PlaybackService service) {
        if (config == null || service == null || !config.threeDLyrics) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.options.hideGui) {
            return;
        }
        IslandSnapshot snapshot = service.snapshot();
        if (!snapshot.hasTrack()) {
            return;
        }
        String text = snapshot.lyric();
        if (text == null || text.isBlank()) {
            return;
        }
        PoseStack pose = context.poseStack();
        if (pose == null) {
            return;
        }
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        Vec3 look = client.player.getViewVector(1.0f);
        Vec3 position = client.player.getEyePosition()
                .add(look.scale(config.lyricsDistance))
                .add(0.0, config.lyricsVerticalOffset, 0.0);

        pose.pushPose();
        pose.translate(position.x - cameraPos.x, position.y - cameraPos.y,
                position.z - cameraPos.z);
        pose.mulPose(camera.rotation());
        float scale = 0.025f * config.lyricsScale;
        pose.scale(-scale, -scale, scale);
        Matrix4f matrix = pose.last().pose();

        Font font = client.font;
        MultiBufferSource buffer = context.bufferSource();
        int color = config.lyricsColor;
        font.drawInBatch(text, -font.width(text) / 2f, 0f, color, true, matrix, buffer,
                Font.DisplayMode.SEE_THROUGH, 0, LIGHT);

        String next = snapshot.nextLyric();
        if (config.lyricsMaxLines > 1 && next != null && !next.isBlank()) {
            int nextColor = (color & 0x00FFFFFF) | 0x99000000;
            font.drawInBatch(next, -font.width(next) / 2f, 12f, nextColor, true, matrix, buffer,
                    Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
        }
        pose.popPose();
    }
}
