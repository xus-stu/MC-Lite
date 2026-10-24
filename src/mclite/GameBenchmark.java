package mclite;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public final class GameBenchmark {
    public record Result(String version, long startupMs, long cpuMs, long score,
                         double averageFps, boolean fpsMeasured, boolean exited) {
    }

    private GameBenchmark() {
    }

    public static Result run(Config config, VersionMeta meta,
                             VersionManifest.VersionEntry entry,
                             JavaManager.JavaInfo javaInfo) throws Exception {
        long start = System.nanoTime();
        GameLauncher.LaunchResult launched = GameLauncher.launch(config, meta, entry, javaInfo);
        Process process = launched.process();
        long started = System.nanoTime();
        long deadline = started + 90_000_000_000L;
        boolean entered = false;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new java.io.IOException("游戏在进入主界面前退出，请查看日志");
            }
            if (looksStarted(launched.logFile())) {
                entered = true;
                break;
            }
            Thread.sleep(500);
        }
        if (!entered) {
            process.destroyForcibly();
            throw new java.io.IOException("等待游戏进入主界面超时");
        }
        long startupMs = Math.max(1, (System.nanoTime() - start) / 1_000_000);
        double[] fps = new double[]{-1};
        Thread sampler = new Thread(() -> fps[0] = sampleFps(12_000), "mclite-fps-sample");
        sampler.setDaemon(true);
        sampler.start();
        Thread.sleep(12_000);
        sampler.join(2000);
        Duration cpu = process.info().totalCpuDuration().orElse(Duration.ZERO);
        boolean exited = !process.isAlive();
        if (!exited) {
            process.destroy();
            if (!process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        long cpuMs = Math.max(1, cpu.toMillis());
        long fpsPart = fps[0] > 0 ? Math.round(fps[0] * 100) : 0;
        long score = Math.max(1, 120_000L / Math.max(1, startupMs / 1000)
                + cpuMs / 4 + fpsPart);
        return new Result(meta.id(), startupMs, cpuMs, score, fps[0], fps[0] >= 0, exited);
    }

    private static double sampleFps(int durationMs) {
        try {
            java.awt.Robot robot = new java.awt.Robot();
            java.awt.Rectangle screen = java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration().getBounds();
            int width = Math.max(320, Math.min(960, screen.width * 2 / 3));
            int height = Math.max(180, Math.min(540, screen.height * 2 / 3));
            java.awt.Rectangle region = new java.awt.Rectangle(
                    screen.x + (screen.width - width) / 2,
                    screen.y + (screen.height - height) / 2, width, height);
            int[] previous = null;
            int changes = 0;
            long started = System.nanoTime();
            long deadline = started + durationMs * 1_000_000L;
            while (System.nanoTime() < deadline) {
                java.awt.image.BufferedImage image = robot.createScreenCapture(region);
                int[] sample = new int[image.getWidth() * image.getHeight() / 64];
                int index = 0;
                for (int y = 0; y < image.getHeight(); y += 8) {
                    for (int x = 0; x < image.getWidth(); x += 8) {
                        if (index < sample.length) {
                            sample[index++] = image.getRGB(x, y);
                        }
                    }
                }
                if (previous != null) {
                    int changed = 0;
                    for (int i = 0; i < Math.min(previous.length, index); i++) {
                        int a = previous[i];
                        int b = sample[i];
                        int delta = Math.abs((a & 0xFF) - (b & 0xFF))
                                + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                                + Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
                        if (delta > 24) {
                            changed++;
                        }
                    }
                    if (changed > 4) {
                        changes++;
                    }
                }
                previous = sample;
                Thread.sleep(16);
            }
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
            return seconds > 0 ? changes / seconds : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private static boolean looksStarted(java.nio.file.Path log) {
        try {
            String text = java.nio.file.Files.readString(log);
            return text.contains("Created: ")
                    || text.contains("OpenAL initialized")
                    || text.contains("Sound engine started")
                    || text.contains("Backend library: LWJGL");
        } catch (Exception ignored) {
            return false;
        }
    }
}
