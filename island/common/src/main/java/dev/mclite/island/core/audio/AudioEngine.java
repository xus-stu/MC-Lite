package dev.mclite.island.core.audio;

import dev.mclite.island.audio.PcmEffect;

import java.nio.file.Path;

/** 播放器抽象；后续若换 OpenAL 实现，界面层无需改动。 */
public interface AudioEngine {

    /** 打开并开始播放；startMs 用于恢复进度。 */
    void play(Path file, long startMs) throws Exception;

    void pause();

    void resume();

    void togglePause();

    void stop();

    void seek(long positionMs);

    void setVolume(float volume);

    boolean isPlaying();

    boolean isPaused();

    long positionMs();

    /** 文件总时长；未知时返回 -1。 */
    long durationMs();

    void setOnEnd(Runnable listener);

    /** JavaSound 的 PCM 效果；Sound Physics 后端不需要，默认空实现。 */
    default void setEffect(PcmEffect effect) {
    }

    /** 客户端每 tick 调用；JavaSound 后端不需要。 */
    default void tick() {
    }
}
