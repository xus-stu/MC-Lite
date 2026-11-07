package dev.mclite.island.bili;

/** B 站 DASH 音频档位（只支持 AAC-LC）。 */
public enum BiliQuality {
    AUTO("自动", 0),
    K64("64K", 30216),
    K132("132K", 30232),
    K192("192K", 30280);

    private final String label;
    private final int audioId;

    BiliQuality(String label, int audioId) {
        this.label = label;
        this.audioId = audioId;
    }

    public String label() {
        return label;
    }

    public int audioId() {
        return audioId;
    }

    public BiliQuality next() {
        BiliQuality[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
