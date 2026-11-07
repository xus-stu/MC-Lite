package dev.mclite.island.core.audio;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OGG Vorbis：jogg + jorbis 低层解码。
 *
 * <p>不用高层的 VorbisFile，是为了自己控制读取节奏、避免一次解码整首歌。
 */
final class OggSource implements PcmSource {

    private static final int CHUNK = 4096;

    private final Path file;
    private final InputStream input;
    private final SyncState oggSync = new SyncState();
    private final StreamState oggStream = new StreamState();
    private final Page oggPage = new Page();
    private final Packet oggPacket = new Packet();
    private final Info info = new Info();
    private final Comment comment = new Comment();
    private final DspState dsp = new DspState();
    private Block block;
    private final float[][][] pcm = new float[1][][];

    private AudioFormat format;
    private boolean headersDone;
    private boolean finished;
    private long decodedFrames;
    private byte[] pending;
    private int pendingOffset;

    OggSource(Path file) throws Exception {
        this.file = file;
        this.input = Files.newInputStream(file);
        initHeaders();
    }

    private void initHeaders() throws Exception {
        oggSync.init();
        info.init();
        comment.init();
        if (!readPage()) {
            throw new IOException("OGG 文件为空");
        }
        oggStream.init(oggPage.serialno());
        oggStream.pagein(oggPage);
        int headers = 0;
        while (headers < 3) {
            int result = oggStream.packetout(oggPacket);
            if (result == 0) {
                if (!readPage()) {
                    throw new IOException("OGG 头信息不完整");
                }
                oggStream.pagein(oggPage);
                continue;
            }
            if (result < 0) {
                throw new IOException("OGG 数据损坏");
            }
            if (info.synthesis_headerin(comment, oggPacket) < 0) {
                throw new IOException("不是有效的 Vorbis 文件");
            }
            headers++;
        }
        dsp.synthesis_init(info);
        block = new Block(dsp);
        format = new AudioFormat(info.rate, 16, info.channels, true, false);
        headersDone = true;
    }

    private boolean readPage() throws IOException {
        while (true) {
            int result = oggSync.pageout(oggPage);
            if (result == 1) {
                return true;
            }
            if (result < 0) {
                throw new IOException("OGG 页校验失败");
            }
            int index = oggSync.buffer(CHUNK);
            byte[] data = oggSync.data;
            int read = input.read(data, index, CHUNK);
            if (read <= 0) {
                return false;
            }
            oggSync.wrote(read);
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
        try {
            byte[] chunk = nextChunk();
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
        } catch (Exception ex) {
            throw new IOException("OGG 解码失败: " + ex, ex);
        }
    }

    private byte[] nextChunk() throws Exception {
        if (finished) {
            return null;
        }
        while (!finished) {
            int result = oggStream.packetout(oggPacket);
            if (result == 0) {
                if (!readPage()) {
                    finished = true;
                    return null;
                }
                oggStream.pagein(oggPage);
                continue;
            }
            if (result < 0) {
                finished = true;
                return null;
            }
            if (!headersDone) {
                continue;
            }
            if (block.synthesis(oggPacket) == 0) {
                dsp.synthesis_blockin(block);
            }
            int[] offsets = new int[format.getChannels()];
            int samples = dsp.synthesis_pcmout(pcm, offsets);
            if (samples > 0) {
                int channels = format.getChannels();
                byte[] out = new byte[samples * channels * 2];
                int p = 0;
                for (int i = 0; i < samples; i++) {
                    for (int ch = 0; ch < channels; ch++) {
                        int value = (int) (pcm[0][ch][i] * 32767f);
                        value = Math.max(-32768, Math.min(32767, value));
                        out[p++] = (byte) (value & 0xFF);
                        out[p++] = (byte) ((value >> 8) & 0xFF);
                    }
                }
                dsp.synthesis_read(samples);
                decodedFrames += samples;
                return out;
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        finished = true;
        try {
            oggStream.clear();
            oggSync.clear();
            if (block != null) block.clear();
            info.clear();
        } catch (Exception ignored) {
            // 清理失败无所谓
        }
        input.close();
    }

    @Override
    public String toString() {
        return "OggSource[" + file.getFileName() + "]";
    }
}
