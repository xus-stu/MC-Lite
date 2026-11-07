package dev.mclite.island.core.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Path;

/** WAV（以及 Java Sound 能直接识别的 PCM 容器）。 */
final class WavSource implements PcmSource {

    private final Path file;
    private final AudioInputStream stream;

    WavSource(Path file) throws Exception {
        this.file = file;
        this.stream = AudioSystem.getAudioInputStream(file.toFile());
    }

    @Override
    public AudioFormat format() {
        return stream.getFormat();
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return stream.read(buffer);
    }

    @Override
    public long durationMs() {
        AudioFormat format = format();
        long frames = stream.getFrameLength();
        float rate = format.getFrameRate();
        if (frames <= 0 || rate <= 0) {
            return -1L;
        }
        return (long) (frames / (double) rate * 1000.0);
    }

    @Override
    public boolean canSeek() {
        return true;
    }

    @Override
    public boolean seek(long positionMs) throws IOException {
        // 重新打开后再按字节跳，兼容全部 PCM WAV。
        AudioFormat format = format();
        long frameSize = Math.max(1, format.getFrameSize());
        long targetFrames = Math.max(0L, positionMs) * (long) format.getFrameRate() / 1000L;
        long targetBytes = targetFrames * frameSize;
        long skipped = stream.skip(targetBytes);
        return skipped > 0 || targetBytes == 0;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @Override
    public String toString() {
        return "WavSource[" + file.getFileName() + "]";
    }
}
