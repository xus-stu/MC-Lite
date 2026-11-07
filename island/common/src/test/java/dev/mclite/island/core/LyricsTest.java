package dev.mclite.island.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricsTest {

    private static Lyrics lyrics() {
        return new Lyrics(List.of(
                new LrcLine(1_000L, "a"),
                new LrcLine(2_000L, "b"),
                new LrcLine(5_000L, "c")));
    }

    @Test
    void indexBeforeFirstLineIsMinusOne() {
        assertEquals(-1, lyrics().indexAt(500L));
        assertNull(lyrics().lineAt(0L));
    }

    @Test
    void findsCurrentAndNextLine() {
        Lyrics lyrics = lyrics();
        assertEquals("b", lyrics.lineAt(2_500L).text());
        assertEquals("c", lyrics.nextLine(2_500L).text());
        assertNull(lyrics.nextLine(6_000L));
    }

    @Test
    void progressInterpolatesBetweenLines() {
        assertEquals(1f / 6f, lyrics().progress(2_500L), 0.001f);
        assertTrue(lyrics().progress(1_000L) < 0.001f);
    }
}
