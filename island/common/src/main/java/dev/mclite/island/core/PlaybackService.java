package dev.mclite.island.core;

import dev.mclite.island.core.audio.AudioEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<Path, Lyrics> lyricCache = new ConcurrentHashMap<>();

    private final Object playLock = new Object();
    private final java.util.concurrent.atomic.AtomicLong playGeneration =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile Lyrics currentLyrics = Lyrics.EMPTY;
    private volatile String contextTitle = "";
    private volatile String contextSubtitle = "";
    private volatile String lastError = "";
    private volatile RemoteAudioResolver remoteResolver;

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

    public void setRemoteResolver(RemoteAudioResolver resolver) {
        this.remoteResolver = resolver;
    }

    /** 重新扫描音乐目录；尽量保留当前曲目。 */
    public List<Track> reloadLibrary() {
        // 歌词文件可能刚被拖进去：清缓存，下次播放/刷新重新读
        lyricCache.clear();
        Path musicDir = config.resolveMusicDir(gameDir);
        List<Track> scanned = MediaScanner.scan(musicDir);
        Track current = playlist.current();
        playlist.replace(scanned);
        if (current != null) {
            int index = scanned.indexOf(current);
            if (index >= 0) {
                playlist.index(index);
            }
            currentLyrics = lyricsFor(current);
        }
        return scanned;
    }

    public Path musicDir() {
        return config.resolveMusicDir(gameDir);
    }

    public Path lyricsDir() {
        return config.resolveLyricsDir(gameDir);
    }

    public synchronized void playAt(int index) {
        Track track = playlist.at(index);
        if (track == null) {
            return;
        }
        playlist.index(index);
        playTrack(track, 0L);
    }

    public synchronized void togglePlay() {
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

    public synchronized void next(boolean manual) {
        Track next = playlist.advance(manual);
        if (next == null) {
            engine.stop();
            currentLyrics = Lyrics.EMPTY;
            return;
        }
        playTrack(next, 0L);
    }

    public synchronized void previous() {
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

    /**
     * 解码器打开/解析头信息可能阻塞（FLAC 等最多几秒），不能在渲染或 tick 线程做。
     * 每次切换递增代号；真正打开时如果代号已经过期就放弃，避免旧任务盖掉新歌。
     */
    private void playTrack(Track track, long startMs) {
        long generation = playGeneration.incrementAndGet();
        currentLyrics = Lyrics.EMPTY;
        Thread worker = new Thread(() -> {
            synchronized (playLock) {
                if (generation != playGeneration.get()) {
                    return;
                }
                try {
                    Path audio;
                    if (track.isRemote()) {
                        RemoteAudioResolver resolver = remoteResolver;
                        if (resolver == null) {
                            throw new IllegalStateException("在线音源未就绪");
                        }
                        audio = resolver.resolve(track);
                    } else {
                        audio = track.file();
                    }
                    engine.play(audio, startMs);
                    Lyrics loaded = lyricsFor(track);
                    if (generation == playGeneration.get()) {
                        currentLyrics = loaded;
                        lastError = "";
                    }
                } catch (Exception ex) {
                    if (generation == playGeneration.get()) {
                        lastError = String.valueOf(ex.getMessage());
                        currentLyrics = Lyrics.EMPTY;
                    }
                }
            }
        }, "mclite-island-play");
        worker.setDaemon(true);
        worker.start();
    }

    private Lyrics lyricsFor(Track track) {
        Path key = track.file();
        if (key == null && track.bvid() != null) {
            key = lyricsDir().resolve("bili_" + track.bvid() + "_" + track.cid() + ".lrc");
        }
        if (key == null) {
            return Lyrics.EMPTY;
        }
        Path cacheKey = key;
        return lyricCache.computeIfAbsent(cacheKey, path -> {
            Path sidecar;
            if (track.isRemote()) {
                sidecar = Files.isRegularFile(cacheKey) ? cacheKey : null;
            } else {
                sidecar = LrcParser.findSidecar(track, lyricsDir());
            }
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
        if (currentLyrics != null && audioFile == null) {
            currentLyrics = Lyrics.EMPTY;
            return;
        }
        lyricCache.remove(audioFile);
        Track current = playlist.current();
        if (current != null && audioFile.equals(current.file())) {
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
