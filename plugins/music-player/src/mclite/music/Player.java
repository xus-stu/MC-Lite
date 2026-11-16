package mclite.music;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 播放内核：ffmpeg 解码 → 环形缓冲 → JavaSound。
 *
 * <p>要点：暂停靠缓冲（写线程暂停时跳过消费）；进度按已写字节数算；seek 用
 * ffmpeg 的 -ss 重新起播；结尾提前 EOF 会判定为异常并刷新地址续播一次。</p>
 */
final class Player {

    enum Mode {
        LIST("列表循环"), SINGLE("单曲循环"), SHUFFLE("随机播放"), SEQUENCE("顺序播放");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** 队列里的歌：只存元数据，播放时再换新地址（B 站链接会过期）。 */
    record Track(String bvid, String title, String author, String cover, long seconds,
                 String quality) {
    }

    interface Resolver {
        Bili.Song resolve(Track track) throws Exception;
    }

    interface Listener {
        void onTrack(Track track, int index);

        void onState(boolean playing);

        void onError(String message);

        void onQueueChanged();

        void onRetry(String message);
    }

    private static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 2, true, false);
    private static final int BUFFER_LIMIT = 384 * 1024;
    private static final long BYTES_PER_SECOND = 44100L * 4L;

    private final List<Track> queue = new ArrayList<>();
    private final Random random = new Random();
    private final Reverb reverb = new Reverb();
    private final Effects effects = new Effects();
    private Listener listener;
    private Resolver resolver;
    private Mode mode = Mode.LIST;
    private int index = -1;
    private long token;
    private Process process;
    private SourceDataLine line;
    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean eof;
    private volatile long baseSeconds;
    private volatile long writtenBytes;

    Player(Listener listener) {
        this.listener = listener;
    }

    void listener(Listener listener) {
        this.listener = listener;
    }

    void resolver(Resolver resolver) {
        this.resolver = resolver;
    }

    List<Track> queue() {
        return queue;
    }

    Mode mode() {
        return mode;
    }

    void mode(Mode next) {
        mode = next;
    }

    int index() {
        return index;
    }

    boolean playing() {
        return playing;
    }

    boolean paused() {
        return paused;
    }

    Reverb reverb() {
        return reverb;
    }

    Effects effects() {
        return effects;
    }

    long positionSeconds() {
        return baseSeconds + writtenBytes / BYTES_PER_SECOND;
    }

    long durationSeconds() {
        return index >= 0 && index < queue.size() ? queue.get(index).seconds() : 0L;
    }

    void enqueue(List<Track> tracks) {
        queue.addAll(tracks);
        listener.onQueueChanged();
    }

    void remove(int target) {
        if (target < 0 || target >= queue.size()) {
            return;
        }
        queue.remove(target);
        if (queue.isEmpty()) {
            stop();
            index = -1;
        } else if (target < index) {
            index--;
        } else if (target == index) {
            int next = Math.min(index, queue.size() - 1);
            if (playing || paused) {
                play(next);
            } else {
                index = next;
            }
        }
        listener.onQueueChanged();
    }

    void clear() {
        stop();
        queue.clear();
        index = -1;
        listener.onQueueChanged();
    }

    void play(int target) {
        playFrom(target, 0L, 0);
    }

    void seek(long seconds) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        playFrom(index, Math.max(0L, seconds), 0);
    }

    void restore(List<Track> tracks, int savedIndex, long savedPosition, Mode savedMode,
                 boolean autoplay) {
        queue.clear();
        queue.addAll(tracks);
        mode = savedMode == null ? Mode.LIST : savedMode;
        index = Math.max(-1, Math.min(savedIndex, queue.size() - 1));
        listener.onQueueChanged();
        if (index >= 0) {
            long position = Math.max(0L, savedPosition);
            Track track = queue.get(index);
            if (position > 0 && track.seconds() > 0 && position >= track.seconds() - 2) {
                position = 0L;
            }
            if (autoplay) {
                playFrom(index, position, 0);
            } else {
                baseSeconds = position;
                writtenBytes = 0L;
                listener.onTrack(track, index);
            }
        }
    }

    private void playFrom(int target, long seconds, int attempt) {
        if (target < 0 || target >= queue.size()) {
            return;
        }
        index = target;
        baseSeconds = Math.max(0L, seconds);
        writtenBytes = 0L;
        start(queue.get(index), baseSeconds, attempt);
    }

    void toggle() {
        if (line == null) {
            if (index >= 0 && index < queue.size()) {
                playFrom(index, positionSeconds(), 0);
            }
            return;
        }
        paused = !paused;
        playing = !paused;
        if (paused) {
            line.stop();
        } else {
            line.start();
        }
        listener.onState(playing);
    }

    void next(boolean manual) {
        if (queue.isEmpty()) {
            return;
        }
        int target = advance(manual);
        if (target < 0) {
            stop();
            return;
        }
        playFrom(target, 0L, 0);
    }

    void previous() {
        if (queue.isEmpty()) {
            return;
        }
        playFrom((index - 1 + queue.size()) % queue.size(), 0L, 0);
    }

    private int advance(boolean manual) {
        if (queue.isEmpty()) {
            return -1;
        }
        return switch (mode) {
            case SINGLE -> manual ? (index + 1) % queue.size() : index;
            case SEQUENCE -> index + 1 < queue.size() ? index + 1 : -1;
            case SHUFFLE -> {
                if (queue.size() == 1) {
                    yield 0;
                }
                int candidate;
                do {
                    candidate = random.nextInt(queue.size());
                } while (candidate == index);
                yield candidate;
            }
            default -> (index + 1) % queue.size();
        };
    }

    void stop() {
        token++;
        playing = false;
        paused = false;
        eof = false;
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (RuntimeException ignored) {
                // 已关闭。
            }
            line = null;
        }
        listener.onState(false);
    }

    private void start(Track track, long startSeconds, int attempt) {
        stop();
        long mine = ++token;
        listener.onTrack(track, index);
        Thread thread = new Thread(() -> stream(track, mine, startSeconds, attempt),
                "mclite-music");
        thread.setDaemon(true);
        thread.start();
    }

    private void stream(Track track, long mine, long startSeconds, int attempt) {
        ArrayDeque<byte[]> chunks = new ArrayDeque<>();
        int[] queued = {0};
        long[] produced = {0L};
        SourceDataLine audio = null;
        try {
            Bili.Song song = track.bvid().isBlank() ? null : resolveFresh(track);
            if (song == null || song.url().isBlank()) {
                finish(mine, track, "解析播放地址失败", startSeconds, attempt);
                return;
            }
            Path ffmpeg = ffmpeg();
            if (ffmpeg == null) {
                finish(mine, track, "找不到 ffmpeg：请先 `brew install ffmpeg`", startSeconds, attempt);
                return;
            }
            java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                    ffmpeg.toString(), "-nostdin", "-loglevel", "quiet"));
            if (song.url().startsWith("http")) {
                command.add("-reconnect");
                command.add("1");
                command.add("-reconnect_streamed");
                command.add("1");
                command.add("-reconnect_delay_max");
                command.add("5");
                command.add("-headers");
                command.add("Referer: https://www.bilibili.com/\r\nUser-Agent: Mozilla/5.0\r\n");
            }
            if (startSeconds > 0) {
                command.add("-ss");
                command.add(String.valueOf(startSeconds));
            }
            command.addAll(java.util.List.of("-i", song.url(),
                    "-vn", "-f", "s16le", "-ac", "2", "-ar", "44100", "pipe:1"));
            Process proc = new ProcessBuilder(command).redirectErrorStream(false).start();
            process = proc;
            audio = AudioSystem.getSourceDataLine(FORMAT);
            audio.open(FORMAT, 96000);
            audio.start();
            line = audio;
            playing = true;
            paused = false;
            eof = false;
            listener.onState(true);

            Thread reader = new Thread(() -> {
                try (InputStream in = proc.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while (mine == token && (read = in.read(buffer)) > 0) {
                        synchronized (queued) {
                            chunks.add(java.util.Arrays.copyOf(buffer, read));
                            queued[0] += read;
                            produced[0] += read;
                        }
                        while (mine == token && queued[0] > BUFFER_LIMIT) {
                            Thread.sleep(8);
                        }
                    }
                } catch (Exception ignored) {
                    // 进程结束/被终止都走 EOF。
                } finally {
                    eof = true;
                }
            }, "mclite-music-reader");
            reader.setDaemon(true);
            reader.start();

            while (mine == token) {
                byte[] chunk;
                synchronized (queued) {
                    chunk = chunks.poll();
                    if (chunk != null) {
                        queued[0] -= chunk.length;
                    }
                }
                if (chunk == null) {
                    if (eof) {
                        break;
                    }
                    Thread.sleep(8);
                    continue;
                }
                if (paused) {
                    synchronized (queued) {
                        chunks.addFirst(chunk);
                        queued[0] += chunk.length;
                    }
                    Thread.sleep(12);
                    continue;
                }
                if (effects.enabled()) {
                    effects.process(chunk, chunk.length);
                }
                if (reverb.enabled()) {
                    reverb.process(chunk, chunk.length);
                }
                audio.write(chunk, 0, chunk.length);
                writtenBytes += chunk.length;
            }
            if (mine != token) {
                return;
            }
            try {
                audio.drain();
            } catch (RuntimeException ignored) {
                // 忽略。
            }
            int exit = proc.waitFor();
            long played = startSeconds + produced[0] / BYTES_PER_SECOND;
            long total = track.seconds();
            boolean shortEnd = total > 0 && played + 8 < total && total - played > Math.max(6, total / 20);
            if (produced[0] <= 0) {
                finish(mine, track, "解码没有输出：音频链接可能已失效", startSeconds, attempt);
            } else if (exit != 0 && shortEnd && attempt == 0) {
                // 结尾异常断流（无损/Hi-Res 常见）：刷新地址从断点续播一次。
                listener.onRetry("结尾提前断流，正在重连续播…");
                playFrom(index, played, 1);
            } else if (exit != 0 && shortEnd) {
                finish(mine, track, "播放中断（ffmpeg 退出码 " + exit + "）", startSeconds, attempt);
            } else {
                // 正常播完；音源本身比视频短时也走这里，不做误判。
                finish(mine, track, null, startSeconds, attempt);
            }
        } catch (Exception ex) {
            if (mine == token) {
                finish(mine, track, "播放失败：" + ex.getMessage(), startSeconds, attempt);
            }
        } finally {
            // 只关自己这条线：换了 token 之后 line 字段可能是下一条（seek/续播）。
            try {
                if (audio != null) {
                    audio.close();
                }
            } catch (RuntimeException ignored) {
                // 忽略。
            }
            synchronized (this) {
                if (line == audio) {
                    line = null;
                }
            }
        }
    }

    private Bili.Song resolveFresh(Track track) throws Exception {
        if (resolver == null) {
            return null;
        }
        return resolver.resolve(track);
    }

    private synchronized void finish(long mine, Track track, String error, long startSeconds,
                                     int attempt) {
        if (mine != token) {
            return;
        }
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        line = null;
        playing = false;
        paused = false;
        if (error != null) {
            listener.onError(error);
            listener.onState(false);
            return;
        }
        int target = advance(false);
        if (target < 0) {
            listener.onState(false);
            return;
        }
        playFrom(target, 0L, 0);
    }

    static Path ffmpeg() {
        String path = System.getenv("PATH");
        List<String> candidates = new ArrayList<>();
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                candidates.add(dir + "/ffmpeg");
            }
        }
        candidates.add("/opt/homebrew/bin/ffmpeg");
        candidates.add("/usr/local/bin/ffmpeg");
        candidates.add("/usr/bin/ffmpeg");
        for (String candidate : candidates) {
            Path file = Path.of(candidate);
            if (Files.isExecutable(file)) {
                return file;
            }
        }
        return null;
    }
}
