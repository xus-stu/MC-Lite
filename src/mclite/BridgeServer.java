package mclite;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Optional loopback JSON API used by the Python companion module. */
public final class BridgeServer {
    private static final int MAX_BODY = 1024 * 1024;
    private static final String API_VERSION = "1.0";
    private static final List<String> CAPABILITIES = List.of(
            "health", "info", "capabilities", "versions", "instances", "content.search",
            "content.project", "content.install", "tasks", "tasks.cancel", "tasks.clear",
            "config", "datapacks", "datapacks.install", "datapacks.sync",
            "logs", "launch", "java");
    private final Config config;
    private final String token;
    private final HttpServer server;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicLong jobIds = new AtomicLong();

    private BridgeServer(Config config, int port) throws IOException {
        this.config = config;
        this.token = newToken();
        this.server = HttpServer.create(new InetSocketAddress(
                InetAddress.getLoopbackAddress(), port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "mclite-bridge");
            thread.setDaemon(true);
            return thread;
        }));
        this.server.createContext("/api/", this::handle);
    }

    public static void startAndBlock(Config config, int port) throws IOException {
        BridgeServer bridge = new BridgeServer(config, port);
        int actualPort = bridge.server.getAddress().getPort();
        Path info = config.appHome().resolve("bridge.json");
        Files.createDirectories(config.appHome());
        Files.writeString(info, json(Map.of(
                "url", "http://127.0.0.1:" + actualPort,
                "port", actualPort,
                "token", bridge.token,
                "pid", ProcessHandle.current().pid())), StandardCharsets.UTF_8);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(info);
            } catch (IOException ignored) {
            }
            bridge.server.stop(0);
        }, "mclite-bridge-shutdown"));
        bridge.server.start();
        System.out.println("MCLITE_BRIDGE_URL=http://127.0.0.1:" + actualPort);
        System.out.println("MCLITE_BRIDGE_TOKEN=" + bridge.token);
        System.out.println("MCLITE_BRIDGE_FILE=" + info);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/v1/")) {
                path = "/api/" + path.substring("/api/v1/".length());
            }
            if ("/api/health".equals(path)) {
                send(exchange, 200, Map.of(
                        "ok", true,
                        "version", GameLauncher.LAUNCHER_VERSION,
                        "apiVersion", API_VERSION,
                        "apiPrefix", "/api/v1"));
                return;
            }
            String supplied = exchange.getRequestHeaders().getFirst("X-MCLite-Token");
            if (supplied == null || !constantTimeEquals(supplied, token)) {
                send(exchange, 401, Map.of("error", "invalid token"));
                return;
            }
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 204, null);
                return;
            }
            route(exchange, path);
        } catch (Exception ex) {
            Log.warn("Bridge 请求失败: " + ex.getMessage());
            send(exchange, 500, Map.of("error", ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange, String path) throws Exception {
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        String method = exchange.getRequestMethod().toUpperCase();
        if ("/api/info".equals(path)) {
            send(exchange, 200, Map.of(
                    "version", GameLauncher.LAUNCHER_VERSION,
                    "apiVersion", API_VERSION,
                    "apiPrefix", "/api/v1",
                    "capabilities", CAPABILITIES,
                    "gameDir", config.gameDir().toString(),
                    "username", config.username()));
            return;
        }
        if ("/api/capabilities".equals(path)) {
            send(exchange, 200, Map.of("apiVersion", API_VERSION,
                    "prefix", "/api/v1", "methods", CAPABILITIES));
            return;
        }
        if ("/api/versions/latest".equals(path)) {
            VersionManifest manifest = Versions.fetch(config, ProgressSink.NOOP,
                    new CancellationToken()).manifest();
            VersionManifest.VersionEntry latest = manifest.find(manifest.latestRelease());
            send(exchange, 200, latest == null ? Map.of("id", "") : versionEntry(latest,
                    installedIds().contains(latest.id())));
            return;
        }
        if ("/api/versions".equals(path) || "/api/instances".equals(path)) {
            send(exchange, 200, versions("1".equals(query.get("local"))
                    || "/api/instances".equals(path)));
            return;
        }
        if (path.startsWith("/api/versions/") && path.endsWith("/install") && "POST".equals(method)) {
            String id = path.substring("/api/versions/".length(), path.length() - "/install".length());
            startVersionInstall(id);
            send(exchange, 200, Map.of("accepted", true, "tasks", true));
            return;
        }
        if (path.startsWith("/api/instances/")) {
            send(exchange, 200, localVersion(path.substring("/api/instances/".length())));
            return;
        }
        if (path.startsWith("/api/versions/")) {
            send(exchange, 200, localVersion(path.substring("/api/versions/".length())));
            return;
        }
        if ("/api/content/sources".equals(path)) {
            send(exchange, 200, List.of(
                    Map.of("id", "modrinth", "available", true),
                    Map.of("id", "curseforge", "available",
                            !config.curseForgeApiKey().isBlank())));
            return;
        }
        if ("/api/content/search".equals(path)) {
            send(exchange, 200, search(query));
            return;
        }
        if (path.startsWith("/api/content/project/") && path.endsWith("/versions")) {
            String id = path.substring("/api/content/project/".length(), path.length() - "/versions".length());
            send(exchange, 200, projectVersions(id, query));
            return;
        }
        if (path.startsWith("/api/content/project/")) {
            String id = path.substring("/api/content/project/".length());
            Modrinth modrinth = new Modrinth(ProgressSink.NOOP, new CancellationToken());
            send(exchange, 200, project(modrinth.projectById(id)));
            return;
        }
        if ("/api/content/install".equals(path) && "POST".equals(method)) {
            startContentInstall(readJson(exchange));
            send(exchange, 200, Map.of("accepted", true));
            return;
        }
        if ("/api/tasks".equals(path)) {
            send(exchange, 200, tasks());
            return;
        }
        if ("/api/tasks/clear".equals(path) && "POST".equals(method)) {
            int removed = (int) jobs.values().stream()
                    .filter(job -> !"running".equals(job.state) && !"queued".equals(job.state))
                    .map(job -> job.id).filter(id -> jobs.remove(id) != null).count();
            send(exchange, 200, Map.of("removed", removed));
            return;
        }
        if (path.startsWith("/api/tasks/") && "GET".equals(method)) {
            String id = path.substring("/api/tasks/".length());
            Job job = jobs.get(id);
            if (job == null) {
                send(exchange, 404, Map.of("error", "task not found"));
            } else {
                send(exchange, 200, job.json());
            }
            return;
        }
        if (path.startsWith("/api/tasks/") && path.endsWith("/cancel") && "POST".equals(method)) {
            String id = path.substring("/api/tasks/".length(), path.length() - "/cancel".length());
            Job job = jobs.get(id);
            if (job == null) {
                send(exchange, 404, Map.of("error", "task not found"));
            } else {
                job.token.cancel();
                send(exchange, 200, Map.of("cancelled", true));
            }
            return;
        }
        if ("/api/config".equals(path)) {
            if ("PUT".equals(method)) {
                updateConfig(readJson(exchange));
            }
            send(exchange, 200, publicConfig());
            return;
        }
        if ("/api/datapacks".equals(path)) {
            send(exchange, 200, datapacks(query.get("instance")));
            return;
        }
        if ("/api/datapacks/sync".equals(path) && "POST".equals(method)) {
            Path gameDir = gameDir(query.get("instance"));
            send(exchange, 200, Map.of("worlds", GlobalDatapacks.sync(gameDir)));
            return;
        }
        if ("/api/datapacks/install".equals(path) && "POST".equals(method)) {
            send(exchange, 200, installDatapack(readJson(exchange)));
            return;
        }
        if ("/api/datapacks/remove".equals(path) && "POST".equals(method)) {
            send(exchange, 200, removeDatapack(readJson(exchange)));
            return;
        }
        if ("/api/logs".equals(path)) {
            send(exchange, 200, Map.of("text", tailLog(query.getOrDefault("tail", "200"))));
            return;
        }
        if ("/api/java".equals(path)) {
            send(exchange, 200, javaList());
            return;
        }
        if ("/api/launch".equals(path) && "POST".equals(method)) {
            send(exchange, 200, launch(readJson(exchange)));
            return;
        }
        send(exchange, 404, Map.of("error", "not found"));
    }

    private java.util.Set<String> installedIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (VersionManifest.VersionEntry entry : LocalVersions.scan(
                new GamePaths(config.gameDir()))) {
            ids.add(entry.id());
        }
        return ids;
    }

    private List<Object> javaList() {
        List<Object> result = new ArrayList<>();
        for (JavaManager.JavaInfo info : new JavaManager(config, ProgressSink.NOOP,
                new CancellationToken()).detectAll()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", info.path().toString());
            map.put("major", info.major());
            map.put("label", info.label());
            map.put("component", info.component());
            map.put("official", info.official());
            result.add(map);
        }
        return result;
    }

    private Map<String, Object> installDatapack(Map<String, Object> body) throws IOException {
        String instance = Json.str(body, "instance", "");
        Path archive = Path.of(Json.str(body, "path", ""));
        GlobalDatapacks.validate(archive);
        Path gameDir = gameDir(instance);
        Path folder = GlobalDatapacks.folder(gameDir);
        Files.createDirectories(folder);
        Path target = folder.resolve(archive.getFileName().toString());
        Files.copy(archive, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        int worlds = GlobalDatapacks.sync(gameDir);
        return Map.of("name", target.getFileName().toString(), "worlds", worlds,
                "instance", instance);
    }

    private Map<String, Object> removeDatapack(Map<String, Object> body) throws IOException {
        String instance = Json.str(body, "instance", "");
        String name = Json.str(body, "name", "");
        if (name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new IOException("数据包名称无效");
        }
        Path gameDir = gameDir(instance);
        Path target = GlobalDatapacks.folder(gameDir).resolve(name);
        boolean removed = Files.deleteIfExists(target);
        for (Path world : GlobalDatapacks.worlds(gameDir)) {
            Files.deleteIfExists(world.resolve("datapacks").resolve(name));
        }
        return Map.of("removed", removed, "name", name);
    }

    private List<Object> versions(boolean localOnly) throws IOException {
        List<Object> result = new ArrayList<>();
        java.util.Set<String> localIds = new java.util.HashSet<>();
        for (VersionManifest.VersionEntry entry : LocalVersions.scan(new GamePaths(config.gameDir()))) {
            localIds.add(entry.id());
            result.add(versionEntry(entry, true));
        }
        if (!localOnly) {
            VersionManifest manifest = Versions.fetch(config, ProgressSink.NOOP,
                    new CancellationToken()).manifest();
            for (VersionManifest.VersionEntry entry : manifest.versions()) {
                if (!localIds.contains(entry.id())) {
                    result.add(versionEntry(entry, localIds.contains(entry.id())));
                }
            }
        }
        return result;
    }

    private Map<String, Object> versionEntry(VersionManifest.VersionEntry entry, boolean local) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.id());
        map.put("type", entry.type());
        map.put("releaseTime", entry.releaseTime());
        map.put("local", local);
        return map;
    }

    private Map<String, Object> localVersion(String id) throws IOException {
        GamePaths paths = new GamePaths(config.gameDir());
        if (!Files.isRegularFile(paths.versionJson(id))) {
            return Map.of("id", id, "local", false);
        }
        VersionMeta meta = LocalVersions.load(paths, id);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", meta.id());
        map.put("local", true);
        map.put("mainClass", meta.mainClass());
        map.put("javaMajor", meta.javaMajor());
        map.put("jarId", meta.clientJarId());
        map.put("libraries", meta.libraries().size());
        map.put("gameArgs", meta.gameArgs());
        return map;
    }

    private List<Object> search(Map<String, String> query) throws IOException {
        String source = query.getOrDefault("source", "modrinth");
        if (!"modrinth".equals(source)) {
            throw new IOException("当前 Bridge 只支持 Modrinth 搜索");
        }
        Modrinth modrinth = new Modrinth(ProgressSink.NOOP, new CancellationToken());
        List<Modrinth.Project> projects = modrinth.search(
                query.getOrDefault("query", ""),
                query.getOrDefault("type", "mod"),
                query.getOrDefault("gameVersion", ""),
                blankToNull(query.get("loader")),
                Integer.parseInt(query.getOrDefault("offset", "0")),
                Math.min(50, Integer.parseInt(query.getOrDefault("limit", "20"))));
        List<Object> result = new ArrayList<>();
        for (Modrinth.Project project : projects) {
            result.add(project(project));
        }
        return result;
    }

    private List<Object> projectVersions(String id, Map<String, String> query) throws IOException {
        Modrinth modrinth = new Modrinth(ProgressSink.NOOP, new CancellationToken());
        List<Object> result = new ArrayList<>();
        for (Modrinth.Version version : modrinth.versions(id,
                query.getOrDefault("gameVersion", ""), blankToNull(query.get("loader")))) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", version.id());
            map.put("name", version.name());
            map.put("versionNumber", version.versionNumber());
            map.put("gameVersions", version.gameVersions());
            map.put("loaders", version.loaders());
            map.put("files", version.files().stream().map(file -> Map.of(
                    "filename", file.filename(), "size", file.size(), "url", file.url())).toList());
            result.add(map);
        }
        return result;
    }

    private Map<String, Object> project(Modrinth.Project project) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", project.id());
        map.put("slug", project.slug());
        map.put("title", project.title());
        map.put("description", project.description());
        map.put("author", project.author());
        map.put("downloads", project.downloads());
        map.put("iconUrl", project.iconUrl());
        map.put("projectType", project.projectType());
        map.put("source", project.source());
        return map;
    }

    private void startContentInstall(Map<String, Object> body) throws IOException {
        String id = Json.str(body, "projectId", "").trim();
        if (id.isEmpty()) {
            throw new IOException("projectId 不能为空");
        }
        String gameVersion = Json.str(body, "gameVersion", "").trim();
        String loader = Json.str(body, "loader", "").trim();
        if (gameVersion.isEmpty()) {
            throw new IOException("gameVersion 不能为空");
        }
        Modrinth.Project project = new Modrinth(ProgressSink.NOOP, new CancellationToken())
                .projectById(id);
        Job job = new Job("安装 " + project.title(), new CancellationToken());
        new Thread(() -> {
            try {
                job.running();
                job.result = new ContentInstaller(config, job.progress, job.token)
                        .install(project, gameVersion, loader.isBlank() ? null : loader);
                job.done("安装完成：" + job.result.summary());
            } catch (Exception ex) {
                job.failed(ex);
            }
        }, "mclite-bridge-install").start();
    }

    private void startVersionInstall(String id) throws IOException {
        VersionManifest manifest = Versions.fetch(config, ProgressSink.NOOP,
                new CancellationToken()).manifest();
        VersionManifest.VersionEntry entry = manifest.find(id);
        if (entry == null) {
            throw new IOException("找不到版本 " + id);
        }
        Job job = new Job("安装 " + id, new CancellationToken());
        Thread thread = new Thread(() -> {
            try {
                job.running();
                new Installer(config, job.token, job.progress)
                        .install(entry, config.downloadAssets());
                job.done("安装完成：" + id);
            } catch (Exception ex) {
                job.failed(ex);
            }
        }, "mclite-bridge-version-install");
        thread.start();
    }

    private Map<String, Object> launch(Map<String, Object> body) throws IOException {
        String id = Json.str(body, "versionId", "").trim();
        GamePaths paths = new GamePaths(config.gameDir());
        VersionMeta meta = LocalVersions.load(paths, id);
        List<JavaManager.JavaInfo> javas = new JavaManager(config, ProgressSink.NOOP,
                new CancellationToken()).detectAll();
        JavaManager.JavaInfo java = javas.stream()
                .filter(info -> info.major() >= meta.javaMajor())
                .max(Comparator.comparingInt(JavaManager.JavaInfo::major))
                .orElseThrow(() -> new IOException("没有满足 Java " + meta.javaMajor() + " 的运行时"));
        VersionManifest.VersionEntry entry = new VersionManifest.VersionEntry(
                id, "local", null, null, "", 0, true);
        GameLauncher.LaunchResult launched = GameLauncher.launch(config, meta, entry, java);
        return Map.of("pid", launched.process().pid(), "log", launched.logFile().toString());
    }

    private Map<String, Object> datapacks(String instance) throws IOException {
        Path gameDir = gameDir(instance);
        List<Object> names = new ArrayList<>();
        Path folder = GlobalDatapacks.folder(gameDir);
        if (Files.isDirectory(folder)) {
            try (var files = Files.list(folder)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".zip"))
                        .forEach(path -> names.add(path.getFileName().toString()));
            }
        }
        return Map.of("instance", instance == null ? "" : instance,
                "packs", names, "worlds", GlobalDatapacks.worlds(gameDir).size());
    }

    private Map<String, Object> publicConfig() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gameDir", config.gameDir().toString());
        map.put("username", config.username());
        map.put("memoryMb", config.memoryMb());
        map.put("autoMemory", config.autoMemory());
        map.put("autoJvmTuning", config.autoJvmTuning());
        map.put("isolateVersions", config.isolateVersions());
        map.put("jvmArgs", config.jvmArgs());
        map.put("windowWidth", config.windowWidth());
        map.put("windowHeight", config.windowHeight());
        map.put("downloadAssets", config.downloadAssets());
        map.put("forceModTranslation", config.forceModTranslation());
        map.put("autoUpdate", config.autoUpdate());
        return map;
    }

    private void updateConfig(Map<String, Object> body) {
        if (Json.has(body, "memoryMb")) {
            config.memoryMb(Json.integer(body, "memoryMb", config.memoryMb()));
        }
        if (Json.has(body, "autoMemory")) {
            config.autoMemory(Json.bool(body, "autoMemory", config.autoMemory()));
        }
        if (Json.has(body, "jvmArgs")) {
            config.jvmArgs(Json.str(body, "jvmArgs", ""));
        }
        if (Json.has(body, "autoJvmTuning")) {
            config.autoJvmTuning(Json.bool(body, "autoJvmTuning", config.autoJvmTuning()));
        }
        if (Json.has(body, "isolateVersions")) {
            config.isolateVersions(Json.bool(body, "isolateVersions", config.isolateVersions()));
        }
        if (Json.has(body, "downloadAssets")) {
            config.downloadAssets(Json.bool(body, "downloadAssets", config.downloadAssets()));
        }
        if (Json.has(body, "forceModTranslation")) {
            config.forceModTranslation(Json.bool(body, "forceModTranslation",
                    config.forceModTranslation()));
        }
        if (Json.has(body, "autoUpdate")) {
            config.autoUpdate(Json.bool(body, "autoUpdate", config.autoUpdate()));
        }
        if (Json.has(body, "windowWidth")) {
            config.windowWidth(Json.integer(body, "windowWidth", config.windowWidth()));
        }
        if (Json.has(body, "windowHeight")) {
            config.windowHeight(Json.integer(body, "windowHeight", config.windowHeight()));
        }
        if (Json.has(body, "username")) {
            String username = Json.str(body, "username", "").trim();
            if (Config.isValidUsername(username)) {
                config.username(username);
            }
        }
        config.save();
    }

    private List<Object> tasks() {
        List<Object> result = new ArrayList<>();
        for (Job job : jobs.values()) {
            result.add(job.json());
        }
        return result;
    }

    private String tailLog(String tail) throws IOException {
        Path file = Log.file();
        if (file == null || !Files.isRegularFile(file)) {
            return "";
        }
        int count = Math.max(1, Math.min(2000, Integer.parseInt(tail)));
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int from = Math.max(0, lines.size() - count);
        return String.join("\n", lines.subList(from, lines.size()));
    }

    private Path gameDir(String instance) {
        return instance == null || instance.isBlank()
                ? config.gameDir() : config.effectiveGameDir(instance);
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = json(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) {
            throw new IOException("请求体过大");
        }
        if (bytes.length == 0) {
            return Map.of();
        }
        return Json.obj(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split("&")) {
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            String value = equals < 0 ? "" : part.substring(equals + 1);
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String json(Object value) {
        StringBuilder out = new StringBuilder();
        appendJson(out, value);
        return out.toString();
    }

    private static void appendJson(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            out.append('"');
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                appendJson(out, String.valueOf(entry.getKey()));
                out.append(':');
                appendJson(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                appendJson(out, item);
            }
            out.append(']');
        } else {
            appendJson(out, value.toString());
        }
    }

    private final class Job {
        private final String id = "job-" + jobIds.incrementAndGet();
        private final String title;
        private final TaskProgress progress = new TaskProgress();
        private final CancellationToken token;
        private volatile String state = "queued";
        private volatile String error;
        private volatile ContentInstaller.Result result;

        private Job(String title, CancellationToken token) {
            this.title = title;
            this.token = token;
            jobs.put(id, this);
        }

        private void running() {
            state = "running";
        }

        private void done(String message) {
            state = "done";
            progress.stage(message);
        }

        private void failed(Exception ex) {
            state = "error";
            error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            progress.stage(error);
        }

        private Map<String, Object> json() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("title", title);
            map.put("state", state);
            map.put("stage", progress.stage());
            map.put("done", progress.done());
            map.put("total", progress.total());
            map.put("percent", progress.percent());
            map.put("speed", progress.bytesPerSecond());
            if (error != null) {
                map.put("error", error);
            }
            if (result != null) {
                map.put("summary", result.summary());
            }
            return map;
        }
    }
}
