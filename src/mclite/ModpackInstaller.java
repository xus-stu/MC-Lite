package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class ModpackInstaller {
    private static final int THREADS = 6;

    private final Config config;
    private final ProgressSink progress;
    private final CancellationToken token;
    private final Modrinth modrinth;
    private final Net net;

    public ModpackInstaller(Config config, ProgressSink progress, CancellationToken token,
                            Modrinth modrinth) {
        this.config = config;
        this.progress = progress;
        this.token = token;
        this.modrinth = modrinth;
        this.net = new Net(progress, token);
    }

    public ContentInstaller.Result install(Modrinth.Project project, String gameVersion)
            throws IOException {
        RegionGate.requireGameDownloadAllowed();
        List<Modrinth.Version> versions = modrinth.versions(project.id(), gameVersion, null);
        if (versions.isEmpty()) {
            versions = modrinth.versions(project.id(), null, null);
        }
        if (versions.isEmpty()) {
            throw new IOException("整合包没有可下载版本");
        }
        Modrinth.Version packVersion = versions.get(0);
        return install(project, packVersion);
    }

    public ContentInstaller.Result install(Modrinth.Project project,
                                           Modrinth.Version packVersion) throws IOException {
        RegionGate.requireGameDownloadAllowed();
        Modrinth.File packFile = packVersion.primaryFile();
        if (packFile == null) {
            throw new IOException("整合包版本没有文件");
        }
        Path cache = config.cacheDir().resolve("packs");
        Files.createDirectories(cache);
        Path mrpack = cache.resolve(packFile.filename());
        progress.stage("下载整合包 " + project.title() + "…");
        if (packFile.size() > 0) {
            progress.addTotal(packFile.size());
        }
        net.download(packFile.url(), mrpack, packFile.sha1(), packFile.size(),
                packFile.filename(), true);
        String slug = project.slug() == null || !project.slug().matches("[A-Za-z0-9._-]{1,60}")
                ? project.id().substring(0, Math.min(8, project.id().length())) : project.slug();
        return installArchive(mrpack, slug, project.title(), project.id(), packVersion.id());
    }

    public ContentInstaller.Result installArchive(Path mrpack, String displayName)
            throws IOException {
        RegionGate.requireGameDownloadAllowed();
        String slug = displayName == null ? "local-pack"
                : displayName.replaceAll("[^A-Za-z0-9._-]", "-");
        if (slug.isBlank()) {
            slug = "local-pack";
        }
        if (slug.length() > 60) {
            slug = slug.substring(0, 60);
        }
        return installArchive(mrpack, slug, displayName == null ? "本地整合包" : displayName,
                "local", "local");
    }

    private ContentInstaller.Result installArchive(Path mrpack, String slug, String title,
                                                    String projectId, String versionId)
            throws IOException {
        PackIndex index = readIndex(mrpack);
        Path instances = config.modpackInstancesDir();
        Path instance = instances.resolve(slug);
        if (Files.exists(instance)) {
            instance = instances.resolve(slug + "-" + System.currentTimeMillis() % 100000);
        }
        Files.createDirectories(instance);

        String minecraft = index.dependencies.get("minecraft");
        if (minecraft == null) {
            throw new IOException("整合包没有声明 Minecraft 版本");
        }
        Versions.FetchResult fetched = Versions.fetch(config, progress, token);
        VersionManifest.VersionEntry vanilla = fetched.manifest().find(minecraft);
        if (vanilla == null) {
            throw new IOException("官方清单中找不到 Minecraft " + minecraft);
        }
        Config instanceConfig = config.copyForGameDir(instance);
        progress.stage("安装 Minecraft " + minecraft + " 到实例…");
        new Installer(instanceConfig, token, progress).install(vanilla, config.downloadAssets());

        String loaderType = null;
        String loaderVersion = null;
        for (String key : new String[]{"fabric-loader", "quilt-loader", "forge", "neoforge"}) {
            if (index.dependencies.containsKey(key)) {
                loaderType = key.replace("-loader", "");
                loaderVersion = index.dependencies.get(key);
                break;
            }
        }
        String loaderVersionId = null;
        if (loaderType != null) {
            progress.stage("安装 " + loaderType + " 加载器 " + loaderVersion + "…");
            JavaManager manager = new JavaManager(instanceConfig, progress, token);
            java.util.List<JavaManager.JavaInfo> javas = manager.detectAll();
            int required = 8;
            String component = null;
            try {
                Path versionJson = new GamePaths(instance).versionJson(minecraft);
                VersionMeta vanillaMeta = VersionMeta.parse(Files.readString(versionJson), minecraft);
                required = vanillaMeta.javaMajor();
                component = vanillaMeta.javaComponent();
            } catch (Exception ignored) {
            }
            JavaManager.JavaInfo java = JavaManager.choose(javas, required, component);
            if (java == null) {
                throw new IOException("安装加载器需要 Java，但没有检测到可用运行时");
            }
            loaderVersionId = new LoaderInstaller(instanceConfig, progress, token)
                    .install(loaderType, minecraft, loaderVersion, java);
        }

        downloadPackFiles(instance, index.files);
        extractOverrides(mrpack, instance);
        Path marker = instance.resolve(".mclite/modpack.properties");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "project=" + projectId + "\nversion=" + versionId
                + "\nminecraft=" + minecraft + "\nloader=" + loaderType + "\nloaderVersion="
                + loaderVersion + "\n", StandardCharsets.UTF_8);
        return new ContentInstaller.Result(instance,
                loaderVersionId == null ? minecraft : loaderVersionId,
                title + " · " + minecraft
                        + (loaderType == null ? "" : " / " + loaderType));
    }

    private record PackIndex(Map<String, String> dependencies, List<PackFile> files) {
    }

    private record PackFile(String path, String url, String sha1, long size) {
    }

    private PackIndex readIndex(Path mrpack) throws IOException {
        try (ZipFile zip = new ZipFile(mrpack.toFile())) {
            ZipEntry entry = zip.getEntry("modrinth.index.json");
            if (entry == null) {
                throw new IOException("整合包中缺少 modrinth.index.json");
            }
            Map<String, Object> root;
            try (InputStream in = zip.getInputStream(entry)) {
                root = Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
            Map<String, String> deps = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : Json.obj(root.get("dependencies")).entrySet()) {
                if (e.getValue() instanceof String value) {
                    deps.put(e.getKey(), value);
                }
            }
            List<PackFile> files = new ArrayList<>();
            for (Object raw : Json.arr(root.get("files"))) {
                Map<String, Object> file = Json.obj(raw);
                String path = Json.str(file, "path");
                String env = Json.str(Json.obj(file.get("env")), "client", "required");
                if (path == null || "unsupported".equals(env)) {
                    continue;
                }
                List<Object> urls = Json.arr(file.get("downloads"));
                if (urls.isEmpty()) {
                    continue;
                }
                Map<String, Object> hashes = Json.obj(file.get("hashes"));
                files.add(new PackFile(path, String.valueOf(urls.get(0)),
                        Json.str(hashes, "sha1"), Json.lng(file, "fileSize", -1)));
            }
            return new PackIndex(deps, files);
        }
    }

    private void downloadPackFiles(Path instance, List<PackFile> files) throws IOException {
        if (files.isEmpty()) {
            return;
        }
        progress.stage("下载整合包文件（" + files.size() + " 个）…");
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS, runnable -> {
            Thread thread = new Thread(runnable, "mclite-modpack");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (PackFile file : files) {
                token.check();
                Path target = GamePaths.resolveUnder(instance, file.path());
                futures.add(pool.submit(() -> {
                    if (file.size() > 0) {
                        progress.addTotal(file.size());
                    }
                    net.download(file.url(), target, file.sha1(), file.size(),
                            file.path(), true);
                    int n = done.incrementAndGet();
                    if (n % 10 == 0 || n == files.size()) {
                        progress.stage("整合包文件（" + n + "/" + files.size() + "）…");
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    token.cancel();
                    throw new CancellationToken.CancelledException();
                } catch (ExecutionException ex) {
                    token.cancel();
                    throw new IOException(ex.getCause() == null
                            ? "整合包文件下载失败" : ex.getCause().getMessage(), ex.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void extractOverrides(Path mrpack, Path instance) throws IOException {
        progress.stage("解压整合包配置…");
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(mrpack))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                String prefix = null;
                if (name.startsWith("overrides/")) {
                    prefix = "overrides/";
                } else if (name.startsWith("client-overrides/")) {
                    prefix = "client-overrides/";
                }
                if (entry.isDirectory() || prefix == null) {
                    zip.closeEntry();
                    continue;
                }
                String relative = name.substring(prefix.length());
                Path target = GamePaths.resolveUnder(instance, relative);
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                zip.closeEntry();
            }
        }
    }
}
