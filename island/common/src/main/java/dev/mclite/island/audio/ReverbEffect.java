package dev.mclite.island.audio;

import javax.sound.sampled.AudioFormat;

/**
 * 环境混响：6 路梳状滤波 + 2 路全通 + 6 次早期反射，参数按房间包裹度/材质吸声平滑变化。
 * 参考 DangoMusic 的 EnvironmentAudio 结构，但不依赖 Minecraft。
 *
 * <p>户外基本是干声；石洞/大厅反射长且亮；羊毛/树叶房间吸高频、尾巴短。</p>
 */
public final class ReverbEffect implements PcmEffect {

    private static final int[] COMB = {1116, 1188, 1277, 1356, 1422, 1491};
    private static final int[] ALLPASS = {556, 441};
    private static final int STEREO_SPREAD = 23;
    private static final float[] TAP_TIMES = {1f, 1.37f, 1.71f, 2.13f, 2.6f, 3.1f};
    private static final float[] TAP_GAINS = {0.8f, 0.62f, 0.5f, 0.4f, 0.31f, 0.24f};

    private int rate;
    private float[][][] comb;
    private int[][] combPos;
    private float[][] combStore;
    private float[][][] allpass;
    private int[][] allpassPos;
    private float[] early;
    private int earlyPos;
    private int earlyTapBase;
    private float lp;
    private float lowpass = 1f;
    private float targetLowpass = 1f;
    private float wet = 0.12f;
    private float targetWet = 0.12f;
    private float decay = 0.72f;
    private float targetDecay = 0.72f;
    private float damp = 0.3f;
    private float targetDamp = 0.3f;
    private float gain = 1f;
    private float targetGain = 1f;
    private boolean initialized;

    public ReverbEffect() {
    }

    public synchronized void setEnvironment(AcousticEnvironment env, float strength) {
        if (env == null) {
            targetWet = 0f;
            targetGain = 1f;
            return;
        }
        float s = Math.max(0f, Math.min(1f, strength));
        float enclosure = env.enclosed() ? 1f : 0f;
        float absorb = Math.max(0f, Math.min(1f, env.absorption()));
        float wetTarget = 0.05f + 0.58f * (float) Math.pow(enclosure, 1.4);
        if (env.raining()) {
            wetTarget += 0.04f;
        }
        if (env.submerged()) {
            wetTarget = Math.max(wetTarget, 0.38f);
        }
        float decayTarget = 0.62f + 0.28f * enclosure - absorb * 0.20f;
        float dampTarget = 0.18f + absorb * 0.55f;
        float cutoff = 16000f;
        if (enclosure > 0f) {
            cutoff = 7000f - absorb * 4200f;
        }
        if (env.submerged()) {
            cutoff = Math.min(cutoff, 620f);
        }
        targetWet = wetTarget * s;
        targetDecay = Math.max(0.45f, Math.min(0.94f, decayTarget));
        targetDamp = Math.max(0.08f, Math.min(0.9f, dampTarget));
        targetLowpass = Math.max(0.015f, Math.min(1f,
                1f - (float) Math.exp(-2.0 * Math.PI * cutoff / 44100.0)));
        targetGain = 1f - (1f - (1f - 0.10f * enclosure * absorb)) * s;
    }

    @Override
    public synchronized void process(byte[] buffer, int length, AudioFormat format) {
        int channels = Math.max(1, format.getChannels());
        int sampleRate = Math.round(format.getSampleRate());
        if (!initialized || sampleRate != rate) {
            setup(sampleRate);
        }
        wet += (targetWet - wet) * 0.08f;
        decay += (targetDecay - decay) * 0.08f;
        damp += (targetDamp - damp) * 0.08f;
        gain += (targetGain - gain) * 0.08f;
        lowpass += (targetLowpass - lowpass) * 0.08f;
        if (wet < 0.001f && Math.abs(gain - 1f) < 0.001f && lowpass > 0.95f) {
            return;
        }

        int frames = length / (2 * channels);
        float[] dry = new float[2];
        float[] out = new float[2];
        for (int frame = 0; frame < frames; frame++) {
            int index = frame * channels * 2;
            float mono = 0f;
            for (int c = 0; c < channels && c < 2; c++) {
                short sample = (short) ((buffer[index + c * 2] & 0xFF)
                        | (buffer[index + c * 2 + 1] << 8));
                float value = sample / 32768f;
                lp += (value - lp) * lowpass;
                value = lp;
                dry[c] = value;
                mono += value;
            }
            mono /= Math.min(2, channels);
            earlyReflection(mono, out);
            float lateL = combBank(0, mono);
            float lateR = combBank(1, mono);
            float wetL = out[0] + lateL * 0.65f;
            float wetR = out[1] + lateR * 0.65f;
            float dryGain = 1f - wet * 0.55f;
            if (channels == 2) {
                writeShort(buffer, index, dry[0] * dryGain * gain + wetL * wet);
                writeShort(buffer, index + 2, dry[1] * dryGain * gain + wetR * wet);
            } else {
                writeShort(buffer, index, dry[0] * dryGain * gain
                        + (wetL + wetR) * 0.5f * wet);
            }
        }
    }

    private static void writeShort(byte[] buffer, int index, float value) {
        int sample = Math.round(Math.max(-1f, Math.min(1f, value)) * 32767f);
        buffer[index] = (byte) (sample & 0xFF);
        buffer[index + 1] = (byte) ((sample >> 8) & 0xFF);
    }

    private void earlyReflection(float mono, float[] out) {
        int mask = early.length - 1;
        for (int i = 0; i < TAP_TIMES.length; i++) {
            int delay = Math.max(1, (int) (earlyTapBase * TAP_TIMES[i]));
            float sample = early[(earlyPos - delay) & mask];
            float pan = ((i % 2 == 0) ? -0.55f : 0.55f);
            out[0] += sample * TAP_GAINS[i] * (1f - pan) * 0.5f * 0.5f;
            out[1] += sample * TAP_GAINS[i] * (1f + pan) * 0.5f * 0.5f;
        }
        early[earlyPos & mask] = mono;
        earlyPos++;
    }

    private float combBank(int side, float input) {
        float sum = 0f;
        for (int i = 0; i < COMB.length; i++) {
            int pos = combPos[side][i];
            float stored = comb[side][i][pos];
            combStore[side][i] = stored * (1f - damp) + combStore[side][i] * damp;
            comb[side][i][pos] = input + combStore[side][i] * decay;
            combPos[side][i] = (pos + 1) % comb[side][i].length;
            sum += stored;
        }
        float value = sum / COMB.length;
        for (int i = 0; i < ALLPASS.length; i++) {
            int pos = allpassPos[side][i];
            float stored = allpass[side][i][pos];
            float output = -value + stored;
            allpass[side][i][pos] = value + stored * 0.5f;
            allpassPos[side][i] = (pos + 1) % allpass[side][i].length;
            value = output;
        }
        return value;
    }

    private void setup(int sampleRate) {
        rate = sampleRate;
        float scale = sampleRate / 44100f;
        comb = new float[2][COMB.length][];
        combPos = new int[2][COMB.length];
        combStore = new float[2][COMB.length];
        allpass = new float[2][ALLPASS.length][];
        allpassPos = new int[2][ALLPASS.length];
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < COMB.length; i++) {
                comb[side][i] = new float[Math.max(32,
                        Math.round((COMB[i] + side * STEREO_SPREAD) * scale))];
            }
            for (int i = 0; i < ALLPASS.length; i++) {
                allpass[side][i] = new float[Math.max(16,
                        Math.round((ALLPASS[i] + side * STEREO_SPREAD) * scale))];
            }
        }
        earlyTapBase = Math.max(8, Math.round(0.010f * sampleRate));
        int earlySize = Integer.highestOneBit(Math.max(1024, earlyTapBase * 4)) * 2;
        early = new float[earlySize];
        earlyPos = 0;
        initialized = true;
    }

}
