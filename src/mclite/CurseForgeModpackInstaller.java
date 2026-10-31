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
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class CurseForgeModpackInstaller {
    private final Config config;
    private final ProgressSink progress;
    private final CancellationToken token;
    private final Net net;
    private final CurseForge curseForge;

    public CurseForgeModpackInstaller(Config config, ProgressSink progress, CancellationToken token) {
        this.config = config;
        this.progress = progress;
        this.token = token;
        this.net = new Net(progress, token);
        this.curseForge = new CurseForge(config.curseForgeApiKey(), progress, token);
    }

    public ContentInstaller.Result install(Modrinth.Project project, Modrinth.Version version,
                                           String gameVersion) throws IOException {
        RegionGate.requireGameDownloadAllowed();
        Modrinth.File packFile = version.primaryFile();
        if (packFile == null) {
            throw new IOException("CurseForge 整合包没有可下载文件");
        }
        Path cache = config.cacheDir().resolve("packs");
        Files.createDirectories(cache);
        Path zipFile = cache.resolve(packFile.filename());
        if (packFile.size() > 0) {
            progress.addTotal(packFile.size());
        }
        net.download(packFile.url(), zipFile, packFile.sha1(), packFile.size(),
                packFile.filename(), true);
        Path root = config.instancesRoot();
        String versionName = ModpackInstaller.versionIdFor(root,
                project.title() == null ? project.slug() : project.title(), "整合包");
        return installArchive(zipFile, versionName, project.title(), project.id(), version.id());
    }

    public ContentInstaller.Result installArchive(Path zipFile, String displayName)
            throws IOException {
        RegionGate.requireGameDownloadAllowed();
        String title = displayName == null || displayName.isBlank()
                ? "本地整合包" : displayName.trim();
        Path root = config.instancesRoot();
        String versionName = ModpackInstaller.versionIdFor(root, title, "整合包");
        return installArchive(zipFile, versionName, title, "local", "local");
    }

    private ContentInstaller.Result installArchive(Path zipFile, String slug, String title,
                                                    String projectId, String versionId)
            throws IOException {
        Manifest manifest = readManifest(zipFile);
        String minecraft = manifest.minecraft;
        Path root = config.instancesRoot();
        Path versionDir = root.resolve("versions").resolve(slug);
        Files.createDirectories(versionDir);
        Config rootConfig = config.copyForGameDir(root);

        Versions.FetchResult fetched = Versions.fetch(config, progress, token);
        VersionManifest.VersionEntry vanilla = fetched.manifest().find(minecraft);
        if (vanilla == null) {
            throw new IOException("官方清单中找不到 Minecraft " + minecraft);
        }
        progress.stage("安装 Minecraft " + minecraft + " 到当前目录…");
        new Installer(rootConfig, token, progress).install(vanilla, config.downloadAssets());

        String baseId = minecraft;
        if (manifest.loaderType != null) {
            progress.stage("安装 " + manifest.loaderType + " " + manifest.loaderVersion + "…");
            java.util.List<JavaManager.JavaInfo> javas = new JavaManager(rootConfig, progress,
                    token).detectAll();
            int required = 8;
            String component = null;
            try {
                VersionMeta meta = VersionMeta.parse(Files.readString(
                        new GamePaths(root).versionJson(minecraft)), minecraft);
                required = meta.javaMajor();
                component = meta.javaComponent();
            } catch (Exception ignored) {
            }
            JavaManager.JavaInfo java = JavaManager.choose(javas, required, component);
            if (java == null) {
                throw new IOException("安装加载器需要 Java");
            }
            String loaderId = new LoaderInstaller(rootConfig, progress, token)
                    .install(manifest.loaderType, minecraft, manifest.loaderVersion, java);
            if (loaderId != null && !loaderId.isBlank()) {
                baseId = loaderId;
            }
        }

        downloadFiles(versionDir, manifest.files);
        extractOverrides(zipFile, versionDir);
        Path marker = versionDir.resolve(".mclite/modpack.properties");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "project=" + projectId + "\nversion=" + versionId
                + "\nminecraft=" + minecraft + "\nloader=" + manifest.loaderType + "\n", StandardCharsets.UTF_8);
        Map<String, Object> versionJson = new java.util.LinkedHashMap<>();
        versionJson.put("id", slug);
        versionJson.put("inheritsFrom", baseId);
        versionJson.put("type", "release");
        Files.writeString(versionDir.resolve(slug + ".json"),
                LoaderInstaller.prettyJson(versionJson), StandardCharsets.UTF_8);
        return new ContentInstaller.Result(root, slug,
                title + " · " + minecraft);
    }

    static String[] parseLoader(String loaderId) {
        String id = loaderId == null ? "" : loaderId.trim();
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        for (String prefix : new String[]{"fabric-loader-", "quilt-loader-"}) {
            if (lower.startsWith(prefix)) {
                return new String[]{prefix.substring(0, prefix.indexOf('-')),
                        id.substring(prefix.length())};
            }
        }
        if (lower.startsWith("neoforge-")) {
            return new String[]{"neoforge", id.substring("neoforge-".length())};
        }
        if (lower.startsWith("forge-")) {
            return new String[]{"forge", id.substring("forge-".length())};
        }
        int forge = lower.indexOf("-forge-");
        if (forge > 0) {
            return new String[]{"forge", id.substring(forge + "-forge-".length())};
        }
        int dash = id.indexOf('-');
        if (dash > 0) {
            return new String[]{id.substring(0, dash), id.substring(dash + 1)};
        }
        return new String[]{null, null};
    }

    private record Manifest(String minecraft, String loaderType, String loaderVersion,
                            List<FileRef> files) {
    }

    private record FileRef(long projectId, long fileId) {
    }

    private Manifest readManifest(Path zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            var entry = zip.getEntry("manifest.json");
            if (entry == null) {
                throw new IOException("CurseForge 整合包缺少 manifest.json");
            }
            Map<String, Object> root;
            try (InputStream in = zip.getInputStream(entry)) {
                root = Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
            Map<String, Object> mc = Json.obj(root.get("minecraft"));
            String minecraft = Json.str(mc, "version");
            String loaderId = null;
            for (Object raw : Json.arr(mc.get("modLoaders"))) {
                Map<String, Object> loader = Json.obj(raw);
                if (Json.bool(loader, "primary", false)) {
                    loaderId = Json.str(loader, "id");
                    break;
                }
            }
            if (loaderId == null) {
                for (Object raw : Json.arr(mc.get("modLoaders"))) {
                    loaderId = Json.str(Json.obj(raw), "id");
                    if (loaderId != null) {
                        break;
                    }
                }
            }
            String loaderType = null;
            String loaderVersion = null;
            if (loaderId != null) {
                String[] parsed = parseLoader(loaderId);
                loaderType = parsed[0];
                loaderVersion = parsed[1];
            }
            List<FileRef> files = new ArrayList<>();
            for (Object raw : Json.arr(root.get("files"))) {
                Map<String, Object> file = Json.obj(raw);
                if (!Json.bool(file, "required", true)) {
                    continue;
                }
                files.add(new FileRef(Json.lng(file, "projectID", 0),
                        Json.lng(file, "fileID", 0)));
            }
            if (minecraft == null) {
                throw new IOException("manifest.json 缺少 Minecraft 版本");
            }
            return new Manifest(minecraft, loaderType, loaderVersion, files);
        }
    }

    private void downloadFiles(Path instance, List<FileRef> refs) throws IOException {
        if (refs.isEmpty()) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(6, runnable -> {
            Thread thread = new Thread(runnable, "mclite-cf-pack");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (FileRef ref : refs) {
                futures.add(pool.submit(() -> {
                    token.check();
                    Modrinth.Version file = curseForge.file("cf:" + ref.projectId(),
                            "cf:" + ref.fileId());
                    Modrinth.File download = file == null ? null : file.primaryFile();
                    if (download == null) {
                        return null;
                    }
                    Path mods = instance.resolve("mods");
                    Files.createDirectories(mods);
                    Path target = GamePaths.resolveUnder(mods, download.filename());
                    if (download.size() > 0) {
                        progress.addTotal(download.size());
                    }
                    net.download(download.url(), target, download.sha1(), download.size(),
                            download.filename(), true);
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
                    throw new IOException("整合包文件下载失败: " + ex.getCause(), ex.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void extractOverrides(Path zipFile, Path instance) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith("overrides/")) {
                    Path target = GamePaths.resolveUnder(instance, name.substring("overrides/".length()));
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
    }
}
