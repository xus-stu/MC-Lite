package dev.mclite.island.core;

/** 播放模式。 */
public enum PlayMode {
    SEQUENTIAL("顺序"),
    RANDOM("随机"),
    LOOP_ONE("单曲"),
    LOOP_ALL("列表");

    private final String label;

    PlayMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public PlayMode next() {
        PlayMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
