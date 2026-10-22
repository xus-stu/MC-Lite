package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameLauncher {
    public static final String LAUNCHER_NAME = "MCLite";
    public static final String LAUNCHER_VERSION = "1.0.1";

    private GameLauncher() {
    }

    public record LaunchResult(Process process, Path logFile, List<String> command) {
    }

    public static LaunchResult launch(Config config, VersionMeta meta,
                                      VersionManifest.VersionEntry entry,
                                      JavaManager.JavaInfo java) throws IOException {
        if (!Config.isValidUsername(config.username())) {
            throw new IOException("用户名必须为 3-16 位字母、数字或下划线");
        }
        Path javaPath = java.path();
        if (javaPath == null || !Files.isRegularFile(javaPath)) {
            throw new IOException("找不到所选的 Java 可执行文件");
        }
        if (java.major() > 0 && java.major() < meta.javaMajor()) {
            throw new IOException("此版本需要 Java " + meta.javaMajor()
                    + "，当前选择的是 Java " + java.major());
        }
        GamePaths paths = new GamePaths(config.gameDir());
        Path instanceDir = config.effectiveGameDir(meta.id());
        Files.createDirectories(paths.gameDir());
        Files.createDirectories(instanceDir);
        Files.createDirectories(paths.assetsDir());
        prepareNatives(meta, paths);
        List<String> command = buildCommand(config, meta, entry, javaPath, true);

        Files.createDirectories(paths.gameLogsDir());
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now());
        Path logFile = paths.gameLogsDir().resolve("mclite-" + meta.id() + "-" + stamp + ".log");

        StringBuilder header = new StringBuilder();
        header.append("MC Lite ").append(LAUNCHER_VERSION).append('\n');
        header.append("Version: ").append(meta.id()).append('\n');
        header.append("Java: ").append(javaPath).append(" (Java ").append(java.major()).append(")\n");
        header.append("Game directory: ").append(instanceDir).append('\n');
        header.append("Command:\n");
        for (String part : command) {
            header.append("  ").append(quoteForLog(part)).append('\n');
        }
        header.append("----- Minecraft output -----\n");
        Files.writeString(logFile, header.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(instanceDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        Process process = builder.start();
        Log.info("已启动 Minecraft " + meta.id() + "，PID=" + process.pid() + "，日志=" + logFile);
        return new LaunchResult(process, logFile, List.copyOf(command));
    }

    private static void prepareNatives(VersionMeta meta, GamePaths paths) throws IOException {
        Rules.Env env = Rules.Env.current();
        Path nativesDir = paths.nativesDir(meta.id());
        Files.createDirectories(nativesDir);

        for (String child : List.of("java", "jna", "lwjgl", "netty")) {
            Files.createDirectories(nativesDir.resolve(child));
        }
        for (VersionMeta.Library library : meta.libraries()) {
            if (!library.applies(env)) {
                continue;
            }
            VersionMeta.Download nativeDownload = library.nativeFor(env);
            if (nativeDownload == null || nativeDownload.path() == null) {
                continue;
            }
            Path jar = GamePaths.resolveUnder(paths.librariesDir(), nativeDownload.path());
            if (Files.isRegularFile(jar)) {
                Installer.extractNatives(jar, nativesDir, library.extractExclude());
            }
        }
    }

    public static List<String> buildCommand(Config config, VersionMeta meta,
                                            VersionManifest.VersionEntry entry,
                                            Path javaPath, boolean verifyFiles) throws IOException {
        if (!Config.isValidUsername(config.username())) {
            throw new IOException("用户名必须为 3-16 位字母、数字或下划线");
        }
        GamePaths paths = new GamePaths(config.gameDir());
        Rules.Env env = Rules.Env.current();
        String classpath = buildClasspath(meta, entry, paths, env, verifyFiles);
        String offlineUuid = offlineUuid(config.username()).toString().replace("-", "");
        String gameAssets = gameAssets(meta, paths);
        Path loggingFile = meta.loggingFile() == null ? null : paths.logConfig(meta.loggingFile().path());

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("auth_player_name", config.username());
        placeholders.put("version_name", meta.id());
        placeholders.put("game_directory", config.effectiveGameDir(meta.id()).toString());
        placeholders.put("assets_root", paths.assetsDir().toString());
        placeholders.put("game_assets", gameAssets);
        placeholders.put("assets_index_name", meta.assetsId() == null ? "legacy" : meta.assetsId());
        placeholders.put("auth_uuid", offlineUuid);
        placeholders.put("auth_access_token", "0");
        placeholders.put("auth_session", "token:0:" + offlineUuid);
        placeholders.put("clientid", "");
        placeholders.put("auth_xuid", "");
        placeholders.put("user_type", "legacy");
        placeholders.put("user_properties", "{}");
        placeholders.put("version_type", entry.type());
        placeholders.put("natives_directory", paths.nativesDir(meta.id()).toString());
        placeholders.put("library_directory", paths.librariesDir().toString());
        placeholders.put("classpath", classpath);
        placeholders.put("classpath_separator", java.io.File.pathSeparator);
        placeholders.put("launcher_name", LAUNCHER_NAME);
        placeholders.put("launcher_version", LAUNCHER_VERSION);
        placeholders.put("resolution_width", Integer.toString(config.windowWidth()));
        placeholders.put("resolution_height", Integer.toString(config.windowHeight()));
        placeholders.put("quickPlayPath", "");
        placeholders.put("quickPlaySingleplayer", "");
        placeholders.put("quickPlayMultiplayer", "");
        placeholders.put("quickPlayRealms", "");

        List<String> command = new ArrayList<>();
        command.add(javaPath.toString());
        command.add("-Xms512M");
        command.add("-Xmx" + config.effectiveMemoryMb() + "M");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");
        command.add("-Dlog4j2.formatMsgNoLookups=true");
        command.add("-Dminecraft.launcher.brand=" + LAUNCHER_NAME);
        command.add("-Dminecraft.launcher.version=" + LAUNCHER_VERSION);
        for (String custom : Rules.splitLegacy(config.jvmArgs())) {
            if (!custom.isBlank()) {
                command.add(custom);
            }
        }

        List<String> metadataJvm = new ArrayList<>();
        for (VersionMeta.Arg arg : meta.jvmArgs()) {
            for (String value : arg.collect(env)) {
                metadataJvm.add(substitute(value, placeholders));
            }
        }
        if (config.autoJvmTuning() && !hasMetadataJvmTuning(metadataJvm)) {
            JvmTuner.Tuning tuning = JvmTuner.tune(javaPath, meta.javaMajor(),
                    config.effectiveMemoryMb());
            if (tuning.applied()) {
                command.addAll(tuning.args());
                Log.info("JVM 自动优化: " + tuning.reason());
            } else {
                Log.info("JVM 自动优化跳过: " + tuning.reason());
            }
        } else if (config.autoJvmTuning()) {
            Log.info("版本自带 JVM 参数，跳过自动优化");
        }
        command.addAll(metadataJvm);

        if (!hasJvmOption(metadataJvm, "-Djava.library.path")) {
            command.add("-Djava.library.path=" + paths.nativesDir(meta.id()));
        }
        if (!hasJvmOption(metadataJvm, "-cp") && !hasJvmOption(metadataJvm, "-classpath")) {
            command.add("-cp");
            command.add(classpath);
        }
        if (meta.loggingArgument() != null && loggingFile != null && Files.isRegularFile(loggingFile)) {
            String loggingArg = meta.loggingArgument().replace("${path}", loggingFile.toString());
            if (!hasJvmOption(metadataJvm, "-Dlog4j.configurationFile")) {
                command.add(loggingArg);
            }
        }

        command.add(meta.mainClass());

        List<String> gameArgs = new ArrayList<>();
        if (!meta.gameArgs().isEmpty()) {
            for (VersionMeta.Arg arg : meta.gameArgs()) {
                for (String value : arg.collect(env)) {
                    gameArgs.add(substitute(value, placeholders));
                }
            }
        } else if (meta.legacyArguments() != null && !meta.legacyArguments().isBlank()) {
            for (String value : Rules.splitLegacy(meta.legacyArguments())) {
                gameArgs.add(substitute(value, placeholders));
            }
        } else {
            gameArgs.addAll(minimalGameArgs(placeholders));
        }

        if (!gameArgs.contains("--width") && !gameArgs.contains("--height")
                && !meta.gameArgs().isEmpty()) {
            gameArgs.add("--width");
            gameArgs.add(Integer.toString(config.windowWidth()));
            gameArgs.add("--height");
            gameArgs.add(Integer.toString(config.windowHeight()));
        }
        command.addAll(gameArgs);
        return command;
    }

    private static List<String> minimalGameArgs(Map<String, String> values) {
        List<String> args = new ArrayList<>();
        args.add("--username");
        args.add(values.get("auth_player_name"));
        args.add("--version");
        args.add(values.get("version_name"));
        args.add("--gameDir");
        args.add(values.get("game_directory"));
        args.add("--assetsDir");
        args.add(values.get("assets_root"));
        args.add("--assetIndex");
        args.add(values.get("assets_index_name"));
        args.add("--uuid");
        args.add(values.get("auth_uuid"));
        args.add("--accessToken");
        args.add(values.get("auth_access_token"));
        args.add("--userType");
        args.add(values.get("user_type"));
        return args;
    }

    private static String buildClasspath(VersionMeta meta, VersionManifest.VersionEntry entry,
                                         GamePaths paths, Rules.Env env, boolean verifyFiles)
            throws IOException {
        List<String> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int missing = 0;
        for (VersionMeta.Library library : meta.libraries()) {
            if (!library.applies(env)) {
                continue;
            }
            VersionMeta.Download artifact = library.artifact();
            if (artifact != null && artifact.path() != null) {
                Path path = GamePaths.resolveUnder(paths.librariesDir(), artifact.path());
                if (verifyFiles && !Files.isRegularFile(path)) {
                    missing++;
                }
                if (seen.add(path.toString())) {
                    entries.add(path.toString());
                }
            }
            VersionMeta.Download nativeDownload = library.nativeFor(env);
            if (nativeDownload != null && nativeDownload.path() != null && verifyFiles) {
                Path path = GamePaths.resolveUnder(paths.librariesDir(), nativeDownload.path());
                if (!Files.isRegularFile(path)) {
                    missing++;
                }
            }
        }
        Path client = paths.clientJar(meta.clientJarId());
        if (verifyFiles && !Files.isRegularFile(client)) {
            missing++;
        }
        entries.add(client.toString());

        if (meta.assetIndex() != null && verifyFiles
                && !Files.isRegularFile(paths.assetIndex(meta.assetsId()))) {
            missing++;
        }
        if (verifyFiles && !Files.isRegularFile(paths.versionJson(meta.id()))) {
            missing++;
        }
        if (missing > 0) {
            throw new MissingFilesException(missing);
        }
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static String gameAssets(VersionMeta meta, GamePaths paths) {
        if (meta.assetIndex() == null) {
            return paths.assetsDir().toString();
        }
        Path index = paths.assetIndex(meta.assetsId());
        if (Files.isRegularFile(index)) {
            try {
                Map<String, Object> json = Json.obj(Json.parse(Files.readString(index, StandardCharsets.UTF_8)));
                if (Json.bool(json, "map_to_resources", false)) {
                    return paths.resourcesDir().toString();
                }
                if (Json.bool(json, "virtual", false)) {
                    return paths.virtualAssetsDir(meta.assetsId()).toString();
                }
            } catch (IOException | RuntimeException ex) {
                Log.warn("读取资源索引失败: " + ex.getMessage());
            }
        }
        return paths.assetsDir().toString();
    }

    private static boolean hasMetadataJvmTuning(List<String> args) {
        for (String arg : args) {
            if (arg.startsWith("-Xmx") || arg.startsWith("-Xms")
                    || arg.startsWith("-Xmn") || arg.startsWith("-Xss")
                    || arg.startsWith("-XX:")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasJvmOption(List<String> args, String prefix) {
        for (String arg : args) {
            if (arg.equals(prefix) || arg.startsWith(prefix + "=")) {
                return true;
            }
        }
        return false;
    }

    private static String substitute(String value, Map<String, String> placeholders) {
        String result = value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private static String quoteForLog(String value) {
        if (value == null) {
            return "\"\"";
        }
        if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    public static final class MissingFilesException extends IOException {
        private final int missing;

        public MissingFilesException(int missing) {
            super("缺少 " + missing + " 个游戏文件，请先点击「下载 / 修复」");
            this.missing = missing;
        }

        public int missing() {
            return missing;
        }
    }
}
