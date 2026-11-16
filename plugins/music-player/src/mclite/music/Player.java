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
 * <p>暂停不再靠阻塞 write（那会让解码线程卡死、表现为乱切歌）：读线程只负责把
 * PCM 塞进缓冲（超过约 2 秒就停手，靠管道反压），写线程在暂停时跳过消费。</p>
 */
final class Player {

    enum Mode {
        LIST("列表循环"), SINGLE("单曲循环"), SHUFFLE("随机播放"), SEQUENCE("顺序播放");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    interface Listener {
        void onTrack(Bili.Song song, int index);

        void onState(boolean playing);

        void onError(String message);

        void onQueueChanged();
    }

    private static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 2, true, false);
    private static final int BUFFER_LIMIT = 384 * 1024;

    private final List<Bili.Song> queue = new ArrayList<>();
    private final Random random = new Random();
    private final Reverb reverb = new Reverb();
    private final Effects effects = new Effects();
    private Listener listener;
    private Mode mode = Mode.LIST;
    private int index = -1;
    private long token;
    private Process process;
    private SourceDataLine line;
    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean eof;

    Player(Listener listener) {
        this.listener = listener;
    }

    void listener(Listener listener) {
        this.listener = listener;
    }

    List<Bili.Song> queue() {
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

    void enqueue(List<Bili.Song> songs) {
        queue.addAll(songs);
        listener.onQueueChanged();
    }

    /** 删除一首；删的是当前曲目就顺延播放，队列空则停止。 */
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
        if (target < 0 || target >= queue.size()) {
            return;
        }
        index = target;
        start(queue.get(target));
    }

    void toggle() {
        if (line == null) {
            if (index >= 0 && index < queue.size()) {
                play(index);
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
        play(target);
    }

    void previous() {
        if (queue.isEmpty()) {
            return;
        }
        play((index - 1 + queue.size()) % queue.size());
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

    private void start(Bili.Song song) {
        stop();
        long mine = ++token;
        listener.onTrack(song, index);
        Thread thread = new Thread(() -> stream(song, mine), "mclite-music");
        thread.setDaemon(true);
        thread.start();
    }

    private void stream(Bili.Song song, long mine) {
        ArrayDeque<byte[]> chunks = new ArrayDeque<>();
        int[] queued = {0};
        SourceDataLine audio;
        try {
            Path ffmpeg = ffmpeg();
            if (ffmpeg == null) {
                finish(mine, "找不到 ffmpeg：请先 `brew install ffmpeg`");
                return;
            }
            java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                    ffmpeg.toString(), "-nostdin", "-loglevel", "quiet"));
            if (song.url().startsWith("http")) {
                // 只有 HTTP 输入才需要防盗链头；本地文件传 headers 会让 ffmpeg 直接报错。
                command.add("-headers");
                command.add("Referer: https://www.bilibili.com/\r\nUser-Agent: Mozilla/5.0\r\n");
            }
            command.addAll(java.util.List.of("-i", song.url(),
                    "-vn", "-f", "s16le", "-ac", "2", "-ar", "44100", "pipe:1"));
            Process proc = new ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .start();
            process = proc;
            audio = AudioSystem.getSourceDataLine(FORMAT);
            audio.open(FORMAT, 96000);
            audio.start();
            line = audio;
            playing = true;
            paused = false;
            eof = false;
            listener.onState(true);

            long[] produced = {0L};
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
            }
            if (mine == token) {
                try {
                    audio.drain();
                } catch (RuntimeException ignored) {
                    // 忽略。
                }
                if (produced[0] <= 0) {
                    // 解码 0 字节（链接 403/过期、ffmpeg 参数错误等）：
                    // 必须报错停止，否则会被当成“播完”无限跳下一首。
                    finish(mine, "解码没有输出：音频链接可能已失效（或 ffmpeg 无法打开）");
                } else {
                    finish(mine, null);
                }
            }
        } catch (Exception ex) {
            if (mine == token) {
                finish(mine, "播放失败：" + ex.getMessage());
            }
        } finally {
            try {
                if (line != null) {
                    line.close();
                }
            } catch (RuntimeException ignored) {
                // 忽略。
            }
        }
    }

    private synchronized void finish(long mine, String error) {
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
        index = target;
        start(queue.get(index));
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
