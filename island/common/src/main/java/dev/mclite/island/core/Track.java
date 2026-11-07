package dev.mclite.island.core;

import java.nio.file.Path;
import java.util.Objects;

/** 一首本地曲目。 */
public record Track(Path file, String title, String artist, long durationMs,
                    String bvid, int cid, int audioId, String remoteUrl) {

    public Track(Path file, String title, String artist) {
        this(file, title, artist, -1L, null, 0, 0, null);
    }

    public Track {
        if (file == null && (bvid == null || bvid.isBlank())) {
            throw new IllegalArgumentException("本地/在线来源至少要有一种");
        }
        if (file != null && (title == null || title.isBlank())) {
            title = file.getFileName().toString();
        }
        if (title == null) {
            title = "未命名";
        }
        if (artist == null) {
            artist = "";
        }
    }

    public boolean isRemote() {
        return file == null;
    }

    public static Track remote(String bvid, int cid, int audioId, String remoteUrl,
                               String title, String artist, long durationMs) {
        return new Track(null, title, artist, durationMs, bvid, cid, audioId, remoteUrl);
    }

    public String displayName() {
        return artist.isBlank() ? title : artist + " - " + title;
    }

    public String fileName() {
        return file == null ? title : file.getFileName().toString();
    }
}
