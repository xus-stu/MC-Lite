package mclite.music;

/**
 * 轻量混响：4 路梳状 + 2 路全通，处理 PCM16 立体声。
 *
 * <p>结构参考岛里的环境混响，但这里是音乐播放器用的固定房间感，不做生物群系推导。</p>
 */
final class Reverb {

    enum Preset {
        OFF("关", 0f),
        LIGHT("轻", 0.18f),
        ROOM("中", 0.32f),
        HALL("强", 0.5f);

        final String label;
        final float wet;

        Preset(String label, float wet) {
            this.label = label;
            this.wet = wet;
        }
    }

    private static final int[] COMB = {1116, 1188, 1277, 1356};
    private static final int[] ALLPASS = {225, 556};

    private final float[][] combL = new float[COMB.length][];
    private final float[][] combR = new float[COMB.length][];
    private final float[][] allL = new float[ALLPASS.length][];
    private final float[][] allR = new float[ALLPASS.length][];
    private final int[] combPos = new int[COMB.length];
    private final int[] allPos = new int[ALLPASS.length];
    private float dampL;
    private float dampR;
    private volatile Preset preset = Preset.OFF;

    Reverb() {
        for (int i = 0; i < COMB.length; i++) {
            combL[i] = new float[COMB[i]];
            combR[i] = new float[COMB[i] + 23];
        }
        for (int i = 0; i < ALLPASS.length; i++) {
            allL[i] = new float[ALLPASS[i]];
            allR[i] = new float[ALLPASS[i] + 17];
        }
    }

    Preset preset() {
        return preset;
    }

    void preset(Preset next) {
        preset = next;
        for (float[] line : combL) {
            java.util.Arrays.fill(line, 0f);
        }
        for (float[] line : combR) {
            java.util.Arrays.fill(line, 0f);
        }
        dampL = 0f;
        dampR = 0f;
    }

    boolean enabled() {
        return preset != Preset.OFF;
    }

    /** 原地处理 PCM16 小端立体声。 */
    void process(byte[] pcm, int length) {
        float wet = preset.wet;
        if (wet <= 0f) {
            return;
        }
        int frames = length / 4;
        for (int frame = 0; frame < frames; frame++) {
            int index = frame * 4;
            float left = ((short) ((pcm[index] & 0xFF) | (pcm[index + 1] << 8))) / 32768f;
            float right = ((short) ((pcm[index + 2] & 0xFF) | (pcm[index + 3] << 8))) / 32768f;
            float revL = 0f;
            float revR = 0f;
            for (int i = 0; i < COMB.length; i++) {
                int pos = combPos[i];
                float[] line = combL[i];
                float sample = line[pos];
                dampL += (sample - dampL) * 0.25f;
                line[pos] = left + dampL * 0.82f;
                revL += sample;
                combPos[i] = (pos + 1) % line.length;

                float[] lineR = combR[i];
                float sampleR = lineR[pos];
                dampR += (sampleR - dampR) * 0.25f;
                lineR[pos] = right + dampR * 0.82f;
                revR += sampleR;
            }
            for (int i = 0; i < ALLPASS.length; i++) {
                float[] line = allL[i];
                int pos = allPos[i];
                float buffered = line[pos];
                float out = -revL + buffered;
                line[pos] = revL + buffered * 0.5f;
                revL = out;
                float[] lineR = allR[i];
                float bufferedR = lineR[pos];
                float outR = -revR + bufferedR;
                lineR[pos] = revR + bufferedR * 0.5f;
                revR = outR;
                allPos[i] = (pos + 1) % line.length;
            }
            float mixedL = left * (1f - wet * 0.5f) + revL * wet * 0.25f;
            float mixedR = right * (1f - wet * 0.5f) + revR * wet * 0.25f;
            write(pcm, index, mixedL);
            write(pcm, index + 2, mixedR);
        }
    }

    private static void write(byte[] pcm, int index, float value) {
        int sample = Math.round(Math.max(-1f, Math.min(1f, value)) * 32767f);
        pcm[index] = (byte) (sample & 0xFF);
        pcm[index + 1] = (byte) ((sample >> 8) & 0xFF);
    }
}
