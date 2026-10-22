package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaManager {
    private static final String RUNTIME_INDEX =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?:java|openjdk|graalvm)\\s+version\\s+\"?(?:1\\.)?(\\d+)(?:\\.(\\d+))?",
                    Pattern.CASE_INSENSITIVE);
    private static final int RUNTIME_THREADS = 4;

    private final Config config;
    private final ProgressSink progress;
    private final CancellationToken token;
    private final Net net;

    public JavaManager(Config config, ProgressSink progress, CancellationToken token) {
        this.config = config;
        this.progress = progress == null ? ProgressSink.NOOP : progress;
        this.token = token == null ? new CancellationToken() : token;
        this.net = new Net(progress, token);
    }

    public record JavaInfo(Path path, int major, String label, String component, boolean official) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record Probe(int major, String vendor, String firstLine) {
    }

    public List<JavaInfo> detectAll() {
        LinkedHashMap<String, JavaInfo> found = new LinkedHashMap<>();
        for (JavaInfo runtime : installedRuntimes()) {
            found.put(runtime.path().toString(), runtime);
        }
        for (Path candidate : systemCandidates()) {
            token.check();
            try {
                Path java = normalizeJavaPath(candidate);
                if (java == null || !Files.isRegularFile(java)) {
                    continue;
                }
                String key = java.toRealPath().toString();
                if (found.containsKey(key)) {
                    continue;
                }
                Probe probe = probe(java);
                if (probe.major() > 0) {
                    found.put(key, new JavaInfo(java.toAbsolutePath().normalize(), probe.major(),
                            "Java " + probe.major() + " · " + probe.vendor(), null, false));
                }
            } catch (IOException | RuntimeException ex) {

            }
        }
        List<JavaInfo> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparingInt(JavaInfo::major).reversed()
                .thenComparing(info -> info.path().toString()));
        return result;
    }

    public List<JavaInfo> installedRuntimes() {
        List<JavaInfo> result = new ArrayList<>();
        Path root = config.runtimesDir();
        if (!Files.isDirectory(root)) {
            return result;
        }
        try (var dirs = Files.newDirectoryStream(root)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                Path java = runtimeJavaPath(dir);
                if (java == null || !Files.isRegularFile(java)) {
                    continue;
                }
                Probe probe = probe(java);
                if (probe.major() > 0) {
                    result.add(new JavaInfo(java, probe.major(),
                            "Java " + probe.major() + " · Mojang 官方", dir.getFileName().toString(), true));
                }
            }
        } catch (IOException ex) {
            Log.warn("扫描官方 Java 运行时失败: " + ex.getMessage());
        }
        return result;
    }

    public JavaInfo findInstalled(String component, int major) {
        if (component != null && component.matches("[a-z0-9-]{1,60}")) {
            Path dir = config.runtimesDir().resolve(component);
            Path java = runtimeJavaPath(dir);
            if (java != null && Files.isRegularFile(java) && Files.isRegularFile(dir.resolve(".complete"))) {
                Probe probe = probe(java);
                if (probe.major() > 0) {
                    return new JavaInfo(java, probe.major(),
                            "Java " + probe.major() + " · Mojang 官方", component, true);
                }
            }
        }
        for (JavaInfo info : installedRuntimes()) {
            if (info.major() == major) {
                return info;
            }
        }
        return null;
    }

    public static JavaInfo choose(List<JavaInfo> choices, int requiredMajor, String component) {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JavaInfo exactOfficial = null;
        JavaInfo exact = null;
        JavaInfo newer = null;
        for (JavaInfo info : choices) {
            if (info.official() && component != null && component.equals(info.component())
                    && info.major() == requiredMajor) {
                exactOfficial = info;
                break;
            }
            if (info.major() == requiredMajor && exact == null) {
                exact = info;
            }
            if (info.major() >= requiredMajor && (newer == null || info.major() < newer.major())) {
                newer = info;
            }
        }
        if (exactOfficial != null) {
            return exactOfficial;
        }
        if (exact != null) {
            return exact;
        }
        if (newer != null) {
            return newer;
        }
        JavaInfo best = choices.get(0);
        for (JavaInfo info : choices) {
            if (info.major() > best.major()) {
                best = info;
            }
        }
        return best;
    }

    public JavaInfo install(String component, int major) throws IOException {
        String requested = component;
        if (requested == null || requested.isBlank()) {
            requested = componentForMajor(major);
        }
        if (requested == null || !requested.matches("[a-z0-9-]{1,60}")) {
            throw new IOException("无法确定该版本需要的 Java 运行时");
        }
        JavaInfo existing = findInstalled(requested, major);
        if (existing != null) {
            return existing;
        }

        Path runtimeIndex = fetchRuntimeIndex();
        String platform = platformKey();
        Map<String, Object> index = Json.obj(Json.parse(Files.readString(runtimeIndex, StandardCharsets.UTF_8)));
        Map<String, Object> platformMap = Json.obj(index.get(platform));
        List<Object> candidates = Json.arr(platformMap.get(requested));
        if (candidates.isEmpty()) {
            throw new IOException("Mojang 官方没有为当前平台提供 " + requested
                    + "。请手动安装 Java " + major + " 并在设置中选择。");
        }
        Map<String, Object> chosen = null;
        String versionName = "";
        for (Object candidate : candidates) {
            Map<String, Object> map = Json.obj(candidate);
            Map<String, Object> manifest = Json.obj(map.get("manifest"));
            if (Json.str(manifest, "url") != null) {
                chosen = manifest;
                versionName = Json.str(Json.obj(map.get("version")), "name", "");
            }
        }
        if (chosen == null) {
            throw new IOException("官方 Java 运行时索引格式异常");
        }

        progress.stage("获取 Java " + major + " 运行时信息…");
        byte[] manifestBytes = net.getBytes(Json.str(chosen, "url"), Json.str(chosen, "sha1"),
                Json.lng(chosen, "size", -1), "Java " + major + " 运行时清单");
        Map<String, Object> manifest = Json.obj(Json.parse(new String(manifestBytes, StandardCharsets.UTF_8)));
        Map<String, Object> files = Json.obj(manifest.get("files"));
        if (files.isEmpty()) {
            throw new IOException("官方 Java 运行时清单为空");
        }

        Path root = config.runtimesDir().resolve(requested);
        Files.createDirectories(root);

        List<RuntimeFile> regularFiles = new ArrayList<>();
        List<RuntimeLink> links = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Object> entry : files.entrySet()) {
            token.check();
            Map<String, Object> file = Json.obj(entry.getValue());
            String type = Json.str(file, "type", "");
            if ("directory".equals(type)) {
                Path dir = GamePaths.resolveUnder(root, entry.getKey());
                Files.createDirectories(dir);
            } else if ("file".equals(type)) {
                Map<String, Object> raw = Json.obj(Json.obj(file.get("downloads")).get("raw"));
                String url = Json.str(raw, "url");
                if (url == null) {
                    throw new IOException("Java 运行时文件缺少下载地址: " + entry.getKey());
                }
                long size = Json.lng(raw, "size", -1);
                regularFiles.add(new RuntimeFile(GamePaths.resolveUnder(root, entry.getKey()), url,
                        Json.str(raw, "sha1"), size, Json.bool(file, "executable", false)));
                total += Math.max(0, size);
            } else if ("link".equals(type)) {
                links.add(new RuntimeLink(GamePaths.resolveUnder(root, entry.getKey()),
                        Json.str(file, "target", "")));
            }
        }
        progress.total(total);
        progress.stage("下载 Java " + major + " 运行时（" + regularFiles.size() + " 个文件）…");
        Log.info("安装官方 Java 运行时 " + requested + "（" + versionName + "），共 "
                + Formatting.bytes(total));

        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(RUNTIME_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "mclite-java");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (RuntimeFile file : regularFiles) {
                token.check();
                futures.add(pool.submit(() -> {
                    net.download(file.url(), file.target(), file.sha1(), file.size(),
                            "Java 运行时 " + file.target().getFileName(), true);
                    if (file.executable()) {
                        makeExecutable(file.target());
                    }
                    int n = done.incrementAndGet();
                    if (n % 10 == 0 || n == regularFiles.size()) {
                        progress.stage("下载 Java " + major + " 运行时（" + n + "/" + regularFiles.size() + "）…");
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                token.check();
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    token.cancel();
                    throw new CancellationToken.CancelledException();
                } catch (ExecutionException ex) {
                    token.cancel();
                    Throwable cause = ex.getCause();
                    if (cause instanceof CancellationToken.CancelledException) {
                        throw (CancellationToken.CancelledException) cause;
                    }
                    throw new IOException(cause == null ? "Java 运行时下载失败" : cause.getMessage(), cause);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        progress.stage("创建 Java 运行时链接…");
        for (RuntimeLink link : links) {
            token.check();
            createLinkSafely(root, link);
        }

        Path java = runtimeJavaPath(root);
        if (java == null || !Files.isRegularFile(java)) {
            throw new IOException("官方 Java 运行时安装后缺少 java 可执行文件");
        }
        makeExecutable(java);
        Probe probe = probe(java);
        if (probe.major() <= 0) {
            throw new IOException("下载的 Java 运行时无法正常执行");
        }

        Properties marker = new Properties();
        marker.setProperty("component", requested);
        marker.setProperty("major", Integer.toString(probe.major()));
        marker.setProperty("version", versionName);
        marker.setProperty("installedAt", Config.timestamp());
        marker.setProperty("javaPath", java.toString());
        Path complete = root.resolve(".complete");
        try (var out = Files.newOutputStream(complete)) {
            marker.store(out, "MC Lite Java runtime");
        }
        progress.stage("Java " + probe.major() + " 安装完成");
        Log.info("官方 Java 运行时安装完成: " + java);
        return new JavaInfo(java, probe.major(), "Java " + probe.major() + " · Mojang 官方",
                requested, true);
    }

    private Path fetchRuntimeIndex() throws IOException {
        Path cache = config.cacheDir().resolve("java-runtimes.json");
        boolean offline = Boolean.getBoolean("mclite.offline");
        IOException networkError = null;
        if (!offline) {
            try {
                byte[] bytes = net.getUnverifiedBytes(RUNTIME_INDEX, "官方 Java 运行时索引");
                Files.createDirectories(cache.getParent());
                Path temp = cache.resolveSibling("java-runtimes.json.tmp");
                Files.write(temp, bytes);
                Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING);
                return cache;
            } catch (CancellationToken.CancelledException ex) {
                throw ex;
            } catch (IOException | RuntimeException ex) {
                networkError = ex instanceof IOException io ? io : new IOException(ex.getMessage(), ex);
                Log.warn("获取 Java 运行时索引失败，尝试缓存: " + ex.getMessage());
            }
        }
        if (Files.isRegularFile(cache)
                && (offline || Duration.between(Files.getLastModifiedTime(cache).toInstant(), Instant.now())
                .toDays() < 30)) {
            return cache;
        }
        if (networkError != null) {
            throw new IOException("无法获取官方 Java 运行时索引，请检查网络连接", networkError);
        }
        throw new IOException("没有可用的 Java 运行时索引缓存");
    }

    private void createLinkSafely(Path root, RuntimeLink link) throws IOException {
        if (link.target() == null || link.target().isBlank()) {
            return;
        }
        Path linkPath = link.path();
        Path targetPath = linkPath.getParent().resolve(link.target()).normalize();
        if (!targetPath.startsWith(root.toAbsolutePath().normalize())) {
            Log.warn("忽略越界的 Java 运行时链接: " + linkPath);
            return;
        }
        Files.createDirectories(linkPath.getParent());
        try {
            if (Files.isSymbolicLink(linkPath)) {
                Files.deleteIfExists(linkPath);
            } else if (Files.exists(linkPath)) {
                return;
            }
            Files.createSymbolicLink(linkPath, Path.of(link.target()));
        } catch (IOException | UnsupportedOperationException ex) {

            if (Files.isRegularFile(targetPath)) {
                Files.copy(targetPath, linkPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Log.warn("无法创建 Java 运行时链接 " + linkPath.getFileName() + ": " + ex.getMessage());
            }
        }
    }

    public static String componentForMajor(int major) {
        if (major <= 8) {
            return "jre-legacy";
        }
        if (major <= 16) {
            return "java-runtime-alpha";
        }
        if (major <= 17) {
            return "java-runtime-gamma";
        }
        if (major <= 21) {
            return "java-runtime-delta";
        }
        return "java-runtime-epsilon";
    }

    public static String platformKey() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        boolean x86 = arch.contains("x86") || arch.contains("i386") || arch.contains("i686");
        if (os.contains("mac") || os.contains("darwin")) {
            return arm ? "mac-os-arm64" : "mac-os";
        }
        if (os.contains("win")) {
            if (arm) {
                return "windows-arm64";
            }
            return x86 ? "windows-x86" : "windows-x64";
        }
        if (os.contains("linux")) {
            if (arm) {
                throw new IOException("Mojang 官方没有为 Linux ARM 提供 Java 运行时");
            }
            return x86 ? "linux-i386" : "linux";
        }
        throw new IOException("当前操作系统不受支持: " + os);
    }

    private List<Path> systemCandidates() {
        List<Path> candidates = new ArrayList<>();
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            candidates.add(Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java"));
        }
        String envHome = System.getenv("JAVA_HOME");
        if (envHome != null && !envHome.isBlank()) {
            candidates.add(Path.of(envHome, "bin", isWindows() ? "java.exe" : "java"));
        }
        Path home = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            scanJavaHomes(Path.of("/Library/Java/JavaVirtualMachines"), candidates);
            scanJavaHomes(home.resolve("Library/Java/JavaVirtualMachines"), candidates);
            addIfExists(candidates, Path.of("/opt/homebrew/opt/openjdk/bin/java"));
            addIfExists(candidates, Path.of("/usr/local/opt/openjdk/bin/java"));
            scanVersioned(Path.of("/opt/homebrew/opt"), "openjdk", candidates);
            scanVersioned(Path.of("/usr/local/opt"), "openjdk", candidates);
        } else if (os.contains("win")) {
            addWindowsJavaHomes(candidates);
        } else {
            scanSimpleHomes(Path.of("/usr/lib/jvm"), candidates);
            scanSimpleHomes(home.resolve(".sdkman/candidates/java"), candidates);
            addIfExists(candidates, Path.of("/usr/bin/java"));
        }
        return candidates;
    }

    private void scanJavaHomes(Path root, List<Path> out) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(root)) {
            for (Path dir : stream) {
                Path java = dir.resolve("Contents/Home/bin/java");
                if (Files.isRegularFile(java)) {
                    out.add(java);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void scanSimpleHomes(Path root, List<Path> out) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(root)) {
            for (Path dir : stream) {
                addIfExists(out, dir.resolve("bin/java"));
            }
        } catch (IOException ignored) {
        }
    }

    private void scanVersioned(Path root, String prefix, List<Path> out) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(root, prefix + "*")) {
            for (Path dir : stream) {
                addIfExists(out, dir.resolve("bin/java"));
            }
        } catch (IOException ignored) {
        }
    }

    private void addWindowsJavaHomes(List<Path> out) {
        String programFiles = System.getenv("ProgramFiles");
        String localAppData = System.getenv("LOCALAPPDATA");
        if (programFiles != null) {
            scanWindowsVendor(Path.of(programFiles, "Java"), out);
            scanWindowsVendor(Path.of(programFiles, "Eclipse Adoptium"), out);
            scanWindowsVendor(Path.of(programFiles, "Microsoft"), out);
        }
        if (localAppData != null) {
            scanWindowsVendor(Path.of(localAppData, "Programs", "Eclipse Adoptium"), out);
        }
        addIfExists(out, Path.of("C:\\Program Files\\Java\\jdk-21\\bin\\java.exe"));
    }

    private void scanWindowsVendor(Path root, List<Path> out) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(root)) {
            for (Path dir : stream) {
                addIfExists(out, dir.resolve("bin/java.exe"));
                addIfExists(out, dir.resolve("Contents/Home/bin/java.exe"));
            }
        } catch (IOException ignored) {
        }
    }

    private static void addIfExists(List<Path> paths, Path java) {
        if (Files.isRegularFile(java)) {
            paths.add(java);
        }
    }

    private Path normalizeJavaPath(Path candidate) {
        if (candidate == null) {
            return null;
        }
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        if (Files.isDirectory(candidate)) {
            Path direct = candidate.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
            if (Files.isRegularFile(direct)) {
                return direct;
            }
            Path bundle = candidate.resolve("Contents/Home/bin/java");
            if (Files.isRegularFile(bundle)) {
                return bundle;
            }
        }
        return null;
    }

    public static Probe probe(Path java) {
        try {
            Process process = new ProcessBuilder(java.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Probe(-1, "未知", "");
            }
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readNBytes(8192), StandardCharsets.UTF_8);
            }
            String first = output.lines().findFirst().orElse("");
            Matcher matcher = VERSION_PATTERN.matcher(output);
            int major = -1;
            if (matcher.find()) {
                major = Integer.parseInt(matcher.group(1));
                if (major == 1 && matcher.group(2) != null) {
                    major = Integer.parseInt(matcher.group(2));
                }
            }
            String lower = output.toLowerCase(Locale.ROOT);
            String vendor;
            if (lower.contains("temurin") || lower.contains("adoptium")) {
                vendor = "Temurin";
            } else if (lower.contains("graalvm")) {
                vendor = "GraalVM";
            } else if (lower.contains("zulu")) {
                vendor = "Zulu";
            } else if (lower.contains("microsoft")) {
                vendor = "Microsoft";
            } else if (lower.contains("corretto")) {
                vendor = "Corretto";
            } else if (lower.contains("oracle")) {
                vendor = "Oracle";
            } else {
                vendor = "OpenJDK";
            }
            return new Probe(major, vendor, first.trim());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Probe(-1, "未知", "");
        }
    }

    private static Path runtimeJavaPath(Path root) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path mac = root.resolve("jre.bundle/Contents/Home/bin/java");
        Path unix = root.resolve("bin/java");
        Path windows = root.resolve("bin/java.exe");
        if (os.contains("mac") && Files.isRegularFile(mac)) {
            return mac;
        }
        if (Files.isRegularFile(windows)) {
            return windows;
        }
        if (Files.isRegularFile(unix)) {
            return unix;
        }
        return null;
    }

    private static void makeExecutable(Path file) {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(file));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record RuntimeFile(Path target, String url, String sha1, long size, boolean executable) {
    }

    private record RuntimeLink(Path path, String target) {
    }
}
