package dev.mclite.island.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandConfigTest {

    @Test
    void roundTripsAndSanitizes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.json");
        IslandConfig config = new IslandConfig();
        config.volume = 3f;
        config.lyricsMaxLines = 9;
        config.islandScale = 99f;
        config.playMode = PlayMode.LOOP_ONE;
        config.save(file);

        IslandConfig loaded = IslandConfig.load(file).sanitized();
        assertEquals(1f, loaded.volume, 0.001f);
        assertEquals(4, loaded.lyricsMaxLines);
        assertEquals(3f, loaded.islandScale, 0.001f);
        assertEquals(PlayMode.LOOP_ONE, loaded.playMode);
    }

    @Test
    void corruptFileFallsBackAndBacksUp(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{not json");
        IslandConfig loaded = IslandConfig.load(file);
        assertEquals(new IslandConfig().musicFolder, loaded.musicFolder);
        assertTrue(Files.isRegularFile(dir.resolve("config.json.broken")));
    }

    @Test
    void missingFileUsesDefaults(@TempDir Path dir) {
        IslandConfig loaded = IslandConfig.load(dir.resolve("none.json"));
        assertEquals(0.7f, loaded.volume, 0.001f);
    }
}
