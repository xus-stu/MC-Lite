package mclite.music;

/**
 * 混响之外的音效：低音增强 / 宽声场 / 8D 旋转 / 回声。
 * 处理 PCM16 立体声，和混响可以叠加（先音效后混响）。
 */
final class Effects {

    enum Preset {
        NONE("原声"),
        BASS("低音增强"),
        WIDE("环绕宽声场"),
        SPIN("8D 旋转"),
        ECHO("回声");

        final String label;

        Preset(String label) {
            this.label = label;
        }
    }

    private static final int ECHO_FRAMES = 11025;

    private Preset preset = Preset.NONE;
    private float lowL;
    private float lowR;
    private float phase;
    private final float[] echoL = new float[ECHO_FRAMES];
    private final float[] echoR = new float[ECHO_FRAMES];
    private int echoPos;

    Preset preset() {
        return preset;
    }

    void preset(Preset next) {
        preset = next;
        lowL = 0f;
        lowR = 0f;
        phase = 0f;
        java.util.Arrays.fill(echoL, 0f);
        java.util.Arrays.fill(echoR, 0f);
        echoPos = 0;
    }

    boolean enabled() {
        return preset != Preset.NONE;
    }

    void process(byte[] pcm, int length) {
        int frames = length / 4;
        float step = (float) (2 * Math.PI * 0.08 / 44100.0);
        for (int frame = 0; frame < frames; frame++) {
            int index = frame * 4;
            float left = ((short) ((pcm[index] & 0xFF) | (pcm[index + 1] << 8))) / 32768f;
            float right = ((short) ((pcm[index + 2] & 0xFF) | (pcm[index + 3] << 8))) / 32768f;
            switch (preset) {
                case BASS -> {
                    lowL += (left - lowL) * 0.06f;
                    lowR += (right - lowR) * 0.06f;
                    left += lowL * 0.9f;
                    right += lowR * 0.9f;
                }
                case WIDE -> {
                    float mid = (left + right) * 0.5f;
                    float side = (left - right) * 0.5f * 1.9f;
                    left = mid + side;
                    right = mid - side;
                }
                case SPIN -> {
                    phase += step;
                    if (phase > Math.PI * 2) {
                        phase -= (float) (Math.PI * 2);
                    }
                    float pan = (float) Math.sin(phase) * 0.55f;
                    left *= 1f - pan;
                    right *= 1f + pan;
                }
                case ECHO -> {
                    float bufferedL = echoL[echoPos];
                    float bufferedR = echoR[echoPos];
                    echoL[echoPos] = left + bufferedL * 0.38f;
                    echoR[echoPos] = right + bufferedR * 0.38f;
                    echoPos = (echoPos + 1) % ECHO_FRAMES;
                    left = left * 0.7f + bufferedL * 0.5f;
                    right = right * 0.7f + bufferedR * 0.5f;
                }
                default -> {
                }
            }
            write(pcm, index, left);
            write(pcm, index + 2, right);
        }
    }

    private static void write(byte[] pcm, int index, float value) {
        int sample = Math.round(Math.max(-1f, Math.min(1f, value)) * 32767f);
        pcm[index] = (byte) (sample & 0xFF);
        pcm[index + 1] = (byte) ((sample >> 8) & 0xFF);
    }
}
