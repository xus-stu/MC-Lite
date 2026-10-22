package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class CrashAnalyzer {
    public record Result(String summary, List<String> suspectMods, String tail, Path source) {
    }

    private CrashAnalyzer() {
    }

    public static Result analyze(Path logFile, Path contentDir) {
        Path crash = latestCrash(contentDir);
        Path source = crash != null ? crash : logFile;
        if (source == null || !Files.isRegularFile(source)) {
            return new Result("没有找到崩溃日志", List.of(), "", source);
        }
        String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return new Result("无法读取崩溃日志：" + ex.getMessage(), List.of(), "", source);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String summary = "";
        for (String line : text.split("\\R")) {
            String trim = line.trim();
            if (trim.startsWith("Description:") || trim.startsWith("Caused by:")
                    || trim.startsWith("java.lang.") && summary.isEmpty()) {
                summary = trim;
                if (trim.startsWith("Description:")) {
                    break;
                }
            }
        }
        if (summary.isBlank()) {
            summary = "游戏异常退出；请查看下方日志。";
        }
        List<String> suspects = new ArrayList<>();
        Path modsDir = contentDir == null ? null : contentDir.resolve("mods");
        if (modsDir != null && Files.isDirectory(modsDir)) {
            try (var stream = Files.list(modsDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        continue;
                    }
                    String base = name.substring(0, name.length() - 4);
                    String probe = base.toLowerCase(Locale.ROOT);
                    int cut = probe.indexOf('-');
                    if (cut > 3) {
                        probe = probe.substring(0, cut);
                    }
                    if (probe.length() >= 3 && lower.contains(probe)) {
                        suspects.add(name);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        suspects = new ArrayList<>(new LinkedHashSet<>(suspects));
        String[] lines = text.split("\\R");
        int from = Math.max(0, lines.length - 140);
        StringBuilder tail = new StringBuilder();
        for (int i = from; i < lines.length; i++) {
            tail.append(lines[i]).append('\n');
        }
        return new Result(summary, suspects, tail.toString(), source);
    }

    private static Path latestCrash(Path contentDir) {
        if (contentDir == null) {
            return null;
        }
        Path dir = contentDir.resolve("crash-reports");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }
}
