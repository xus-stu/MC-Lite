package dev.mclite.island.mc26;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mclite.island.core.IslandConfig;
import dev.mclite.island.core.IslandSnapshot;
import dev.mclite.island.core.PlaybackService;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 世界空间 3D 悬浮歌词：在 LevelRenderer 画完、画手之前，用世界投影把文字
 * 画在玩家前方的一个固定世界锚点上；朝向镜头、随距离缩放，不是贴在屏幕上。
 *
 * <p>位置和渲染时机参考 DangoMusic 的 worldRenderEnd。</p>
 */
public final class LyricsWorld26 {

    private static IslandConfig config;
    private static PlaybackService service;
    private static String lastText = "";
    private static Vec3 anchor;
    private static long changeNanos;

    private LyricsWorld26() {
    }

    public static void init(IslandConfig islandConfig, PlaybackService playbackService) {
        config = islandConfig;
        service = playbackService;
    }

    public static void renderWorld() {
        if (config == null || service == null || !config.threeDLyrics) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.options.hideGui) {
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
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        if (!text.equals(lastText) || anchor == null || anchor.distanceTo(eye) > 18.0) {
            lastText = text;
            anchor = eye.add(look.scale(Math.max(3.0, config.lyricsDistance)))
                    .add(0.0, config.lyricsVerticalOffset, 0.0);
            changeNanos = System.nanoTime();
        }
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        double rx = anchor.x - cam.x;
        double ry = anchor.y - cam.y;
        double rz = anchor.z - cam.z;
        double length = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (length < 0.2) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-rx, rz));
        float pitch = (float) Math.toDegrees(Math.atan2(-ry, Math.sqrt(rx * rx + rz * rz)));

        PoseStack pose = new PoseStack();
        pose.mulPose(new Quaternionf()
                .rotateX((float) Math.toRadians(camera.xRot()))
                .rotateY((float) Math.toRadians(camera.yRot() + 180.0)));
        pose.translate((float) rx, (float) ry, (float) rz);
        pose.mulPose(new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch)));
        float scale = 0.03f * Math.max(0.4f, config.lyricsScale);
        pose.scale(-scale, -scale, scale);

        float fade = Math.min(1f, (System.nanoTime() - changeNanos) / 260_000_000f);
        int alpha = (int) (0xFF * fade);
        int color = withAlpha(config.lyricsColor, alpha);
        Font font = client.font;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        try (ByteBufferBuilder builder = new ByteBufferBuilder(2048)) {
            MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(builder);
            font.drawInBatch(text, -font.width(text) / 2f, 0f, color, true,
                    pose.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            String next = snapshot.nextLyric();
            if (config.lyricsMaxLines > 1 && next != null && !next.isBlank()) {
                font.drawInBatch(next, -font.width(next) / 2f, 12f,
                        withAlpha(config.lyricsColor, alpha * 2 / 3), true,
                        pose.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            buffer.endBatch();
        } finally {
            modelView.popMatrix();
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha))) << 24);
    }
}
