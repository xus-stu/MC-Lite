package dev.mclite.island.core.audio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** 按扩展名选择解码器。 */
public final class AudioDecoders {

    private AudioDecoders() {
    }

    public static PcmSource open(Path file, long startMs) throws Exception {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return switch (ext) {
            case "wav" -> {
                WavSource source = new WavSource(file);
                if (startMs > 0) {
                    source.seek(startMs);
                }
                yield source;
            }
            case "mp3" -> new Mp3Source(file, startMs);
            case "ogg" -> new OggSource(file);
            case "flac" -> new FlacSource(file);
            default -> throw new IOException("不支持的音频格式: " + ext);
        };
    }
}
