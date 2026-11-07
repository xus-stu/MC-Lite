package dev.mclite.island.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 从音乐目录扫描可播放文件。 */
public final class MediaScanner {

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "ogg", "wav", "flac");

    private MediaScanner() {
    }

    public static List<Track> scan(Path musicDir) {
        List<Track> tracks = new ArrayList<>();
        if (musicDir == null || !Files.isDirectory(musicDir)) {
            return tracks;
        }
        try (var walk = Files.walk(musicDir, 8)) {
            walk.filter(Files::isRegularFile)
                    .filter(MediaScanner::isSupported)
                    .forEach(path -> tracks.add(trackOf(path)));
        } catch (IOException ignored) {
            // 扫描失败时返回已找到的部分，调用方负责提示
        }
        tracks.sort(Comparator.comparing(t -> t.displayName().toLowerCase(Locale.ROOT)));
        return tracks;
    }

    public static boolean isSupported(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    public static Track trackOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String title = stem;
        String artist = "";
        int dash = stem.indexOf(" - ");
        if (dash > 0) {
            artist = stem.substring(0, dash).trim();
            title = stem.substring(dash + 3).trim();
        }
        return new Track(file, title, artist);
    }
}
