package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * 崩溃分析：从异常栈/报错行里定位可疑模组，并给出可执行的解决建议。
 *
 * <p>关键：不能拿整份崩溃报告去搜模组名——报告本身的「Fabric Mods」段就列了所有模组，
 * 一搜就会全员可疑。这里只取真正的信号：栈帧、Caused by、mixin 报错、加载器依赖报错，
 * 再用 Fabric 元数据（fabric.mod.json 的 id/name/入口包）把类名映射回模组。</p>
 */
public final class CrashAnalyzer {

    public record Result(String summary, List<String> suspectMods, List<String> advice,
                         String tail, Path source) {
    }

    private static final Pattern ANNOTATED_JAR = Pattern.compile("~\\[([^]:]+)\\.jar");
    private static final Pattern LOADER_MOD = Pattern.compile(
            "(?i)mod\\s+['\"]?([a-z0-9_.\\-]+)['\"]?");
    private static final String[] LIST_PREFIXES = {"packs:", "resource packs:", "data packs:",
            "active data packs:", "available data packs:", "jvm flags:", "fabric mods:"};

    private CrashAnalyzer() {
    }

    public static Result analyze(Path logFile, Path contentDir) {
        Path crash = latestCrash(contentDir);
        Path source = crash != null ? crash : logFile;
        if (source == null || !Files.isRegularFile(source)) {
            return new Result("没有找到崩溃日志", List.of(), List.of(), "", source);
        }
        String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return new Result("无法读取崩溃日志：" + ex.getMessage(), List.of(), List.of(), "", source);
        }
        String[] lines = text.split("\\R");
        String summary = summary(lines);
        List<String> signal = signal(lines);
        Map<String, ModInfo> mods = modIndex(contentDir);
        // 得分：栈帧/mixin/加载器报错里出现得越靠前、越具体，分越高。
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<String, ModInfo> mod : mods.entrySet()) {
            String id = mod.getKey();
            String name = mod.getValue().name();
            List<String> prefixes = mod.getValue().prefixes();
            int score = 0;
            for (int i = 0; i < signal.size(); i++) {
                String line = signal.get(i).toLowerCase(Locale.ROOT);
                String hit = tokenHit(line, id, name, prefixes);
                if (hit == null) {
                    continue;
                }
                boolean frame = line.trim().startsWith("at ");
                if (frame && (line.contains("mixinextras$bridge")
                        || line.contains("wrapoperation$"))) {
                    continue;
                }
                boolean mixin = hit.equals("jar")
                        || line.contains("mixin apply") || line.contains("mixin config");
                int weight = frame ? 3 : 1;
                if (frame && i < 6) {
                    weight += 2;
                }
                if (mixin) {
                    weight += 4;
                }
                score += weight;
            }
            if (score > 0) {
                scores.merge(id, score, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        List<String> suspects = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ranked) {
            if (suspects.size() >= 4 || entry.getValue() < 2) {
                break;
            }
            suspects.add(suspects.size() == 0
                    ? entry.getKey() + "（最可疑）" : entry.getKey());
        }
        List<String> advice = advice(text.toLowerCase(Locale.ROOT), suspects);
        StringBuilder tailText = new StringBuilder();
        for (int i = Math.max(0, lines.length - 120); i < lines.length; i++) {
            tailText.append(lines[i]).append('\n');
        }
        return new Result(summary, suspects, advice, tailText.toString(), source);
    }

    private static String tokenHit(String line, String id, String name, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (line.contains(prefix)) {
                return "pkg";
            }
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (key.length() >= 4 && containsWord(line, key)) {
            return key;
        }
        String label = name.toLowerCase(Locale.ROOT);
        if (label.length() >= 5 && line.contains(label)) {
            return label;
        }
        // 栈帧里带 jar 注解（Fabric 有时会加 ~[xxx.jar:...]）
        Matcher matcher = ANNOTATED_JAR.matcher(line);
        while (matcher.find()) {
            if (matcher.group(1).toLowerCase(Locale.ROOT).contains(key)) {
                return "jar";
            }
        }
        return null;
    }

    private static boolean containsWord(String line, String key) {
        int from = 0;
        while (true) {
            int at = line.indexOf(key, from);
            if (at < 0) {
                return false;
            }
            boolean left = at == 0 || !Character.isLetterOrDigit(line.charAt(at - 1));
            int end = at + key.length();
            boolean right = end >= line.length() || !Character.isLetterOrDigit(line.charAt(end));
            if (left && right) {
                return true;
            }
            from = at + 1;
        }
    }

    /** 只保留真正的信号行：栈帧、异常/报错、mixin、加载器依赖错误；跳过所有模组/资源包列表。 */
    private static List<String> signal(String[] lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String trim = line.trim();
            if (trim.isEmpty()) {
                continue;
            }
            String lower = trim.toLowerCase(Locale.ROOT);
            boolean listLine = false;
            for (String prefix : LIST_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    listLine = true;
                    break;
                }
            }
            if (listLine) {
                continue;
            }
            boolean keep = lower.startsWith("at ")
                    || lower.startsWith("caused by")
                    || lower.startsWith("description:")
                    || lower.startsWith("java.") || lower.startsWith("exception")
                    || lower.contains("mixin")
                    || lower.contains("failed")
                    || lower.contains("requires")
                    || lower.contains("incompatible")
                    || lower.contains("missing")
                    || lower.contains("suspected")
                    || lower.startsWith("screen name")
                    || lower.contains("ticking entity")
                    || lower.contains("ticking block entity")
                    || lower.contains("error")
                    || lower.contains(": ") && out.size() < 20;
            if (keep) {
                out.add(trim);
            }
            if (out.size() >= 260) {
                break;
            }
        }
        return out;
    }

    private static String summary(String[] lines) {
        String summary = "";
        for (String line : lines) {
            String trim = line.trim();
            if (trim.startsWith("Description:") || trim.startsWith("Caused by:")) {
                summary = trim;
                if (trim.startsWith("Description:")) {
                    break;
                }
            }
            if (summary.isEmpty() && trim.startsWith("java.") && trim.contains("Exception")) {
                summary = trim;
            }
        }
        return summary.isEmpty() ? "游戏异常退出；请看下方日志。" : summary;
    }

    private record ModInfo(String name, List<String> prefixes) {
    }

    /** Fabric 元数据：模组 id → 显示名 + 入口类包前缀，用来把栈帧/报错映射回模组。 */
    private static Map<String, ModInfo> modIndex(Path contentDir) {
        Map<String, ModInfo> out = new LinkedHashMap<>();
        Path modsDir = contentDir == null ? null : contentDir.resolve("mods");
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(modsDir)) {
            for (Path jar : stream.filter(Files::isRegularFile).toList()) {
                String file = jar.getFileName().toString();
                if (!file.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                String id = file.substring(0, file.length() - 4);
                String name = id;
                List<String> prefixes = new ArrayList<>();
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    var entry = zip.getEntry("fabric.mod.json");
                    if (entry != null) {
                        String json = new String(zip.getInputStream(entry).readAllBytes(),
                                StandardCharsets.UTF_8);
                        String parsedId = simpleValue(json, "id");
                        String parsedName = simpleValue(json, "name");
                        if (parsedId != null && !parsedId.isBlank()) {
                            id = parsedId;
                        }
                        if (parsedName != null && !parsedName.isBlank()) {
                            name = parsedName;
                        }
                        Matcher classes = Pattern.compile(
                                "\"([a-z][a-z0-9_.]*\\.[A-Z][A-Za-z0-9_$]*)\"").matcher(json);
                        while (classes.find() && prefixes.size() < 3) {
                            String fqcn = classes.group(1);
                            int dot = fqcn.lastIndexOf('.');
                            if (dot > 0) {
                                String pkg = fqcn.substring(0, dot);
                                String[] parts = pkg.split("\\.");
                                String trimmed = String.join(".",
                                        java.util.Arrays.copyOf(parts, Math.min(parts.length, 3)));
                                if (trimmed.length() >= 6 && !prefixes.contains(trimmed)) {
                                    prefixes.add(trimmed);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 坏 jar 跳过，用文件名当 id。
                }
                out.put(id, new ModInfo(name, prefixes));
            }
        } catch (IOException ignored) {
            // 目录读不了就返回空表。
        }
        return out;
    }

    private static String simpleValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> advice(String lower, List<String> suspects) {
        List<String> out = new ArrayList<>();
        String suspect = suspects.isEmpty() ? null
                : suspects.get(0).replace("（最可疑）", "");
        if (lower.contains("can only blur once per frame")) {
            out.add("界面重复调用背景模糊：升级 MCLite Island 到 0.3.9+（0.1.x 会重复调用）。");
        }
        if (lower.contains("mixin apply failed") || lower.contains("mixin config")) {
            out.add("Mixin 冲突：多为版本不匹配或两个模组改同一处。先禁用"
                    + (suspect == null ? "最近新增的模组" : "「" + suspect + "」")
                    + "验证，再更新到 26.1.2 版本。");
        }
        boolean missingClass = lower.contains("noclassdeffounderror")
                || lower.contains("nosuchmethoderror") || lower.contains("classnotfoundexception");
        if (missingClass) {
            out.add("缺类/方法：模组与游戏版本不匹配或缺前置，换成 26.1.2 版本或补前置。");
        }
        if (lower.contains("outofmemoryerror") || lower.contains("java heap space")) {
            out.add("内存不足：设置里把内存调到 4–8 GB（M5 建议 6G），并减少同时加载的重型模组。");
        }
        if (lower.contains("unsupportedclassversion")) {
            out.add("Java 版本过低：这个模组需要更高版本的 Java，换用模组要求的 Java 25+。");
        }
        if (lower.contains("mod resolution") || lower.contains("incompatible mods")
                || lower.contains("requires") && lower.contains("of mod")) {
            out.add("加载器依赖冲突：按提示补齐前置或降级；刚更新过的模组回退试试。");
        }
        if (lower.contains("ticking entity") || lower.contains("ticking block entity")
                || lower.contains("exception in server tick loop")) {
            out.add("方块/实体 tick 崩溃：备份存档，按日志坐标清理那一块；常因模组更新后旧数据不兼容。");
        }
        if (lower.contains("opengl") || lower.contains("glfw")) {
            out.add("图形问题：先把光影切回原版，再更新显卡驱动。");
        }
        if (out.isEmpty()) {
            out.add("通用排查：备份存档，禁用最近新增/更新的模组启动一次，再用二分法定位。");
        }
        return out;
    }

    private static Path latestCrash(Path contentDir) {
        if (contentDir == null) {
            return null;
        }
        Path dir = contentDir.resolve("crash-reports");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }
}
