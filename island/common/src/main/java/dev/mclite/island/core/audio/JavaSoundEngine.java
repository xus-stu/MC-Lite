package dev.mclite.island.core.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import dev.mclite.island.audio.PcmEffect;

/**
 * Java Sound 播放引擎。独立于 Minecraft 的声音引擎，
 * 这样 1.20.1 ~ 26.x 的音频代码不用各写一份。
 *
 * <p>并发约定：打开解码器/音频设备这类可能阻塞的操作绝不在锁内做，
 * 停播也不 join 工作线程——关闭 line/source 让阻塞的读写自然抛错退出。
 */
public final class JavaSoundEngine implements AudioEngine {

    private static final int BUFFER_BYTES = 8 * 1024;

    /** 一次播放的会话；工作线程只操作自己的会话。 */
    private static final class Session {
        final PcmSource source;
        final SourceDataLine line;
        final long durationMs;
        Thread worker;
        volatile boolean stopRequested;
        volatile boolean paused;
        volatile boolean ended;
        volatile long positionBytes;

        Session(PcmSource source, SourceDataLine line, long durationMs) {
            this.source = source;
            this.line = line;
            this.durationMs = durationMs;
        }
    }

    private final Object lock = new Object();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong requestCounter = new AtomicLong();
    private volatile Session session;
    private volatile float volume = 0.7f;
    private volatile Runnable onEnd;
    private volatile PcmEffect effect;

    @Override
    public void play(Path file, long startMs) throws Exception {
        long request = requestCounter.incrementAndGet();
        lastFile = file;

        // 可能阻塞：解码器初始化、设备打开。放在锁外。
        PcmSource opened = AudioDecoders.open(file, Math.max(0L, startMs));
        AudioFormat format = opened.format();
        long effectiveStart = opened.canSeek() ? Math.max(0L, startMs) : 0L;
        SourceDataLine line;
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format);
        } catch (Exception ex) {
            opened.close();
            throw new IllegalStateException("无法打开音频设备: " + format, ex);
        }
        applyVolume(line, volume);

        Session next = new Session(opened, line, opened.durationMs());
        long frameSize = Math.max(1, format.getFrameSize());
        float frameRate = format.getFrameRate();
        next.positionBytes = frameRate > 0
                ? (long) (effectiveStart / 1000.0 * frameRate * frameSize)
                : 0L;

        Session previous;
        long requestGeneration;
        synchronized (lock) {
            if (request != requestCounter.get()) {
                // 打开设备期间用户已经切歌，这次结果作废；当前会话继续播
                closeQuietly(line);
                closeQuietly(opened);
                return;
            }
            requestGeneration = generation.incrementAndGet();
            previous = session;
            session = next;
        }
        if (previous != null) {
            retire(previous);
        }
        line.start();
        Thread worker = new Thread(() -> runSession(requestGeneration, next),
                "mclite-island-audio");
        worker.setDaemon(true);
        next.worker = worker;
        worker.start();
    }

    private void runSession(long requestGeneration, Session own) {
        byte[] buffer = new byte[BUFFER_BYTES];
        boolean naturalEnd = false;
        try {
            while (!own.stopRequested && requestGeneration == generation.get()) {
                if (own.paused) {
                    Thread.sleep(20L);
                    continue;
                }
                int read = own.source.read(buffer);
                if (read < 0) {
                    own.ended = true;
                    naturalEnd = true;
                    break;
                }
                if (read == 0) {
                    continue;
                }
                PcmEffect fx = effect;
                if (fx != null) {
                    try {
                        fx.process(buffer, read, own.source.format());
                    } catch (Exception ignored) {
                        // 效果失败不影响播放
                    }
                }
                own.line.write(buffer, 0, read);
                own.positionBytes += read;
            }
        } catch (Exception ignored) {
            // 关闭设备/解码失败都按结束处理
        } finally {
            closeQuietly(own.line);
            closeQuietly(own.source);
            boolean notify;
            synchronized (lock) {
                notify = naturalEnd && !own.stopRequested
                        && requestGeneration == generation.get() && session == own;
                if (session == own) {
                    session = null;
                }
            }
            if (notify) {
                Runnable listener = onEnd;
                if (listener != null) {
                    listener.run();
                }
            }
        }
    }

    private void retire(Session old) {
        old.stopRequested = true;
        closeQuietly(old.line);
        closeQuietly(old.source);
        Thread worker = old.worker;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public void pause() {
        Session current = session;
        if (current == null || current.paused) {
            return;
        }
        current.paused = true;
        try {
            current.line.stop();
        } catch (Exception ignored) {
            // 设备已关闭
        }
    }

    @Override
    public void resume() {
        Session current = session;
        if (current == null || !current.paused) {
            return;
        }
        current.paused = false;
        try {
            current.line.start();
        } catch (Exception ignored) {
            // 设备已关闭
        }
    }

    @Override
    public void togglePause() {
        if (isPaused()) {
            resume();
        } else {
            pause();
        }
    }

    @Override
    public void stop() {
        retireCurrent();
    }

    private void retireCurrent() {
        requestCounter.incrementAndGet();
        generation.incrementAndGet();
        Session current;
        synchronized (lock) {
            current = session;
            session = null;
        }
        if (current != null) {
            retire(current);
        }
    }

    @Override
    public void seek(long positionMs) {
        Session current = session;
        if (current == null || current.stopRequested) {
            return;
        }
        boolean wasPaused = current.paused;
        // play() 会作废旧会话；路径与文件名需要从旧解码器无法取得，
        // 因此由 PlaybackService 在切歌时负责；这里只处理同一会话的重新定位。
        try {
            Path file = currentFileName();
            if (file == null) {
                return;
            }
            play(file, Math.max(0L, positionMs));
            if (wasPaused) {
                pause();
            }
        } catch (Exception ignored) {
            // seek 失败保持原状
        }
    }

    private volatile Path lastFile;

    private Path currentFileName() {
        return lastFile;
    }

    @Override
    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        Session current = session;
        if (current != null) {
            applyVolume(current.line, volume);
        }
    }

    private static void applyVolume(SourceDataLine line, float volume) {
        try {
            FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float db = volume <= 0.0001f ? control.getMinimum()
                    : (float) (20.0 * Math.log10(volume));
            control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), db)));
        } catch (Exception ignored) {
            // 设备不支持音量控制
        }
    }

    @Override
    public boolean isPlaying() {
        Session current = session;
        return current != null && !current.ended;
    }

    @Override
    public boolean isPaused() {
        Session current = session;
        return current != null && current.paused;
    }

    @Override
    public long positionMs() {
        Session current = session;
        if (current == null) {
            return 0L;
        }
        AudioFormat format = current.source.format();
        float frameRate = format.getFrameRate();
        int frameSize = Math.max(1, format.getFrameSize());
        if (frameRate <= 0) {
            return 0L;
        }
        return (long) (current.positionBytes / (frameSize * (double) frameRate) * 1000.0);
    }

    @Override
    public long durationMs() {
        Session current = session;
        return current == null ? -1L : current.source.durationMs();
    }

    @Override
    public void setOnEnd(Runnable listener) {
        this.onEnd = listener;
    }

    /** 音频线程上的原地效果（混响等）；null 表示直通。 */
    public void setEffect(PcmEffect effect) {
        this.effect = effect;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 已关闭
        }
    }
}
