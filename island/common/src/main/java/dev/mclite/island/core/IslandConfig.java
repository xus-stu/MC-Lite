package dev.mclite.island.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * 模组配置。字段全部 public 是给 Gson 用的；新增字段直接给默认值即可，
 * 旧配置文件缺字段时不会报错。
 */
public final class IslandConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 相对游戏目录的音乐文件夹。 */
    public String musicFolder = "mclite-island/music";
    /** 相对游戏目录的歌词文件夹。 */
    public String lyricsFolder = "mclite-island/lyrics";

    public float volume = 0.7f;
    public PlayMode playMode = PlayMode.SEQUENTIAL;
    public int lastTrackIndex = 0;
    public long lastPositionMs = 0L;

    // 灵动岛
    public boolean showIsland = true;
    public int islandX = 0;
    public int islandY = 0;
    public float islandScale = 1.0f;
    /** 播放音乐时是否也让 JEI/Jade 上下文占用灵动岛。 */
    public boolean contextWhilePlaying = false;
    public boolean showContext = true;
    /** JEI 物品悬停集成（装了 JEI 才有效）。 */
    public boolean useJei = true;
    /** Jade 目标提示集成（装了 Jade 才有效）。 */
    public boolean useJade = true;

    // 3D 歌词
    public boolean threeDLyrics = false;
    public float lyricsDistance = 2.5f;
    public float lyricsScale = 1.0f;
    public int lyricsColor = 0xFFFFFFFF;
    public int lyricsMaxLines = 2;
    public long lyricsOffsetMs = 0L;
    /** 3D 歌词显示在视线下方多少格（0 表示视线中心）。 */
    public float lyricsVerticalOffset = -0.35f;

    public Path resolveMusicDir(Path gameDir) {
        return gameDir.resolve(musicFolder).toAbsolutePath().normalize();
    }

    public Path resolveLyricsDir(Path gameDir) {
        return gameDir.resolve(lyricsFolder).toAbsolutePath().normalize();
    }

    public static IslandConfig load(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            return new IslandConfig();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            IslandConfig loaded = GSON.fromJson(json, IslandConfig.class);
            return loaded == null ? new IslandConfig() : loaded.sanitized();
        } catch (IOException | JsonSyntaxException ex) {
            backupBroken(file);
            return new IslandConfig();
        }
    }

    public void save(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public IslandConfig sanitized() {
        volume = clamp(volume, 0f, 1f);
        islandScale = clamp(islandScale, 0.5f, 3f);
        lyricsDistance = clamp(lyricsDistance, 0.5f, 8f);
        lyricsScale = clamp(lyricsScale, 0.25f, 4f);
        lyricsMaxLines = Math.max(1, Math.min(4, lyricsMaxLines));
        lyricsVerticalOffset = clamp(lyricsVerticalOffset, -4f, 4f);
        islandX = Math.max(0, Math.min(4096, islandX));
        islandY = Math.max(0, Math.min(4096, islandY));
        if (playMode == null) {
            playMode = PlayMode.SEQUENTIAL;
        }
        if (musicFolder == null || musicFolder.isBlank()) {
            musicFolder = "mclite-island/music";
        }
        if (lyricsFolder == null || lyricsFolder.isBlank()) {
            lyricsFolder = "mclite-island/lyrics";
        }
        return this;
    }

    private static void backupBroken(Path file) {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // 备份失败不影响用默认配置启动
        }
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
