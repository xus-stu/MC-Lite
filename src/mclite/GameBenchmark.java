package mclite;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public final class GameBenchmark {
    public record Result(String version, long startupMs, long cpuMs, long score, boolean exited) {
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
        Thread.sleep(12_000);
        Duration cpu = process.info().totalCpuDuration().orElse(Duration.ZERO);
        boolean exited = !process.isAlive();
        if (!exited) {
            process.destroy();
            if (!process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        long startupMs = Math.max(1, (started - start) / 1_000_000);
        long cpuMs = Math.max(1, cpu.toMillis());
        long score = Math.max(1, 120_000L / Math.max(1, startupMs / 1000)
                + cpuMs / 4);
        return new Result(meta.id(), startupMs, cpuMs, score, exited);
    }
}
