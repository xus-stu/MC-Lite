package dev.mclite.island.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlaylistTest {

    private static Track track(String name) {
        return new Track(Path.of("/music", name + ".mp3"), name, "");
    }

    private static Playlist playlist(PlayMode mode, int size) {
        Playlist playlist = new Playlist();
        List<Track> tracks = java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> track("t" + i))
                .toList();
        playlist.replace(tracks);
        playlist.mode(mode);
        return playlist;
    }

    @Test
    void sequentialStopsAtEnd() {
        Playlist playlist = playlist(PlayMode.SEQUENTIAL, 2);
        assertEquals("t1", playlist.advance(false).title());
        assertNull(playlist.advance(false));
        assertEquals("t1", playlist.current().title());
    }

    @Test
    void loopAllWrapsAround() {
        Playlist playlist = playlist(PlayMode.LOOP_ALL, 2);
        assertEquals("t1", playlist.advance(false).title());
        assertEquals("t0", playlist.advance(false).title());
    }

    @Test
    void loopOneRepeatsOnNaturalEndAndAdvancesOnManual() {
        Playlist playlist = playlist(PlayMode.LOOP_ONE, 3);
        assertSame(playlist.current(), playlist.advance(false));
        assertEquals("t1", playlist.advance(true).title());
    }

    @Test
    void previousWrapsToEnd() {
        Playlist playlist = playlist(PlayMode.SEQUENTIAL, 3);
        assertEquals("t2", playlist.previous().title());
        assertEquals("t1", playlist.previous().title());
    }

    @Test
    void modeCycles() {
        Playlist playlist = playlist(PlayMode.SEQUENTIAL, 1);
        assertEquals(PlayMode.RANDOM, playlist.cycleMode());
        assertEquals(PlayMode.LOOP_ONE, playlist.cycleMode());
        assertEquals(PlayMode.LOOP_ALL, playlist.cycleMode());
        assertEquals(PlayMode.SEQUENTIAL, playlist.cycleMode());
    }
}
