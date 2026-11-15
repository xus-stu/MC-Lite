package mclite.music;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 播放内核：ffmpeg 解码 B 站音频流，JavaSound 出声；带队列和四种循环模式。 */
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

    private final List<Bili.Song> queue = new ArrayList<>();
    private final Random random = new Random();
    private Listener listener;
    private Mode mode = Mode.LIST;
    private int index = -1;
    private long token;
    private Process process;
    private SourceDataLine line;
    private volatile boolean playing;

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

    void enqueue(List<Bili.Song> songs) {
        queue.addAll(songs);
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
        if (!playing && index >= 0 && index < queue.size() && line == null) {
            play(index);
            return;
        }
        if (line == null) {
            return;
        }
        if (playing) {
            line.stop();
            playing = false;
        } else {
            line.start();
            playing = true;
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
        Thread worker = new Thread(() -> stream(song, mine), "mclite-music");
        worker.setDaemon(true);
        worker.start();
    }

    private void stream(Bili.Song song, long mine) {
        Process proc = null;
        SourceDataLine audio = null;
        try {
            Path ffmpeg = ffmpeg();
            if (ffmpeg == null) {
                finish(mine, "找不到 ffmpeg：请先 `brew install ffmpeg` 或在插件设置里指定路径");
                return;
            }
            proc = new ProcessBuilder(ffmpeg.toString(),
                    "-nostdin", "-loglevel", "quiet",
                    "-headers", "Referer: https://www.bilibili.com/\r\nUser-Agent: Mozilla/5.0\r\n",
                    "-i", song.url(),
                    "-vn", "-f", "s16le", "-ac", "2", "-ar", "44100", "pipe:1")
                    .redirectErrorStream(false)
                    .start();
            process = proc;
            audio = AudioSystem.getSourceDataLine(FORMAT);
            audio.open(FORMAT, 96000);
            line = audio;
            audio.start();
            playing = true;
            listener.onState(true);
            InputStream in = proc.getInputStream();
            byte[] buffer = new byte[8192];
            int read;
            while (mine == token && (read = in.read(buffer)) > 0) {
                audio.write(buffer, 0, read);
            }
            if (mine == token) {
                finish(mine, null);
            }
        } catch (Exception ex) {
            if (mine == token) {
                finish(mine, "播放失败：" + ex.getMessage());
            }
        } finally {
            if (audio != null) {
                try {
                    audio.drain();
                    audio.close();
                } catch (RuntimeException ignored) {
                    // 忽略。
                }
            }
            if (proc != null) {
                proc.destroy();
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
        if (line != null) {
            try {
                line.close();
            } catch (RuntimeException ignored) {
                // 忽略。
            }
            line = null;
        }
        playing = false;
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
