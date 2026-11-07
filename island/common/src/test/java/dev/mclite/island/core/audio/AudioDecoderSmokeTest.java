package dev.mclite.island.core.audio;

import dev.mclite.island.core.MediaScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用真实容器做冒烟：WAV 必测；本机有 ffmpeg 时额外转出 MP3/OGG/FLAC 测三条解码链。
 */
class AudioDecoderSmokeTest {

    @Test
    void decodesWavMp3OggFlac(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("tone.wav");
        writeToneWav(wav);

        assertDecodes(wav, "wav");
        try (PcmSource source = AudioDecoders.open(wav, 0L)) {
            assertTrue(source.canSeek(), "WAV 应支持 seek");
            assertTrue(source.durationMs() >= 900L, "WAV 时长应接近 1 秒");
        }

        String ffmpeg = ffmpeg();
        if (ffmpeg == null) {
            return;
        }
        for (String[] spec : new String[][]{
                {"mp3", "libmp3lame"}, {"ogg", "vorbis"}, {"flac", "flac"}}) {
            String ext = spec[0];
            Path target = dir.resolve("tone." + ext);
            java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                    ffmpeg, "-y", "-loglevel", "error", "-i", wav.toString()));
            if ("ogg".equals(ext)) {
                command.addAll(java.util.List.of("-c:a", spec[1], "-strict", "-2", "-ac", "2"));
            } else {
                command.addAll(java.util.List.of("-c:a", spec[1]));
            }
            command.add(target.toString());
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true).start();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "ffmpeg 超时: " + ext);
            assertEquals(0, process.exitValue(), "ffmpeg 转换失败: " + ext);
            assertDecodes(target, ext);
        }
    }

    private static void assertDecodes(Path file, String label) throws Exception {
        assertTrue(MediaScanner.isSupported(file), label + " 应被媒体扫描器接受");
        try (PcmSource source = AudioDecoders.open(file, 0L)) {
            assertTrue(source.format().getSampleRate() > 0, label + " 采样率无效");
            assertTrue(source.format().getChannels() > 0, label + " 声道无效");
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            int guard = 0;
            while ((read = source.read(buffer)) >= 0 && guard++ < 10_000) {
                total += Math.max(0, read);
            }
            assertTrue(total > 0, label + " 没有解出任何 PCM");
        }
    }

    private static void writeToneWav(Path target) throws Exception {
        int sampleRate = 44_100;
        int seconds = 1;
        byte[] pcm = new byte[sampleRate * seconds * 2];
        for (int i = 0; i < sampleRate * seconds; i++) {
            short value = (short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000);
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), format, sampleRate * seconds)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, target.toFile());
        }
    }

    private static String ffmpeg() {
        for (String candidate : new String[]{"/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg"}) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }
}
