package dev.mclite.island.mc26;

import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.Track;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.lwjgl.glfw.GLFW;

/** 手动填写 .lrc 绝对路径导入，避开 macOS 上 AWT FileDialog 不稳定。 */
final class LrcImportScreen26 extends Screen {

    private static final int TEXT = 0xFFF2F5F8;
    private static final int DIM = 0xFF93A0AD;
    private static final int MUTED = 0xFF6B7885;
    private static final int ERROR = 0xFFFF8A8A;

    private final PlaybackService service;
    private final Runnable saveConfig;
    private EditBox pathBox;
    private String status = "粘贴 .lrc 文件的完整路径";
    private final UiText26 statusCache = new UiText26();
    private int panelX;
    private int panelY;
    private UISurface26.Surface background;

    LrcImportScreen26(PlaybackService service, Runnable saveConfig) {
        super(Component.literal("导入歌词"));
        this.service = service;
        this.saveConfig = saveConfig;
    }

    @Override
    protected void init() {
        panelX = Math.max(8, (width - UiPaint26.LRC_W) / 2);
        panelY = Math.max(8, (height - UiPaint26.LRC_H) / 2);
        background = UISurface26.get("lrc", UiPaint26.LRC_W, UiPaint26.LRC_H,
                UiPaint26::lrc);
        pathBox = new EditBox(minecraft.font, panelX + 36, panelY + 72, 488, 20,
                Component.literal("LRC 路径"));
        pathBox.setMaxLength(400);
        pathBox.setBordered(false);
        pathBox.setTextColor(TEXT);
        pathBox.setHint(Component.literal("/Users/you/Music/song.lrc"));
        addRenderableWidget(pathBox);
        addRenderableWidget(UiButton26.primary("导入", panelX + 24, panelY + 112, 90, 26,
                this::doImport));
        addRenderableWidget(UiButton26.ghost("取消", panelX + 122, panelY + 112, 90, 26,
                this::onClose));
        addRenderableWidget(UiButton26.ghost("歌词目录", panelX + 220, panelY + 112, 96, 26,
                this::openLyricsFolder));
    }

    private void doImport() {
        Track current = service.playlist().current();
        if (current == null) {
            status = "先播放一首歌，再导入同名 LRC";
            return;
        }
        String raw = pathBox == null ? "" : pathBox.getValue().trim();
        if (raw.isEmpty()) {
            status = "路径为空";
            return;
        }
        try {
            Path source = Path.of(raw);
            if (!Files.isRegularFile(source)) {
                status = "文件不存在: " + source;
                return;
            }
            String stem = current.fileName();
            int dot = stem.lastIndexOf('.');
            stem = dot > 0 ? stem.substring(0, dot) : stem;
            Path target = service.lyricsDir().resolve(stem + ".lrc");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            service.invalidateLyrics(current.file());
            status = "已导入: " + target.getFileName();
            saveConfig.run();
        } catch (Exception ex) {
            status = "导入失败: " + ex.getMessage();
        }
    }

    private void openLyricsFolder() {
        try {
            Path dir = service.lyricsDir();
            Files.createDirectories(dir);
            java.awt.Desktop.getDesktop().open(dir.toFile());
            status = "已打开歌词目录";
        } catch (Exception failure) {
            status = "打不开目录: " + failure.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        var font = minecraft.font;
        if (background != null) {
            UISurface26.draw(extractor, background, panelX, panelY);
        } else {
            UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY,
                    UiPaint26.LRC_W, UiPaint26.LRC_H, 20);
        }
        drawScaledText(extractor, font, "导入 LRC 歌词", panelX + 44, panelY + 16, 1.18f, TEXT);
        extractor.text(font, "填写歌词文件路径；同名 .lrc 也可直接放到歌词目录", panelX + 44,
                panelY + 37, DIM, false);
        statusCache.set(font, status, 300);
        extractor.text(font, statusCache.text(), panelX + 24, panelY + 164,
                status.startsWith("导入失败") || status.startsWith("文件不存在")
                        || status.startsWith("打不开") ? ERROR : DIM, false);
        extractor.text(font, "支持绝对路径 / ~ 不展开", panelX + UiPaint26.LRC_W - 24
                - font.width("支持绝对路径 / ~ 不展开"), panelY + 164, MUTED, false);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private static void drawScaledText(GuiGraphicsExtractor extractor,
                                       net.minecraft.client.gui.Font font, String text,
                                       int x, int y, float scale, int color) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        extractor.text(font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (pathBox != null && pathBox.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            doImport();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
