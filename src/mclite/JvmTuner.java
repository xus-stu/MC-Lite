package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

public final class JvmTuner {
    private static final Map<String, Tuning> CACHE = new ConcurrentHashMap<>();

    public record Tuning(List<String> args, String reason) {
        public boolean applied() {
            return !args.isEmpty();
        }
    }

    private JvmTuner() {
    }

    public static Tuning tune(Path java, int major, int memoryMb) {
        if (java == null || major < 8) {
            return new Tuning(List.of(), "Java 版本不支持自动 G1 参数");
        }
        String key = java.toAbsolutePath().normalize() + "|" + major + "|"
                + Math.max(1, memoryMb / 1024);
        Tuning cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        synchronized (CACHE) {
            cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            Tuning tuning = detectAndValidate(java, major, memoryMb);
            CACHE.put(key, tuning);
            return tuning;
        }
    }

    private static Tuning detectAndValidate(Path java, int major, int memoryMb) {
        List<String> full = new ArrayList<>(List.of(
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+PerfDisableSharedMem",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:MaxTenuringThreshold=1"));
        full.add("-XX:G1HeapRegionSize=" + (memoryMb >= 4096 ? "8M" : "4M"));
        Tuning result = validate(java, full);
        if (result.applied()) {
            return result;
        }

        List<String> safe = List.of(
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+PerfDisableSharedMem");
        result = validate(java, safe);
        if (result.applied()) {
            return result;
        }
        return new Tuning(List.of(), "没有找到该 Java 可接受的优化参数");
    }

    private static Tuning validate(Path java, List<String> flags) {
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.addAll(flags);
        command.add("-XshowSettings:properties");
        command.add("-version");
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(6, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Tuning(List.of(), "JVM 参数检测超时");
            }
            if (process.exitValue() != 0) {
                return new Tuning(List.of(), "JVM 不接受这些优化参数");
            }
            String vm = "";
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("java.vm.name =")) {
                    vm = trimmed.substring("java.vm.name =".length()).trim();
                    break;
                }
            }
            String lower = vm.toLowerCase(Locale.ROOT);
            if (!(lower.contains("hotspot") || lower.contains("openjdk"))) {
                return new Tuning(List.of(), "非 HotSpot JVM，跳过参数");
            }
            return new Tuning(List.copyOf(flags), "HotSpot G1 自动参数");
        } catch (IOException ex) {
            return new Tuning(List.of(), "无法启动该 Java 进行参数检测");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Tuning(List.of(), "JVM 参数检测被中断");
        }
    }
}
