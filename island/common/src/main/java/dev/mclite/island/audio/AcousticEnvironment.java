package dev.mclite.island.audio;

/** 玩家所处声学环境（不可变快照）。 */
public record AcousticEnvironment(boolean submerged, boolean enclosed, boolean raining,
                                  float roomSize, float absorption) {

    public AcousticEnvironment(boolean submerged, boolean enclosed, boolean raining,
                               float roomSize) {
        this(submerged, enclosed, raining, roomSize, 0.5f);
    }

    public static final AcousticEnvironment OPEN = new AcousticEnvironment(false, false, false, 1f, 0.5f);
}
