package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Classifies launch failures from the latest log/crash report and suggests a fix. */
final class LaunchDiagnostics {
    record Finding(String kind, String summary, String action) {
    }

    private LaunchDiagnostics() {
    }

    static List<Finding> analyze(String text) {
        List<Finding> findings = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return findings;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("unsupportedclassversionerror")) {
            findings.add(new Finding("java",
                    "Java 版本低于游戏要求",
                    "在启动器配置里切换到更高版本的 Java，或让启动器自动下载。"));
        }
        if (lower.contains("outofmemoryerror") || lower.contains("java heap space")) {
            findings.add(new Finding("memory",
                    "游戏内存不足",
                    "在设置里调高内存，或关闭占用内存的模组/光影。"));
        }
        if (lower.contains("which is missing") || lower.contains("missing dependency")) {
            findings.add(new Finding("mod-dependency",
                    "缺少模组前置",
                    "按日志里的模组名补装前置，或停用依赖它的模组。"));
        }
        if (lower.contains("noclassdeffounderror") || lower.contains("classnotfoundexception")) {
            findings.add(new Finding("mod-conflict",
                    "模组与当前版本不兼容",
                    "停用日志里出现的最新模组，或升级到对应游戏版本。"));
        }
        if (lower.contains("failed to download") || lower.contains("could not download")) {
            findings.add(new Finding("assets",
                    "游戏文件或资源下载失败",
                    "在下载页对所选版本点「修复 / 更新」后重试。"));
        }
        return findings;
    }

    static List<Finding> analyzeReport(Path report) {
        try {
            return analyze(Files.readString(report, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            Log.warn("读取崩溃报告失败: " + ex.getMessage());
            return List.of();
        }
    }

    /** Newest crash report under a version directory, or null when none exists. */
    static Path latestCrashReport(Path versionDir) {
        Path dir = versionDir.resolve("crash-reports");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var list = Files.list(dir)) {
            return list.filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .max(java.util.Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }
}
