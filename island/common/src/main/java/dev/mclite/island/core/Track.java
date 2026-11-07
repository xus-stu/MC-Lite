package dev.mclite.island.core;

import java.nio.file.Path;
import java.util.Objects;

/** 一首本地曲目。 */
public record Track(Path file, String title, String artist) {

    public Track {
        Objects.requireNonNull(file, "file");
        if (title == null || title.isBlank()) {
            title = file.getFileName().toString();
        }
        if (artist == null) {
            artist = "";
        }
    }

    public String displayName() {
        return artist.isBlank() ? title : artist + " - " + title;
    }

    public String fileName() {
        return file.getFileName().toString();
    }
}
