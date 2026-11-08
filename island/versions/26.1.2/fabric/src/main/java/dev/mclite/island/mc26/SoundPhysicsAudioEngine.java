package dev.mclite.island.mc26;

import dev.mclite.island.core.audio.AudioEngine;
import dev.mclite.island.core.audio.JavaSoundEngine;
import dev.mclite.island.audio.PcmEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实验性音频后端：把音乐交给 Minecraft 的 SoundEngine，以玩家为声源播放。
 *
 * <p>Sound Physics Remastered 只处理经过 MC 声音引擎的 OpenAL 声道，
 * 所以 JavaSound 的混响它永远收不到。这里把曲目转成 OGG，挂到自定义
 * {@link SoundBufferLibrary} 上，再用一个普通 {@link SoundInstance} 播放；
 * Sound Physics 就能按玩家所在环境加混响/遮挡。</p>
 *
 * <p>实现是“够用”级别：暂停/seek 通过重新从对应位置转 OGG 再播，
 * 播放位置用墙钟推进；不追求采样级精确。</p>
 */
final class SoundPhysicsAudioEngine implements AudioEngine {

    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static McLibrary library;
    private static boolean loggedActive;
    private static final java.util.Set<Identifier> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final Map<Identifier, java.util.concurrent.atomic.AtomicLong> READ =
            new ConcurrentHashMap<>();

    private SoundManager manager;

    private Path file;
    private long baseMs;
    private long startNanos;
    private long durationMs = -1L;
    private float volume = 0.7f;
    private boolean playing;
    private boolean paused;
    private volatile boolean finished = true;
    private Runnable onEnd = () -> {
    };
    private McInstance instance;
    private final JavaSoundEngine fallback = new JavaSoundEngine();
    private volatile boolean fallbackMode;

    SoundPhysicsAudioEngine() {
        // 构造发生在客户端初始化阶段，SoundEngine 还没准备好；等第一次播放再注入。
    }

    @Override
    public void play(Path file, long startMs) throws Exception {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IOException("玩家未进入世界");
        }
        manager = Minecraft.getInstance().getSoundManager();
        installLibrary();
        if (library == null) {
            fallbackMode = true;
            System.err.println("[mclite-island] Sound Physics 注入失败，回退 JavaSound");
            fallback.play(file, startMs);
            return;
        }
        stopInternal();
        long startAt = Math.max(0L, startMs);
        if (durationMs < 0) {
            durationMs = PcmAudioStream.durationMs(file);
        }
        Identifier id = Identifier.fromNamespaceAndPath("mclite-audio",
                "track_" + SERIAL.incrementAndGet());
        library.add(id, file, startAt);
        Sound sound = new Sound(id, ConstantFloat.of(1f), ConstantFloat.of(1f), 1,
                Sound.Type.FILE, true, false, 0);
        instance = new McInstance(id, sound, volume);
        instance.follow(player);
        System.out.println("[mclite-island] Sound Physics 尝试播放: " + file.getFileName()
                + " @" + startAt + "ms");
        SoundEngine.PlayResult result;
        try {
            result = manager.play(instance);
        } catch (Throwable failure) {
            fallbackMode = true;
            instance = null;
            System.err.println("[mclite-island] Sound Physics play 异常，回退 JavaSound: " + failure);
            fallback.play(file, startAt);
            return;
        }
        if (result == SoundEngine.PlayResult.NOT_STARTED) {
            fallbackMode = true;
            instance = null;
            System.err.println("[mclite-island] Sound Physics 未启动，回退 JavaSound");
            fallback.play(file, startAt);
            return;
        }
        System.out.println("[mclite-island] Sound Physics 音频已启用: " + file.getFileName());
        startWatchdog(id, file, startAt);
        this.file = file;
        this.baseMs = startAt;
        this.startNanos = System.nanoTime();
        this.playing = true;
        this.paused = false;
        this.finished = false;
    }

    private void startWatchdog(Identifier id, Path source, long startAt) {
        McInstance watched = instance;
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(900L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            if (fallbackMode || watched == null || instance != watched) {
                return;
            }
            boolean streamRequested = REQUESTED.contains(id);
            long bytesRead = READ.getOrDefault(id,
                    new java.util.concurrent.atomic.AtomicLong()).get();
            boolean active = manager != null && manager.isActive(watched);
            if (!streamRequested || bytesRead <= 0 || !active) {
                String reason = !streamRequested ? "SoundEngine 没有请求音频流"
                        : bytesRead <= 0 ? "没有读到音频数据" : "声道未激活";
                System.err.println("[mclite-island] " + reason + "，回退 JavaSound");
                long resume = positionMs();
                manager.stop(watched);
                instance = null;
                playing = false;
                paused = false;
                fallbackMode = true;
                fallback.setVolume(volume);
                try {
                    fallback.play(source, resume);
                } catch (Exception ex) {
                    System.err.println("[mclite-island] JavaSound 回退也失败: " + ex);
                }
            }
        }, "mclite-island-sound-watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    private void stopInternal() {
        if (instance != null) {
            if (manager == null) {
                manager = Minecraft.getInstance().getSoundManager();
            }
            manager.stop(instance);
            instance = null;
        }
        playing = false;
    }

    @Override
    public void pause() {
        if (fallbackMode) {
            fallback.pause();
            return;
        }
        if (!playing || paused) {
            return;
        }
        baseMs = positionMs();
        manager.stop(instance);
        instance = null;
        playing = false;
        paused = true;
    }

    @Override
    public void resume() {
        if (fallbackMode) {
            fallback.resume();
            return;
        }
        if (!paused || file == null) {
            return;
        }
        long resumeAt = baseMs;
        paused = false;
        try {
            play(file, resumeAt);
        } catch (Exception ex) {
            paused = true;
        }
    }

    @Override
    public void togglePause() {
        if (fallbackMode) {
            fallback.togglePause();
            return;
        }
        if (paused) {
            resume();
        } else {
            pause();
        }
    }

    @Override
    public void stop() {
        if (fallbackMode) {
            fallback.stop();
            return;
        }
        stopInternal();
        paused = false;
        baseMs = 0;
        finished = true;
    }

    @Override
    public void seek(long positionMs) {
        if (fallbackMode) {
            fallback.seek(positionMs);
            return;
        }
        long target = Math.max(0L, positionMs);
        if (file == null) {
            baseMs = target;
            return;
        }
        boolean wasPlaying = playing && !paused;
        if (wasPlaying) {
            try {
                play(file, target);
                return;
            } catch (Exception ignored) {
                // 退回只记录位置
            }
        }
        baseMs = target;
    }

    @Override
    public void setVolume(float volume) {
        fallback.setVolume(volume);
        this.volume = Math.max(0f, Math.min(1f, volume));
        if (instance != null) {
            instance.setVolume(this.volume);
        }
    }

    @Override
    public boolean isPlaying() {
        return fallbackMode ? fallback.isPlaying() : playing && !paused;
    }

    @Override
    public boolean isPaused() {
        return fallbackMode ? fallback.isPaused() : paused;
    }

    @Override
    public long positionMs() {
        if (fallbackMode) {
            return fallback.positionMs();
        }
        if (playing && !paused) {
            long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
            long position = baseMs + elapsed;
            if (durationMs > 0) {
                return Math.min(durationMs, position);
            }
            return position;
        }
        return baseMs;
    }

    @Override
    public long durationMs() {
        return fallbackMode ? fallback.durationMs() : durationMs;
    }

    @Override
    public void setOnEnd(Runnable listener) {
        this.onEnd = listener == null ? () -> {
        } : listener;
        fallback.setOnEnd(listener);
    }

    @Override
    public void setEffect(PcmEffect effect) {
        fallback.setEffect(effect);
    }

    @Override
    public void tick() {
        if (fallbackMode) {
            fallback.tick();
            return;
        }
        if (instance == null || !playing || paused) {
            return;
        }
        if (manager == null) {
            manager = Minecraft.getInstance().getSoundManager();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            instance.follow(player);
            instance.setVolume(volume);
        }
        if (System.nanoTime() - startNanos < 300_000_000L) {
            return;
        }
        if (!manager.isActive(instance)) {
            playing = false;
            paused = false;
            instance = null;
            if (!finished) {
                finished = true;
                onEnd.run();
            }
        }
    }

    private void installLibrary() {
        synchronized (SoundPhysicsAudioEngine.class) {
            try {
                McLibrary created = library == null ? new McLibrary() : library;
                Field engineField = SoundManager.class.getDeclaredField("soundEngine");
                engineField.setAccessible(true);
                Object engine = engineField.get(manager);
                Field buffersField = SoundEngine.class.getDeclaredField("soundBuffers");
                buffersField.setAccessible(true);
                try {
                    buffersField.set(engine, created);
                } catch (Throwable reflectionFailure) {
                    unsafeSet(engine, buffersField, created);
                }
                library = created;
                System.out.println("[mclite-island] Sound Physics 音频后端注入成功");
            } catch (Throwable failure) {
                System.err.println("[mclite-island] Sound Physics 音频后端注入失败: " + failure);
            }
        }
    }

    private static void unsafeSet(Object target, Field field, Object value) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        java.lang.reflect.Method offset = unsafeClass.getMethod("objectFieldOffset", Field.class);
        long fieldOffset = (Long) offset.invoke(unsafe, field);
        java.lang.reflect.Method putObject = unsafeClass.getMethod("putObject",
                Object.class, long.class, Object.class);
        putObject.invoke(unsafe, target, fieldOffset, value);
    }

    /** 自定义 Sound 的音频流来源；其他声音回退到原版资源。 */
    private record Source(Path path, long startMs) {
    }

    private static final class McLibrary extends SoundBufferLibrary {
        private final Map<Identifier, Source> files = new ConcurrentHashMap<>();

        McLibrary() {
            super(Minecraft.getInstance().getResourceManager());
        }

        void add(Identifier id, Path path, long startMs) {
            files.put(id, new Source(path, startMs));
        }

        @Override
        public CompletableFuture<AudioStream> getStream(Identifier id, boolean looping) {
            Source source = files.get(id);
            if (source == null) {
                return super.getStream(id, looping);
            }
            REQUESTED.add(id);
            System.out.println("[mclite-island] Sound Physics 请求音频流: " + id);
            try {
                PcmAudioStream stream = new PcmAudioStream(source.path(), source.startMs());
                java.util.concurrent.atomic.AtomicLong counter =
                        READ.computeIfAbsent(id, key -> new java.util.concurrent.atomic.AtomicLong());
                return CompletableFuture.completedFuture(new AudioStream() {
                    @Override
                    public javax.sound.sampled.AudioFormat getFormat() {
                        return stream.getFormat();
                    }

                    @Override
                    public java.nio.ByteBuffer read(int size) throws IOException {
                        java.nio.ByteBuffer buffer = stream.read(size);
                        if (buffer.hasRemaining()) {
                            counter.addAndGet(buffer.remaining());
                        }
                        return buffer;
                    }

                    @Override
                    public void close() throws IOException {
                        stream.close();
                    }
                });
            } catch (Exception ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }
    }

    /** 以玩家为声源的普通 SoundInstance。 */
    private static final class McInstance extends AbstractSoundInstance {
        McInstance(Identifier id, Sound sound, float volume) {
            super(id, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.sound = sound;
            this.volume = volume;
            this.attenuation = Attenuation.LINEAR;
            this.relative = false;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            WeighedSoundEvents events = new WeighedSoundEvents(identifier, null);
            events.addSound(sound);
            return events;
        }

        void setVolume(float value) {
            this.volume = value;
        }

        void follow(LocalPlayer player) {
            var eye = player.getEyePosition();
            this.x = eye.x;
            this.y = eye.y;
            this.z = eye.z;
        }
    }
}
