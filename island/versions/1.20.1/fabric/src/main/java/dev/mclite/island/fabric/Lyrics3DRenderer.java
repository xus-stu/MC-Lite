package dev.mclite.island.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 世界空间 3D 歌词：面向摄像机的 billboard 文字，不用后处理。 */
final class Lyrics3DRenderer {

    private Lyrics3DRenderer() {
    }

    static void render(WorldRenderContext context, IslandConfig config, PlaybackService service) {
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
        PoseStack pose = context.matrixStack();
        if (pose == null) {
            return;
        }
        Camera camera = context.camera();
        Vec3 cameraPos = camera.getPosition();

        Vec3 look = Vec3.directionFromRotation(client.player.getXRot(), client.player.getYRot());
        Vec3 position = client.player.getEyePosition()
                .add(look.scale(config.lyricsDistance))
                .add(0.0, config.lyricsVerticalOffset, 0.0);

        pose.pushPose();
        pose.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
        pose.mulPose(camera.rotation());
        float scale = 0.025f * config.lyricsScale;
        pose.scale(-scale, -scale, scale);
        Matrix4f matrix = pose.last().pose();

        MultiBufferSource buffer = context.consumers();
        if (buffer == null) {
            pose.popPose();
            return;
        }
        Font font = client.font;
        int color = config.lyricsColor;
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, 0f, color, true, matrix, buffer,
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);

        String next = snapshot.nextLyric();
        if (config.lyricsMaxLines > 1 && next != null && !next.isBlank()) {
            int nextColor = (color & 0x00FFFFFF) | 0x99000000;
            font.drawInBatch(next, -font.width(next) / 2f, 12f, nextColor, true, matrix, buffer,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
}
