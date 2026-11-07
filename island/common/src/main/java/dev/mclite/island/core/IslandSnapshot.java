package dev.mclite.island.core;

/** 灵动岛一次渲染所需的不可变快照，渲染线程只读它。 */
public record IslandSnapshot(
        String title,
        String artist,
        String lyric,
        String nextLyric,
        long positionMs,
        long durationMs,
        boolean playing,
        boolean paused,
        String contextTitle,
        String contextSubtitle) {

    public static final IslandSnapshot EMPTY =
            new IslandSnapshot("", "", "", "", 0L, -1L, false, false, "", "");

    public boolean hasTrack() {
        return title != null && !title.isBlank();
    }

    public boolean hasContext() {
        return contextTitle != null && !contextTitle.isBlank();
    }
}
