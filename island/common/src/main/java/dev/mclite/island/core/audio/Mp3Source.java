package dev.mclite.island.core.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** MP3：JLayer 解码，输出 16-bit little-endian PCM。 */
final class Mp3Source implements PcmSource {

    private final Path file;
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private final AudioFormat format;
    private final long startMs;
    private byte[] pending;
    private int pendingOffset;
    private long decodedFrames;
    private volatile long durationMs = -1L;
    private boolean finished;

    Mp3Source(Path file, long startMs) throws Exception {
        this.file = file;
        this.startMs = Math.max(0L, startMs);
        this.bitstream = new Bitstream(new BufferedInputStream(Files.newInputStream(file)));
        Header first = bitstream.readFrame();
        if (first == null) {
            throw new IOException("空的 MP3 文件");
        }
        SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(first, bitstream);
        bitstream.closeFrame();
        if (buffer == null) {
            throw new IOException("MP3 解码失败");
        }
        this.format = new AudioFormat(buffer.getSampleFrequency(), 16,
                buffer.getChannelCount(), true, false);
        estimateDuration(first);
        // 第一帧已经解出来了，留作首个数据块
        this.pending = toLittleEndian(buffer);
        this.pendingOffset = 0;
        this.decodedFrames = buffer.getBufferLength() / Math.max(1, buffer.getChannelCount());
        skipToStart();
        if (startMs > 0) {
            // 探测格式用的首帧不能留在 seek 后的播放里
            pending = null;
            pendingOffset = 0;
        }
    }

    private void skipToStart() throws Exception {
        if (startMs <= 0) {
            return;
        }
        long targetFrames = startMs * (long) format.getSampleRate() / 1000L;
        while (!finished && decodedFrames < targetFrames) {
            byte[] chunk = nextChunk();
            if (chunk == null) {
                return;
            }
            // decodedFrames 已在 nextChunk() 内累加，这里不能再加一次
        }
    }

    private void estimateDuration(Header header) {
        try {
            int index = header.bitrate_index();
            int version = header.version();
            int layer = Math.max(0, Math.min(2, 3 - header.layer()));
            int kbps = Header.bitrates[version][layer][index];
            long bytes = Files.size(file);
            if (kbps > 0 && bytes > 0) {
                durationMs = (long) (bytes * 8.0 / (kbps * 1000.0) * 1000.0);
            }
        } catch (Throwable ignored) {
            // 估不出来就保持 -1
        }
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        if (pending != null && pendingOffset < pending.length) {
            int n = Math.min(buffer.length, pending.length - pendingOffset);
            System.arraycopy(pending, pendingOffset, buffer, 0, n);
            pendingOffset += n;
            return n;
        }
        byte[] chunk;
        try {
            chunk = nextChunk();
        } catch (Exception ex) {
            throw new IOException("MP3 解码失败", ex);
        }
        if (chunk == null) {
            return -1;
        }
        int n = Math.min(buffer.length, chunk.length);
        System.arraycopy(chunk, 0, buffer, 0, n);
        if (n < chunk.length) {
            pending = chunk;
            pendingOffset = n;
        }
        return n;
    }

    private byte[] nextChunk() throws Exception {
        if (finished) {
            return null;
        }
        Header header = bitstream.readFrame();
        if (header == null) {
            finished = true;
            return null;
        }
        SampleBuffer samples;
        try {
            samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
        } finally {
            bitstream.closeFrame();
        }
        if (samples == null) {
            finished = true;
            return null;
        }
        byte[] bytes = toLittleEndian(samples);
        decodedFrames += samples.getBufferLength() / Math.max(1, samples.getChannelCount());
        return bytes;
    }

    private static byte[] toLittleEndian(SampleBuffer buffer) {
        short[] samples = buffer.getBuffer();
        int length = buffer.getBufferLength();
        byte[] out = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            short value = samples[i];
            out[i * 2] = (byte) (value & 0xFF);
            out[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        try {
            bitstream.close();
        } catch (Exception ignored) {
            // 已经关过
        }
        if (!finished) {
            finished = true;
        }
    }

    @Override
    public String toString() {
        return "Mp3Source[" + file.getFileName() + "]";
    }
}
