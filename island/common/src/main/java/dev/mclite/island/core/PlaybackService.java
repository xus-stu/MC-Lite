package dev.mclite.island.core;

import dev.mclite.island.core.audio.AudioEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 播放服务：把歌单、音频引擎、歌词、配置粘在一起。
 * 界面和 HUD 只跟这个类打交道。
 */
public final class PlaybackService {

    private final Path gameDir;
    private final IslandConfig config;
    private final AudioEngine engine;
    private final Playlist playlist = new Playlist();
    private final Map<Path, Lyrics> lyricCache = new HashMap<>();

    private volatile Lyrics currentLyrics = Lyrics.EMPTY;
    private volatile String contextTitle = "";
    private volatile String contextSubtitle = "";
    private volatile String lastError = "";

    public PlaybackService(Path gameDir, IslandConfig config, AudioEngine engine) {
        this.gameDir = Objects.requireNonNull(gameDir, "gameDir");
        this.config = Objects.requireNonNull(config, "config");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.playlist.mode(config.playMode);
        engine.setOnEnd(() -> {
            try {
                next(false);
            } catch (Exception ex) {
                lastError = String.valueOf(ex.getMessage());
            }
        });
        reloadLibrary();
        playlist.restoreIndex(config.lastTrackIndex);
        engine.setVolume(config.volume);
    }

    public Playlist playlist() {
        return playlist;
    }

    public IslandConfig config() {
        return config;
    }

    public String lastError() {
        return lastError;
    }

    /** 重新扫描音乐目录；尽量保留当前曲目。 */
    public List<Track> reloadLibrary() {
        Path musicDir = config.resolveMusicDir(gameDir);
        List<Track> scanned = MediaScanner.scan(musicDir);
        Track current = playlist.current();
        playlist.replace(scanned);
        if (current != null) {
            int index = scanned.indexOf(current);
            if (index >= 0) {
                playlist.index(index);
            }
        }
        return scanned;
    }

    public Path musicDir() {
        return config.resolveMusicDir(gameDir);
    }

    public Path lyricsDir() {
        return config.resolveLyricsDir(gameDir);
    }

    public void playAt(int index) {
        Track track = playlist.at(index);
        if (track == null) {
            return;
        }
        playlist.index(index);
        playTrack(track, 0L);
    }

    public void togglePlay() {
        if (engine.isPlaying() && !engine.isPaused()) {
            engine.pause();
            return;
        }
        if (engine.isPaused()) {
            engine.resume();
            return;
        }
        Track current = playlist.current();
        if (current != null) {
            playTrack(current, 0L);
        } else if (!playlist.isEmpty()) {
            playAt(0);
        }
    }

    public void next(boolean manual) {
        Track next = playlist.advance(manual);
        if (next == null) {
            engine.stop();
            currentLyrics = Lyrics.EMPTY;
            return;
        }
        playTrack(next, 0L);
    }

    public void previous() {
        Track previous = playlist.previous();
        if (previous != null) {
            playTrack(previous, 0L);
        }
    }

    public void seek(long positionMs) {
        engine.seek(Math.max(0L, positionMs));
    }

    public void setVolume(float volume) {
        config.volume = Math.max(0f, Math.min(1f, volume));
        engine.setVolume(config.volume);
    }

    public void cycleMode() {
        config.playMode = playlist.cycleMode();
    }

    public void setContext(String title, String subtitle) {
        contextTitle = title == null ? "" : title;
        contextSubtitle = subtitle == null ? "" : subtitle;
    }

    public void clearContext() {
        contextTitle = "";
        contextSubtitle = "";
    }

    private void playTrack(Track track, long startMs) {
        try {
            engine.play(track.file(), startMs);
            currentLyrics = lyricsFor(track);
            lastError = "";
        } catch (Exception ex) {
            lastError = String.valueOf(ex.getMessage());
            currentLyrics = Lyrics.EMPTY;
        }
    }

    private Lyrics lyricsFor(Track track) {
        return lyricCache.computeIfAbsent(track.file(), path -> {
            Path sidecar = LrcParser.findSidecar(track, lyricsDir());
            if (sidecar == null) {
                return Lyrics.EMPTY;
            }
            try {
                return new Lyrics(LrcParser.readDocument(sidecar).lines());
            } catch (IOException ex) {
                return Lyrics.EMPTY;
            }
        });
    }

    /** 清掉某个曲目的歌词缓存，导入新歌词后调用。 */
    public void invalidateLyrics(Path audioFile) {
        lyricCache.remove(audioFile);
        Track current = playlist.current();
        if (current != null && current.file().equals(audioFile)) {
            currentLyrics = lyricsFor(current);
        }
    }

    public IslandSnapshot snapshot() {
        Track current = playlist.current();
        long position = engine.positionMs() + config.lyricsOffsetMs;
        String lyric = "";
        String nextLine = "";
        if (current != null && !currentLyrics.isEmpty()) {
            LrcLine line = currentLyrics.lineAt(position);
            if (line != null) {
                lyric = line.text();
            }
            LrcLine next = currentLyrics.nextLine(position);
            if (next != null) {
                nextLine = next.text();
            }
        }
        if (current == null) {
            return new IslandSnapshot("", "", "", "", 0L, -1L, false, false,
                    contextTitle, contextSubtitle);
        }
        return new IslandSnapshot(current.title(), current.artist(), lyric, nextLine,
                Math.max(0L, position), engine.durationMs(), engine.isPlaying(),
                engine.isPaused(), contextTitle, contextSubtitle);
    }

    public void saveState() {
        Track current = playlist.current();
        config.lastTrackIndex = current == null ? 0 : playlist.index();
        config.lastPositionMs = engine.positionMs();
        config.playMode = playlist.mode();
    }

    public void shutdown() {
        saveState();
        engine.stop();
    }
}
