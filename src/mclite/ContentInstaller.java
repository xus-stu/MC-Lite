package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class ContentInstaller {
    private static final Object INDEX_LOCK = new Object();
    private static final java.util.concurrent.ConcurrentHashMap<Path, Object> DOWNLOAD_LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final Config config;
    private final GamePaths paths;
    private final Modrinth modrinth;
    private final ProgressSink progress;
    private final CancellationToken token;
    private final Net net;

    public ContentInstaller(Config config, ProgressSink progress, CancellationToken token) {
        this.config = config;
        this.paths = new GamePaths(config.gameDir());
        this.progress = progress;
        this.token = token;
        this.net = new Net(progress, token);
        this.modrinth = new Modrinth(progress, token);
    }

    public record Result(Path gameDir, String versionId, String summary) {
    }

    public Result install(Modrinth.Project project, String gameVersion, String loader)
            throws IOException {
        if (project.curseForge()) {
            return new CurseForgeInstaller(config, progress, token)
                    .install(project, gameVersion, loader);
        }
        if ("modpack".equals(project.projectType())) {
            RegionGate.requireGameDownloadAllowed();
            return new ModpackInstaller(config, progress, token, modrinth).install(project, gameVersion);
        }
        boolean pack = "resourcepack".equals(project.projectType())
                || "shader".equals(project.projectType());
        List<Modrinth.Version> versions = modrinth.versions(project.id(), gameVersion,
                pack ? null : loader);
        if (versions.isEmpty()) {
            throw new IOException("没有找到兼容 " + gameVersion
                    + (loader == null ? "" : " / " + loader) + " 的版本");
        }
        Modrinth.Version chosen = versions.get(0);
        return installVersion(project, chosen, gameVersion, loader);
    }

    public Result installVersion(Modrinth.Project project, Modrinth.Version chosen,
                                 String gameVersion, String loader) throws IOException {
        if (project.curseForge()) {
            return new CurseForgeInstaller(config, progress, token)
                    .installVersion(project, chosen, gameVersion, loader);
        }
        if ("modpack".equals(project.projectType())) {
            RegionGate.requireGameDownloadAllowed();
            return new ModpackInstaller(config, progress, token, modrinth)
                    .install(project, chosen);
        }
        Properties index = readIndex();
        installFileVersion(project.id(), chosen, index, new HashSet<>(), 0,
                "resourcepack".equals(project.projectType()),
                "shader".equals(project.projectType()));
        writeIndex(index);
        return new Result(config.gameDir(), chosen.id(),
                project.title() + " " + chosen.versionNumber());
    }

    private void installFileVersion(String projectId, Modrinth.Version version, Properties index,
                                    Set<String> seen, int depth, boolean resourcePack,
                                    boolean shader) throws IOException {
        token.check();
        if (depth > 8) {
            throw new IOException("前置模组依赖层级过深，已停止");
        }
        if (!seen.add(projectId)) {
            return;
        }
        Modrinth.File file = version.primaryFile();
        if (file == null) {
            throw new IOException("该版本没有可下载文件");
        }
        Path dir = shader ? paths.gameDir().resolve("shaderpacks")
                : resourcePack ? paths.gameDir().resolve("resourcepacks")
                : paths.gameDir().resolve("mods");
        Files.createDirectories(dir);
        Path target = GamePaths.resolveUnder(dir, file.filename());
        String previous = index.getProperty("project." + projectId);
        boolean exists = Files.isRegularFile(target) && file.sha1() != null
                && Net.sha1Matches(target, file.sha1());
        if ((previous != null && previous.equals(file.filename()) && exists)
                || (previous == null && exists)) {
            progress.stage("已存在 · " + file.filename());
            index.setProperty("project." + projectId, file.filename());
            index.setProperty("file." + file.filename(), file.sha1() == null ? "" : file.sha1());
        } else {
            Path key = target.toAbsolutePath().normalize();
            Object lock = DOWNLOAD_LOCKS.computeIfAbsent(key, value -> new Object());
            synchronized (lock) {
                boolean downloaded = Files.isRegularFile(target) && file.sha1() != null
                        && Net.sha1Matches(target, file.sha1());
                if (!downloaded) {
                    progress.stage("下载 " + file.filename());
                    if (file.size() > 0) {
                        progress.addTotal(file.size());
                    }
                    // Modrinth 的 Fastly 节点用 4 段连接反而容易超时；
                    // 这里强制单连接，靠 DNS 选择快节点和 6 路并行模组提速。
                    net.download(file.url(), target, file.sha1(), file.size(),
                            file.filename(), true, Long.MAX_VALUE);
                }
            }
            if (previous != null && !previous.equals(file.filename())) {
                Path old = GamePaths.resolveUnder(target.getParent(), previous);
                if (Files.isRegularFile(old) && !old.equals(target)) {
                    try {
                        Files.deleteIfExists(old);
                        Log.info("移除旧版本模组 " + previous + "（已替换为 "
                                + file.filename() + "）");
                    } catch (IOException ex) {
                        Log.warn("移除旧版本模组失败 " + previous + ": " + ex.getMessage());
                    }
                }
            }
            index.setProperty("project." + projectId, file.filename());
            index.setProperty("file." + file.filename(), file.sha1() == null ? "" : file.sha1());
        }

        for (Modrinth.Dependency dependency : version.dependencies()) {
            token.check();
            if (!dependency.required() || dependency.projectId() == null) {
                continue;
            }
            String installed = index.getProperty("project." + dependency.projectId());
            if (installed != null) {
                Path existing = GamePaths.resolveUnder(dir, installed);
                if (Files.isRegularFile(existing)) {
                    continue;
                }
            }
            Modrinth.Version depVersion = dependency.versionId() != null
                    ? modrinth.version(dependency.versionId())
                    : firstCompatible(dependency.projectId(), version);
            if (depVersion == null) {
                throw new IOException("找不到前置模组 " + dependency.projectId() + " 的兼容版本");
            }
            progress.stage("解析前置 · " + depVersion.name());
            installFileVersion(dependency.projectId(), depVersion, index, seen, depth + 1,
                    resourcePack, shader);
        }
    }

    private Modrinth.Version firstCompatible(String projectId, Modrinth.Version parent)
            throws IOException {
        String gameVersion = parent.gameVersions().isEmpty() ? null : parent.gameVersions().get(0);
        String loader = parent.loaders().isEmpty() ? null : parent.loaders().get(0);
        List<Modrinth.Version> versions = modrinth.versions(projectId, gameVersion, loader);
        return versions.isEmpty() ? null : versions.get(0);
    }

    private Properties readIndex() {
        Properties properties = new Properties();
        Path file = paths.gameDir().resolve(".mclite/content.properties");
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException ex) {
                Log.warn("读取模组索引失败: " + ex.getMessage());
            }
        }
        return properties;
    }

    private void writeIndex(Properties properties) throws IOException {
        synchronized (INDEX_LOCK) {
            Path file = paths.gameDir().resolve(".mclite/content.properties");
            Files.createDirectories(file.getParent());
            Properties merged = readIndex();
            merged.putAll(properties);
            try (OutputStream out = Files.newOutputStream(file)) {
                merged.store(out, "MC Lite installed content");
            }
        }
    }
}
