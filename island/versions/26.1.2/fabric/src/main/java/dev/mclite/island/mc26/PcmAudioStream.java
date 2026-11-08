package dev.mclite.island.mc26;

import dev.mclite.island.bili.BiliAudioCache;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 把本地/B 站音频解码成 16-bit 44.1k 立体声 PCM，直接喂给 Minecraft 的
 * {@link AudioStream}，不经过 OGG 转码，也没有临时文件。
 */
final class PcmAudioStream implements AudioStream {

    private static final int TARGET_RATE = 44100;
    private static final float TARGET_RATE_F = TARGET_RATE;

    private final AudioInputStream stream;
    private final AudioFormat format;
    private long skipBytes;

    PcmAudioStream(Path source, long startMs) throws Exception {
        Path readable = source;
        if (!isPcmContainer(source)) {
            readable = toWav(source);
        }
        AudioInputStream original = AudioSystem.getAudioInputStream(readable.toFile());
        AudioFormat pcm = new AudioFormat(TARGET_RATE_F, 16, 2, true, false);
        this.stream = AudioSystem.getAudioInputStream(pcm, original);
        this.format = stream.getFormat();
        this.skipBytes = Math.max(0L, startMs) * (long) format.getFrameSize()
                * (long) TARGET_RATE / 1000L;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        int want = Math.max(2, size);
        byte[] buffer = new byte[want];
        while (skipBytes > 0) {
            int skipped = stream.read(buffer, 0, (int) Math.min(buffer.length, skipBytes));
            if (skipped <= 0) {
                skipBytes = 0;
                return ByteBuffer.allocate(0);
            }
            skipBytes -= skipped;
        }
        int read = stream.read(buffer, 0, buffer.length);
        if (read <= 0) {
            return ByteBuffer.allocate(0);
        }
        ByteBuffer result = ByteBuffer.allocate(read);
        result.put(buffer, 0, read);
        result.flip();
        return result;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    static long durationMs(Path source) {
        try {
            var fileFormat = AudioSystem.getAudioFileFormat(
                    isPcmContainer(source) ? source.toFile() : toWav(source).toFile());
            long frames = fileFormat.getFrameLength();
            float rate = fileFormat.getFormat().getFrameRate();
            if (frames > 0 && rate > 0) {
                return Math.round(frames * 1000.0 / rate);
            }
        } catch (Exception ignored) {
            // 交给 ffmpeg 兜底
        }
        try {
            Path ffmpeg = BiliAudioCache.findFfmpeg();
            if (ffmpeg == null) {
                return -1L;
            }
            Process process = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-i",
                    source.toString()).redirectErrorStream(true).start();
            String text;
            try (var in = process.getInputStream()) {
                text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            process.waitFor(10, TimeUnit.SECONDS);
            int at = text.indexOf("Duration: ");
            if (at < 0) {
                return -1L;
            }
            String line = text.substring(at + 10, Math.min(text.length(), at + 30));
            String[] parts = line.split("[:,]");
            if (parts.length < 3) {
                return -1L;
            }
            double seconds = Integer.parseInt(parts[0].trim()) * 3600
                    + Integer.parseInt(parts[1].trim()) * 60
                    + Double.parseDouble(parts[2].trim());
            return Math.round(seconds * 1000.0);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static boolean isPcmContainer(Path source) {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".wav") || name.endsWith(".aiff") || name.endsWith(".aif");
    }

    private static Path toWav(Path source) throws Exception {
        Path ffmpeg = BiliAudioCache.findFfmpeg();
        if (ffmpeg == null) {
            throw new IOException("找不到 ffmpeg，无法解码 " + source.getFileName());
        }
        Path dir = source.toAbsolutePath().getParent().resolve(".mclite-pcm");
        Files.createDirectories(dir);
        String name = Integer.toHexString(source.toAbsolutePath().hashCode())
                + "-" + source.getFileName().toString().hashCode() + ".wav";
        Path out = dir.resolve(name);
        if (Files.isRegularFile(out) && Files.size(out) > 1024) {
            return out;
        }
        Path temp = dir.resolve(name + ".part");
        Files.deleteIfExists(temp);
        Process process = new ProcessBuilder(ffmpeg.toString(), "-y", "-loglevel", "error",
                "-i", source.toString(), "-vn", "-ac", "2", "-ar", "44100",
                "-c:a", "pcm_s16le", temp.toString()).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg 转 WAV 超时");
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(temp)) {
            throw new IOException("ffmpeg 转 WAV 失败");
        }
        Files.move(temp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return out;
    }
}
