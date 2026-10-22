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

public final class CurseForgeInstaller {
    private final Config config;
    private final GamePaths paths;
    private final ProgressSink progress;
    private final CancellationToken token;
    private final Net net;
    private final CurseForge curseForge;

    public CurseForgeInstaller(Config config, ProgressSink progress, CancellationToken token) {
        this.config = config;
        this.paths = new GamePaths(config.gameDir());
        this.progress = progress;
        this.token = token;
        this.net = new Net(progress, token);
        this.curseForge = new CurseForge(config.curseForgeApiKey(), progress, token);
    }

    public ContentInstaller.Result install(Modrinth.Project project, String gameVersion,
                                           String loader) throws IOException {
        if ("modpack".equals(project.projectType())) {
            return new CurseForgeModpackInstaller(config, progress, token)
                    .install(project, versionsForPack(project, gameVersion), gameVersion);
        }
        List<Modrinth.Version> versions = curseForge.versions(project.id(), gameVersion,
                "resourcepack".equals(project.projectType()) ? null : loader);
        if (versions.isEmpty()) {
            throw new IOException("没有找到兼容版本");
        }
        return installVersion(project, versions.get(0), gameVersion, loader);
    }

    public ContentInstaller.Result installVersion(Modrinth.Project project,
                                                  Modrinth.Version version,
                                                  String gameVersion, String loader)
            throws IOException {
        if ("modpack".equals(project.projectType())) {
            return new CurseForgeModpackInstaller(config, progress, token)
                    .install(project, version, gameVersion);
        }
        Properties index = readIndex();
        installFile(project, version, index, new HashSet<>(), 0,
                "resourcepack".equals(project.projectType()),
                "shader".equals(project.projectType()), gameVersion, loader);
        writeIndex(index);
        return new ContentInstaller.Result(config.gameDir(), version.id(),
                project.title() + " " + version.versionNumber());
    }

    private Modrinth.Version versionsForPack(Modrinth.Project project, String gameVersion)
            throws IOException {
        List<Modrinth.Version> versions = curseForge.versions(project.id(), gameVersion, null);
        if (versions.isEmpty()) {
            versions = curseForge.versions(project.id(), null, null);
        }
        if (versions.isEmpty()) {
            throw new IOException("CurseForge 整合包没有可用文件");
        }
        return versions.get(0);
    }

    private void installFile(Modrinth.Project project, Modrinth.Version version,
                             Properties index, Set<String> seen, int depth, boolean resourcePack,
                             boolean shader, String gameVersion, String loader) throws IOException {
        token.check();
        if (depth > 8 || !seen.add(project.id())) {
            return;
        }
        Modrinth.File file = version.primaryFile();
        if (file == null) {
            throw new IOException("CurseForge 文件不允许 API 下载：" + project.title());
        }
        Path dir = shader ? config.gameDir().resolve("shaderpacks")
                : resourcePack ? config.gameDir().resolve("resourcepacks")
                : config.gameDir().resolve("mods");
        Files.createDirectories(dir);
        Path target = GamePaths.resolveUnder(dir, file.filename());
        boolean exists = Files.isRegularFile(target) && file.sha1() != null
                && Net.sha1Matches(target, file.sha1());
        if (!exists) {
            progress.stage("下载 " + file.filename());
            if (file.size() > 0) {
                progress.addTotal(file.size());
            }
            net.download(file.url(), target, file.sha1(), file.size(), file.filename(), true);
        }
        index.setProperty("project." + project.id(), file.filename());
        for (Modrinth.Dependency dependency : version.dependencies()) {
            if (!dependency.required() || dependency.projectId() == null) {
                continue;
            }
            if (index.getProperty("project." + dependency.projectId()) != null) {
                continue;
            }
            List<Modrinth.Version> options = curseForge.versions(dependency.projectId(),
                    gameVersion, loader);
            if (options.isEmpty()) {
                throw new IOException("找不到前置模组 " + dependency.projectId());
            }
            Modrinth.Project depProject = new Modrinth.Project(dependency.projectId(),
                    dependency.projectId(), dependency.fileName() == null
                    ? "前置 " + dependency.projectId() : dependency.fileName(),
                    "", "", 0, null, "mod", null, null, "curseforge");
            progress.stage("解析前置 · " + dependency.projectId());
            installFile(depProject, options.get(0), index, seen, depth + 1, false, false,
                    gameVersion, loader);
        }
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
        Path file = paths.gameDir().resolve(".mclite/content.properties");
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "MC Lite installed content");
        }
    }
}
