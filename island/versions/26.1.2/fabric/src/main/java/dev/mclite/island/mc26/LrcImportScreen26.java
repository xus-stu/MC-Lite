package dev.mclite.island.mc26;

import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.Track;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 手动填写 .lrc 绝对路径导入，避开 macOS 上 AWT FileDialog 不稳定。 */
final class LrcImportScreen26 extends Screen {

    private final PlaybackService service;
    private final Runnable saveConfig;
    private EditBox pathBox;
    private String status = "粘贴 .lrc 文件的完整路径";
    private int panelX;
    private int panelY;

    LrcImportScreen26(PlaybackService service, Runnable saveConfig) {
        super(Component.literal("导入歌词"));
        this.service = service;
        this.saveConfig = saveConfig;
    }

    @Override
    protected void init() {
        panelX = (width - 520) / 2;
        panelY = (height - 200) / 2;
        pathBox = new EditBox(minecraft.font, panelX + 24, panelY + 72, 472, 20,
                Component.literal("LRC 路径"));
        pathBox.setMaxLength(400);
        pathBox.setHint(Component.literal("/Users/you/Music/song.lrc"));
        addRenderableWidget(pathBox);
        addRenderableWidget(UiButton26.primary("导入", panelX + 24, panelY + 112, 90, 24,
                this::doImport));
        addRenderableWidget(UiButton26.ghost("取消", panelX + 122, panelY + 112, 90, 24,
                this::onClose));
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        UiDraw26.panel(extractor, UiDraw26.CARD, panelX, panelY, 520, 200, 18);
        var font = minecraft.font;
        extractor.text(font, "导入 LRC 歌词", panelX + 24, panelY + 22, 0xFFF2F5F8, false);
        extractor.text(font, "填写歌词文件路径；同名 .lrc 也可直接放到歌词目录", panelX + 24,
                panelY + 42, 0xFF93A0AD, false);
        extractor.text(font, status, panelX + 24, panelY + 152,
                status.startsWith("导入失败") || status.startsWith("文件不存在") ? 0xFFFF6B6B
                        : 0xFF93A0AD, false);
        super.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
