package dev.mclite.island.core.audio;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

/** 已解码的 PCM 数据源，按块读取，避免整首歌进内存。 */
public interface PcmSource extends AutoCloseable {

    AudioFormat format();

    /** @return 读到的字节数；-1 表示结束 */
    int read(byte[] buffer) throws IOException;

    /** 总时长；未知返回 -1。 */
    default long durationMs() {
        return -1L;
    }

    /** 是否支持跳转；不支持时播放器从 0 开始且如实显示位置。 */
    default boolean canSeek() {
        return false;
    }

    /** 跳到指定毫秒；不支持时返回 false。 */
    default boolean seek(long positionMs) throws IOException {
        return false;
    }

    @Override
    void close() throws IOException;
}
