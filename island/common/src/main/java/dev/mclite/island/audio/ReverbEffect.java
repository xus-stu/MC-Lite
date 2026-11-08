package dev.mclite.island.audio;

import javax.sound.sampled.AudioFormat;

/**
 * 轻量 Freeverb 风格混响：4 comb + 2 allpass/声道，按环境平滑收敛。
 * 只在音频线程运行；关闭时由引擎直接短路，不产生任何处理。
 */
public final class ReverbEffect implements PcmEffect {

    private static final int[] COMB = {1116, 1188, 1277, 1356};
    private static final int[] ALLPASS = {556, 441};

    private final float[][][] comb = new float[2][4][];
    private final int[][] combIndex = new int[2][4];
    private final float[][] combStore = new float[2][4];
    private final float[][][] allpass = new float[2][2][];
    private final int[][] allpassIndex = new int[2][2];

    private float wet = 0.18f;
    private float targetWet = 0.18f;
    private float decay = 0.78f;
    private float targetDecay = 0.78f;
    private float damp = 0.25f;
    private float targetDamp = 0.25f;
    private float lowpass = 1f;
    private float targetLowpass = 1f;
    private final float[] lowpassState = new float[2];
    private int channels = 2;
    private int sampleRate = 44100;
    private boolean initialized;

    public void setEnvironment(AcousticEnvironment env, float strength) {
        float s = Math.max(0f, Math.min(1f, strength));
        float wetTarget = 0.14f;
        float decayTarget = 0.74f;
        float dampTarget = 0.32f;
        if (env.submerged()) {
            wetTarget = 0.58f;
            decayTarget = 0.90f;
            dampTarget = 0.82f;
        } else if (env.enclosed()) {
            wetTarget = 0.46f;
            decayTarget = 0.86f;
            dampTarget = 0.40f;
        } else if (env.raining()) {
            wetTarget = 0.26f;
            decayTarget = 0.78f;
            dampTarget = 0.30f;
        }
        this.targetWet = wetTarget * s;
        this.targetDecay = 0.55f + (decayTarget - 0.55f) * s;
        this.targetDamp = dampTarget;
        // 低通才是“水下糊”的关键：湿声再多，高频不闷也听不出来
        this.targetLowpass = env.submerged() ? 0.05f : env.enclosed() ? 0.28f
                : env.raining() ? 0.62f : 0.95f;
    }

    @Override
    public void process(byte[] buffer, int length, AudioFormat format) {
        int rate = Math.round(format.getSampleRate());
        int ch = format.getChannels();
        if (!initialized || rate != sampleRate || ch != channels) {
            init(rate, ch);
        }
        // 每缓冲朝目标收敛一次
        wet += (targetWet - wet) * 0.08f;
        decay += (targetDecay - decay) * 0.05f;
        damp += (targetDamp - damp) * 0.08f;
        lowpass += (targetLowpass - lowpass) * 0.10f;

        int frames = length / 2 / channels;
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < channels; c++) {
                int idx = (i * channels + c) * 2;
                short sample = (short) ((buffer[idx] & 0xFF) | (buffer[idx + 1] << 8));
                float dry = sample / 32768f;
                float wetSample = processChannel(c, dry);
                float out = dry * (1f - wet * 0.6f) + wetSample * wet;
                lowpassState[c] += (out - lowpassState[c]) * lowpass;
                out = lowpassState[c];
                if (out > 0.98f) out = 0.98f;
                if (out < -0.98f) out = -0.98f;
                int value = (int) (out * 32767f);
                buffer[idx] = (byte) (value & 0xFF);
                buffer[idx + 1] = (byte) ((value >> 8) & 0xFF);
            }
        }
    }

    private float processChannel(int c, float input) {
        float sum = 0f;
        for (int k = 0; k < 4; k++) {
            int size = comb[c][k].length;
            int idx = combIndex[c][k];
            float delayed = comb[c][k][idx];
            combStore[c][k] = delayed * (1f - damp) + combStore[c][k] * damp;
            comb[c][k][idx] = input + combStore[c][k] * decay;
            combIndex[c][k] = (idx + 1) % size;
            sum += delayed;
        }
        for (int k = 0; k < 2; k++) {
            int size = allpass[c][k].length;
            int idx = allpassIndex[c][k];
            float delayed = allpass[c][k][idx];
            float out = -sum + delayed;
            allpass[c][k][idx] = sum + delayed * 0.5f;
            allpassIndex[c][k] = (idx + 1) % size;
            sum = out;
        }
        return sum * 0.25f;
    }

    private void init(int rate, int ch) {
        sampleRate = rate;
        channels = ch;
        float scale = rate / 44100f;
        for (int c = 0; c < 2; c++) {
            for (int k = 0; k < 4; k++) {
                comb[c][k] = new float[Math.max(64, Math.round(COMB[k] * scale))];
                combIndex[c][k] = 0;
                combStore[c][k] = 0f;
            }
            lowpassState[c] = 0f;
            for (int k = 0; k < 2; k++) {
                allpass[c][k] = new float[Math.max(32, Math.round(ALLPASS[k] * scale))];
                allpassIndex[c][k] = 0;
            }
        }
        initialized = true;
    }
}
