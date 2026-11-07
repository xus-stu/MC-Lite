package dev.mclite.island.audio;

import javax.sound.sampled.AudioFormat;

/** 音频线程上的原地 PCM 处理（16-bit little-endian）。 */
public interface PcmEffect {
    void process(byte[] buffer, int length, AudioFormat format);
}
