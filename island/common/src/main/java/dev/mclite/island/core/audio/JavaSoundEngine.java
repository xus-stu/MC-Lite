package dev.mclite.island.core.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;

/**
 * Java Sound 播放引擎。独立于 Minecraft 的声音引擎，
 * 这样 1.20.1 ~ 26.x 的音频代码不用各写一份。
 */
public final class JavaSoundEngine implements AudioEngine {

    private static final int BUFFER_BYTES = 8 * 1024;

    private final Object lock = new Object();
    private SourceDataLine line;
    private PcmSource source;
    private Thread worker;
    private volatile boolean stopped = true;
    private volatile boolean paused;
    private volatile long positionBytes;
    private volatile long durationMs = -1L;
    private volatile boolean ended;
    private volatile float volume = 0.7f;
    private volatile Runnable onEnd;
    private Path currentFile;

    @Override
    public void play(Path file, long startMs) throws Exception {
        synchronized (lock) {
            stopLocked();
            PcmSource opened = AudioDecoders.open(file, Math.max(0L, startMs));
            AudioFormat format = opened.format();
            long effectiveStart = opened.canSeek() ? Math.max(0L, startMs) : 0L;
            SourceDataLine newLine;
            try {
                newLine = AudioSystem.getSourceDataLine(format);
                newLine.open(format);
            } catch (Exception ex) {
                opened.close();
                throw new IllegalStateException("无法打开音频设备: " + format, ex);
            }
            newLine.start();
            applyVolume(newLine, volume);

            this.source = opened;
            this.line = newLine;
            this.currentFile = file;
            this.stopped = false;
            this.paused = false;
            this.ended = false;
            this.durationMs = opened.durationMs();
            long frameSize = Math.max(1, format.getFrameSize());
            float frameRate = format.getFrameRate();
            this.positionBytes = frameRate > 0
                    ? (long) (effectiveStart / 1000.0 * frameRate * frameSize)
                    : 0L;

            Runnable body = () -> runPlayback(opened, newLine);
            Thread thread = new Thread(body, "mclite-island-audio");
            thread.setDaemon(true);
            this.worker = thread;
            thread.start();
        }
    }

    private void runPlayback(PcmSource opened, SourceDataLine line) {
        byte[] buffer = new byte[BUFFER_BYTES];
        try {
            while (!stopped) {
                if (paused) {
                    Thread.sleep(20L);
                    continue;
                }
                int read = opened.read(buffer);
                if (read < 0) {
                    ended = true;
                    break;
                }
                if (read == 0) {
                    continue;
                }
                line.write(buffer, 0, read);
                positionBytes += read;
            }
        } catch (Exception ignored) {
            // 解码/设备错误按播放结束处理，界面会显示状态
        } finally {
            boolean naturalEnd = !stopped;
            try {
                line.drain();
            } catch (Exception ignored) {
                // 设备已关闭
            }
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) {
                // 设备已关闭
            }
            try {
                opened.close();
            } catch (Exception ignored) {
                // 已关闭
            }
            synchronized (lock) {
                if (this.line == line) {
                    this.line = null;
                    this.source = null;
                }
            }
            if (naturalEnd) {
                Runnable listener = onEnd;
                if (listener != null) {
                    listener.run();
                }
            }
        }
    }

    @Override
    public void pause() {
        if (paused || stopped) {
            return;
        }
        paused = true;
        SourceDataLine current = line;
        if (current != null) {
            current.stop();
        }
    }

    @Override
    public void resume() {
        if (!paused) {
            return;
        }
        paused = false;
        SourceDataLine current = line;
        if (current != null) {
            current.start();
        }
    }

    @Override
    public void togglePause() {
        if (paused) {
            resume();
        } else {
            pause();
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            stopLocked();
        }
    }

    private void stopLocked() {
        stopped = true;
        paused = false;
        ended = false;
        durationMs = -1L;
        Thread current = worker;
        if (current != null && current != Thread.currentThread()) {
            current.interrupt();
            try {
                current.join(500L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        worker = null;
        SourceDataLine currentLine = line;
        line = null;
        source = null;
        if (currentLine != null) {
            try {
                currentLine.stop();
                currentLine.close();
            } catch (Exception ignored) {
                // 已关闭
            }
        }
    }

    @Override
    public void seek(long positionMs) {
        Path file = currentFile;
        if (file == null || stopped || source == null) {
            return;
        }
        boolean wasPaused = paused;
        try {
            play(file, Math.max(0L, positionMs));
            if (wasPaused) {
                pause();
            }
        } catch (Exception ignored) {
            // seek 失败保持原状
        }
    }

    @Override
    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        SourceDataLine current = line;
        if (current != null) {
            applyVolume(current, volume);
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
        return !stopped && !ended;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public long positionMs() {
        PcmSource current = source;
        SourceDataLine currentLine = line;
        if (current == null || currentLine == null) {
            return 0L;
        }
        AudioFormat format = current.format();
        float frameRate = format.getFrameRate();
        int frameSize = Math.max(1, format.getFrameSize());
        if (frameRate <= 0) {
            return 0L;
        }
        return (long) (positionBytes / (frameSize * (double) frameRate) * 1000.0);
    }

    @Override
    public long durationMs() {
        return durationMs;
    }

    @Override
    public void setOnEnd(Runnable listener) {
        this.onEnd = listener;
    }
}
