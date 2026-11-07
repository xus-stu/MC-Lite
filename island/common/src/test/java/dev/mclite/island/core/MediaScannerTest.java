package dev.mclite.island.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaScannerTest {

    @Test
    void scansSupportedFormatsRecursively(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("A - Title.mp3"), "x");
        Files.writeString(dir.resolve("plain.flac"), "x");
        Files.writeString(dir.resolve("note.txt"), "x");
        Path sub = Files.createDirectories(dir.resolve("sub"));
        Files.writeString(sub.resolve("B - Nested.ogg"), "x");

        List<Track> tracks = MediaScanner.scan(dir);
        assertEquals(3, tracks.size());
        assertTrue(tracks.stream().anyMatch(t -> t.title().equals("Title") && t.artist().equals("A")));
        assertTrue(tracks.stream().anyMatch(t -> t.title().equals("plain")));
        assertTrue(tracks.stream().anyMatch(t -> t.title().equals("Nested")));
    }

    @Test
    void rejectsUnsupportedAndExtensionless() {
        assertFalse(MediaScanner.isSupported(Path.of("a.txt")));
        assertFalse(MediaScanner.isSupported(Path.of("README")));
        assertTrue(MediaScanner.isSupported(Path.of("a.MP3")));
    }

    @Test
    void missingDirectoryReturnsEmpty(@TempDir Path dir) {
        assertTrue(MediaScanner.scan(dir.resolve("nope")).isEmpty());
    }
}
