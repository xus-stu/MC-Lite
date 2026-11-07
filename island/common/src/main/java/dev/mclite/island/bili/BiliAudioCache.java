package dev.mclite.island.bili;

import dev.mclite.island.core.RemoteAudioResolver;
import dev.mclite.island.core.Track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B 站音频缓存：下载 DASH m4s，用 ffmpeg 转成 WAV（AAC 解码交给 ffmpeg），
 * 之后交给 JavaSoundEngine 播放。
 */
public final class BiliAudioCache implements RemoteAudioResolver {

    private final Path cacheDir;
    private final BiliApi api;
    private volatile BiliQuality quality;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public void setQuality(BiliQuality quality) {
        this.quality = quality == null ? BiliQuality.AUTO : quality;
    }

    public BiliAudioCache(Path cacheDir, BiliApi api, BiliQuality quality) {
        this.cacheDir = cacheDir;
        this.api = api;
        this.quality = quality;
    }

    @Override
    public Path resolve(Track track) throws Exception {
        if (!track.isRemote()) {
            return track.file();
        }
        String key = "bili_" + safe(track.bvid()) + "_" + track.cid() + "_" + track.audioId();
        Path wav = cacheDir.resolve(key + ".wav");
        if (Files.isRegularFile(wav) && Files.size(wav) > 1024) {
            return wav;
        }
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (Files.isRegularFile(wav) && Files.size(wav) > 1024) {
                return wav;
            }
            Files.createDirectories(cacheDir);
            String url = track.remoteUrl();
            if (url == null || url.isBlank()) {
                BiliApi.AudioStream stream = api.playUrl(track.bvid(), track.cid(), quality);
                url = stream.url();
            }
            byte[] audio;
            try {
                audio = api.download(url);
            } catch (IOException expired) {
                // CDN URL 过期：重新解析一次
                BiliApi.AudioStream stream = api.playUrl(track.bvid(), track.cid(), quality);
                audio = api.download(stream.url());
            }
            Path m4s = cacheDir.resolve(key + ".m4s");
            Path tmpWav = cacheDir.resolve(key + ".tmp.wav");
            Files.write(m4s, audio);
            convert(m4s, tmpWav);
            try {
                Files.move(tmpWav, wav, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmpWav, wav, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(m4s);
            return wav;
        }
    }

    private static void convert(Path input, Path output) throws IOException {
        Path ffmpeg = findFfmpeg();
        if (ffmpeg == null) {
            throw new IOException("未找到 ffmpeg：B 站音频是 AAC，需要 ffmpeg 转成 WAV；"
                    + "安装后把路径加到 PATH，或设置环境变量 MCLITE_FFMPEG");
        }
        Process process = new ProcessBuilder(ffmpeg.toString(), "-y", "-loglevel", "error",
                "-i", input.toString(), "-vn", "-ac", "2", "-ar", "44100",
                "-c:a", "pcm_s16le", output.toString())
                .redirectErrorStream(true)
                .start();
        try {
            boolean done = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                throw new IOException("ffmpeg 转码超时");
            }
            if (process.exitValue() != 0) {
                throw new IOException("ffmpeg 转码失败（退出码 " + process.exitValue() + "）");
            }
            if (!Files.isRegularFile(output) || Files.size(output) < 1024) {
                throw new IOException("ffmpeg 没有产出有效 WAV");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("转码被中断", ex);
        }
    }

    public static Path findFfmpeg() {
        String env = System.getenv("MCLITE_FFMPEG");
        if (env != null && !env.isBlank()) {
            Path path = Path.of(env);
            if (Files.isExecutable(path)) {
                return path;
            }
        }
        for (String candidate : new String[]{
                "/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg", "/usr/bin/ffmpeg"}) {
            Path path = Path.of(candidate);
            if (Files.isExecutable(path)) {
                return path;
            }
        }
        return null;
    }

    public void clear() throws IOException {
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (var stream = Files.list(cacheDir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.startsWith("bili_")) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9]", "");
    }
}
