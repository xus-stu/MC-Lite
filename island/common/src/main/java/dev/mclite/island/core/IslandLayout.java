package dev.mclite.island.core;

/** 灵动岛在 GUI 坐标系里的位置与缩放。 */
public record IslandLayout(int x, int y, float scale) {

    public static final int BASE_WIDTH = 220;
    public static final int BASE_HEIGHT = 28;

    public static IslandLayout fromConfig(IslandConfig config) {
        return new IslandLayout(config.islandX, config.islandY, config.islandScale);
    }

    /** 夹到屏幕内，避免拖出边界后找不回来。 */
    public IslandLayout clampTo(int screenWidth, int screenHeight) {
        int width = Math.round(BASE_WIDTH * scale);
        int height = Math.round(BASE_HEIGHT * scale);
        int clampedX = Math.max(0, Math.min(x, Math.max(0, screenWidth - width)));
        int clampedY = Math.max(0, Math.min(y, Math.max(0, screenHeight - height)));
        return new IslandLayout(clampedX, clampedY, scale);
    }

    public int width() {
        return Math.round(BASE_WIDTH * scale);
    }

    public int height() {
        return Math.round(BASE_HEIGHT * scale);
    }
}
