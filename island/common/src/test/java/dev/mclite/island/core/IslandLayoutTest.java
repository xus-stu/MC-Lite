package dev.mclite.island.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandLayoutTest {

    @Test
    void clampsIntoSmallScreens() {
        IslandLayout layout = new IslandLayout(500, 400, 1f).clampTo(320, 240);
        assertEquals(100, layout.x());
        assertEquals(212, layout.y());
    }

    @Test
    void scalesDimensions() {
        IslandLayout layout = new IslandLayout(0, 0, 2f);
        assertEquals(IslandLayout.BASE_WIDTH * 2, layout.width());
        assertEquals(IslandLayout.BASE_HEIGHT * 2, layout.height());
    }
}
