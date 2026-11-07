package dev.mclite.island.core.audio;

import org.jflac.FLACDecoder;
import org.jflac.PCMProcessor;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * FLAC：jFLAC 解码。jFLAC 的 PCMProcessor 是推送式的，
 * 这里用有界队列把它适配成拉取式 PcmSource。
 */
final class FlacSource implements PcmSource {

    private static final byte[] EOF = new byte[0];

    private final Path file;
    private final InputStream input;
    private final FLACDecoder decoder;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(64);
    private final CountDownLatch headerLatch = new CountDownLatch(1);
    private final Thread producer;
    private volatile AudioFormat format;
    private volatile int bits = 16;
    private volatile long durationMs = -1L;
    private volatile boolean closed;
    private byte[] pending;
    private int pendingOffset;

    FlacSource(Path file) throws Exception {
        this.file = file;
        this.input = Files.newInputStream(file);
        this.decoder = new FLACDecoder(input);
        decoder.addPCMProcessor(new PCMProcessor() {
            @Override
            public void processStreamInfo(StreamInfo info) {
                if (info != null) {
                    bits = info.getBitsPerSample();
                    format = new AudioFormat(info.getSampleRate(), bits,
                            info.getChannels(), true, false);
                    if (info.getTotalSamples() > 0L && info.getSampleRate() > 0) {
                        durationMs = (long) (info.getTotalSamples()
                                / (double) info.getSampleRate() * 1000.0);
                    }
                }
                headerLatch.countDown();
            }

            @Override
            public void processPCM(ByteData pcm) {
                if (closed || format == null) {
                    return;
                }
                byte[] copy = new byte[pcm.getLen()];
                System.arraycopy(pcm.getData(), 0, copy, 0, copy.length);
                if (bits == 16) {
                    // jFLAC 输出 big-endian，Java Sound 这边统一小端
                    for (int i = 0; i + 1 < copy.length; i += 2) {
                        byte tmp = copy[i];
                        copy[i] = copy[i + 1];
                        copy[i + 1] = tmp;
                    }
                }
                try {
                    queue.put(copy);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        this.producer = new Thread(this::decodeLoop, "mclite-island-flac");
        this.producer.setDaemon(true);
        this.producer.start();
        if (!headerLatch.await(5, TimeUnit.SECONDS) || format == null) {
            close();
            throw new IOException("不是有效的 FLAC 文件");
        }
    }

    private void decodeLoop() {
        try {
            decoder.decode();
        } catch (Exception ignored) {
            // 损坏文件按结束处理
        } finally {
            headerLatch.countDown();
            try {
                queue.put(EOF);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public long durationMs() {
        return durationMs;
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
            chunk = queue.poll(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        }
        if (chunk == null) {
            throw new IOException("FLAC 解码超时");
        }
        if (chunk == EOF) {
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

    @Override
    public void close() throws IOException {
        closed = true;
        producer.interrupt();
        queue.clear();
        input.close();
    }

    @Override
    public String toString() {
        return "FlacSource[" + file.getFileName() + "]";
    }
}
