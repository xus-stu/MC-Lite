package dev.mclite.island.audio;

/** 玩家所处声学环境（不可变快照）。 */
public record AcousticEnvironment(boolean submerged, boolean enclosed, boolean raining,
                                  float roomSize) {

    public static final AcousticEnvironment OPEN = new AcousticEnvironment(false, false, false, 1f);
}
