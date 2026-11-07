package dev.mclite.island.core.audio;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

/** 已解码的 PCM 数据源，按块读取，避免整首歌进内存。 */
public interface PcmSource extends AutoCloseable {

    AudioFormat format();

    /** @return 读到的字节数；-1 表示结束 */
    int read(byte[] buffer) throws IOException;

    /** 跳到指定毫秒；不支持时返回 false。 */
    default boolean seek(long positionMs) throws IOException {
        return false;
    }

    @Override
    void close() throws IOException;
}
