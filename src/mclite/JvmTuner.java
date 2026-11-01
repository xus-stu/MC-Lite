package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JvmTuner {
    private static final Map<String, Tuning> CACHE = new ConcurrentHashMap<>();
    private static final Pattern LEGACY_VERSION = Pattern.compile(
            "(?:^|[^\\d])(1\\.(?:7|8|9|10|11|12)(?:\\.\\d+)?)(?:[^\\d]|$)");

    public record Tuning(List<String> args, String reason) {
        public boolean applied() {
            return !args.isEmpty();
        }
    }

    private JvmTuner() {
    }

    public static Tuning tune(Path java, int major, int memoryMb) {
        return tune(java, major, memoryMb, "");
    }

    public static Tuning tune(Path java, int major, int memoryMb, String gameVersion) {
        if (java == null || major < 8) {
            return new Tuning(List.of(), "Java 版本不支持自动 GC 参数");
        }
        boolean legacy = isLegacyGame(gameVersion);
        String key = java.toAbsolutePath().normalize() + "|" + major + "|"
                + Math.max(1, memoryMb / 1024) + "|" + (major <= 8 ? "java8" : legacy ? "legacy" : "modern");
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

    static boolean isLegacyGame(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        Matcher matcher = LEGACY_VERSION.matcher(version);
        return matcher.find();
    }

    static List<String> java8Args(int memoryMb) {
        int threads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        return List.of(
                "-XX:+UseParallelGC",
                "-XX:+UseAdaptiveSizePolicy",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+PerfDisableSharedMem",
                "-XX:ParallelGCThreads=" + threads);
    }

    private static Tuning detectAndValidate(Path java, int major, int memoryMb) {
        if (major <= 8) {
            Tuning result = validate(java, java8Args(memoryMb));
            if (result.applied()) {
                return result;
            }
            result = validate(java, List.of("-XX:+UseParallelGC"));
            return result.applied() ? result
                    : new Tuning(List.of(), "Java 8 不接受 ParallelGC 参数");
        }

        List<String> full = new ArrayList<>(List.of(
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+PerfDisableSharedMem",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:G1NewSizePercent=20",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1ReservePercent=15",
                "-XX:G1HeapWastePercent=10",
                "-XX:G1MixedGCCountTarget=8",
                "-XX:InitiatingHeapOccupancyPercent=20",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5"));
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
            return new Tuning(List.copyOf(flags),
                    flags.stream().anyMatch(flag -> flag.contains("UseParallelGC"))
                            ? "低版本 Java 8 ParallelGC 参数" : "HotSpot G1 自动参数");
        } catch (IOException ex) {
            return new Tuning(List.of(), "无法启动该 Java 进行参数检测");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Tuning(List.of(), "JVM 参数检测被中断");
        }
    }
}
