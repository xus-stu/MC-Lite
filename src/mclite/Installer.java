package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Installer {
    private static final int ASSET_THREADS = 12;

    private final Config config;
    private final GamePaths paths;
    private final CancellationToken token;
    private final ProgressSink progress;
    private final Net net;
    private final Rules.Env env;

    public Installer(Config config, CancellationToken token, ProgressSink progress) {
        this.config = config;
        this.paths = new GamePaths(config.gameDir());
        this.token = token;
        this.progress = progress;
        this.net = new Net(progress, token);
        this.env = Rules.Env.current();
    }

    public record InstallResult(VersionMeta meta, boolean assetsComplete, boolean virtualAssets,
                                boolean mappedResources, long estimatedBytes) {
    }

    private record Planned(String label, VersionMeta.Download download, Path target,
                           boolean verifyExistingSha1, String mirrorUrl) {
    }

    private record NativeJar(Path jar, List<String> excludes) {
    }

    private record Plan(List<Planned> files, List<NativeJar> natives, long totalBytes) {
    }

    public InstallResult install(VersionManifest.VersionEntry entry, boolean includeAssets) throws IOException {
        RegionGate.requireGameDownloadAllowed();
        token.check();
        progress.indeterminate(true);
        progress.stage("读取版本信息…");

        Path versionJson = paths.versionJson(entry.id());
        VersionMeta meta;
        try {
            if (!Files.isRegularFile(versionJson) || !Net.sha1Matches(versionJson, entry.sha1())) {
                net.downloadFallback(entry.url(), Mirror.versionJson(entry.id()), versionJson,
                        entry.sha1(), -1, "版本信息 " + entry.id(), true);
            }
            meta = VersionMeta.parse(Files.readString(versionJson, StandardCharsets.UTF_8), entry.id());
        } catch (SecurityException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
        if (!meta.id().equals(entry.id())) {
            throw new IOException("版本元数据与所选版本不一致");
        }
        Log.info("开始安装 " + entry.id() + "，完整资源=" + includeAssets);

        Plan plan = buildPlan(meta, includeAssets);
        if (plan.totalBytes() > 0) {
            progress.addTotal(plan.totalBytes());
        } else {
            progress.indeterminate(true);
        }

        try {
            AtomicInteger index = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(6, runnable -> {
                Thread thread = new Thread(runnable, "mclite-files");
                thread.setDaemon(true);
                return thread;
            });
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (Planned planned : plan.files()) {
                    token.check();
                    futures.add(pool.submit(() -> {
                        token.check();
                        int number = index.incrementAndGet();
                        String label = planned.label() + "（" + number + "/"
                                + plan.files().size() + "）";
                        if (config.autoMirror() && planned.mirrorUrl() != null) {
                            net.downloadRacing(planned.download().url(), planned.mirrorUrl(),
                                    planned.target(), planned.download().sha1(),
                                    planned.download().size(), label, planned.verifyExistingSha1());
                        } else {
                            net.download(planned.download().url(), planned.target(),
                                    planned.download().sha1(), planned.download().size(), label,
                                    planned.verifyExistingSha1());
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
                        if (cause instanceof SecurityException) {
                            throw new IOException(cause.getMessage(), cause);
                        }
                        throw cause instanceof IOException io ? io
                                : new IOException(cause == null ? "文件下载失败" : cause.getMessage(), cause);
                    }
                }
            } finally {
                pool.shutdownNow();
            }

            if (!plan.natives().isEmpty()) {
                progress.stage("解压原生库…");
                for (NativeJar nativeJar : plan.natives()) {
                    extractNatives(nativeJar.jar(), paths.nativesDir(meta.id()), nativeJar.excludes());
                }
            }

            boolean assetsComplete = true;
            boolean virtual = false;
            boolean mapped = false;
            if (meta.assetIndex() != null) {
                Path indexFile = paths.assetIndex(meta.assetsId());
                Map<String, Object> indexJson = Json.obj(Json.parse(Files.readString(indexFile, StandardCharsets.UTF_8)));
                virtual = Json.bool(indexJson, "virtual", false);
                mapped = Json.bool(indexJson, "map_to_resources", false);
                Map<String, Object> objects = Json.obj(indexJson.get("objects"));

                boolean previousComplete = paths.hasMarker(meta.id()) && paths.assetsComplete(meta.id());
                if (includeAssets) {
                    downloadAssets(objects);
                    if (virtual) {
                        linkAssets(objects, paths.virtualAssetsDir(meta.assetsId()), "整理旧版资源");
                    }
                    if (mapped) {
                        linkAssets(objects, paths.resourcesDir(), "映射旧版资源");
                    }
                    assetsComplete = true;
                } else {
                    assetsComplete = previousComplete;
                    progress.stage("已跳过完整资源下载");
                }
            }

            Properties marker = new Properties();
            marker.setProperty("versionId", meta.id());
            marker.setProperty("assetsComplete", Boolean.toString(assetsComplete));
            marker.setProperty("assetId", meta.assetsId() == null ? "" : meta.assetsId());
            marker.setProperty("includeAssets", Boolean.toString(includeAssets));
            marker.setProperty("virtualAssets", Boolean.toString(virtual));
            marker.setProperty("mappedResources", Boolean.toString(mapped));
            marker.setProperty("installedAt", Config.timestamp());
            try {
                paths.writeMarker(meta.id(), marker);
            } catch (IOException ex) {
                Log.warn("写入安装标记失败: " + ex.getMessage());
            }

            progress.stage("安装完成");
            Log.info("安装完成 " + meta.id() + "，预计下载 " + Formatting.bytes(plan.totalBytes()));
            return new InstallResult(meta, assetsComplete, virtual, mapped, plan.totalBytes());
        } catch (CancellationToken.CancelledException ex) {
            progress.stage("已取消");
            throw ex;
        } catch (SecurityException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private Plan buildPlan(VersionMeta meta, boolean includeAssets) throws IOException {
        List<Planned> files = new ArrayList<>();
        List<NativeJar> natives = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long total = 0;

        total += add(files, seen, "客户端", meta.client(), paths.clientJar(meta.id()), true,
                Mirror.client(meta.id()));

        for (VersionMeta.Library library : meta.libraries()) {
            if (!library.applies(env)) {
                continue;
            }
            VersionMeta.Download artifact = library.artifact();
            if (artifact != null) {
                Path target = libraryTarget(artifact);
                total += add(files, seen, "运行库", artifact, target, true,
                        Mirror.library(artifact.path()));
            }
            VersionMeta.Download nativeDownload = library.nativeFor(env);
            if (nativeDownload != null) {
                Path target = libraryTarget(nativeDownload);
                total += add(files, seen, "原生库", nativeDownload, target, true,
                        Mirror.library(nativeDownload.path()));
                natives.add(new NativeJar(target, library.extractExclude()));
            }
        }

        if (meta.loggingFile() != null) {
            Path target = paths.logConfig(meta.loggingFile().path());
            total += add(files, seen, "日志配置", meta.loggingFile(), target, true, null);
        }

        if (meta.assetIndex() != null) {
            Path target = paths.assetIndex(meta.assetsId());
            total += add(files, seen, "资源索引", new VersionMeta.Download(null,
                    meta.assetIndex().url(), meta.assetIndex().sha1(), meta.assetIndex().size()),
                    target, true, null);
            if (includeAssets) {
                total += Math.max(0, meta.assetIndex().totalSize());
            }
        }

        return new Plan(files, natives, total);
    }

    private Path libraryTarget(VersionMeta.Download download) {
        if (download.path() == null || download.path().isBlank()) {
            throw new IllegalArgumentException("运行库缺少路径");
        }
        return GamePaths.resolveUnder(paths.librariesDir(), download.path());
    }

    private long add(List<Planned> files, Set<String> seen, String label,
                     VersionMeta.Download download, Path target, boolean verifyExistingSha1,
                     String mirrorUrl) {
        String key = target.toAbsolutePath().normalize().toString();
        if (!seen.add(key)) {
            return 0;
        }
        files.add(new Planned(label, download, target, verifyExistingSha1, mirrorUrl));
        return Math.max(0, download.size());
    }

    private void downloadAssets(Map<String, Object> objects) throws IOException {
        List<Asset> assets = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            Map<String, Object> object = Json.obj(entry.getValue());
            String hash = Json.str(object, "hash");
            long size = Json.lng(object, "size", -1);
            if (hash == null || !hash.matches("[0-9a-fA-F]{40}") || size < 0) {
                throw new IOException("资源索引中存在非法条目: " + entry.getKey());
            }
            String lower = hash.toLowerCase();
            if (hashes.add(lower)) {
                assets.add(new Asset(entry.getKey(), lower, size));
            }
        }
        if (assets.isEmpty()) {
            return;
        }
        boolean useMirror = false;
        if (config.autoMirror()) {
            Asset sample = assets.get(0);
            String official = "https://resources.download.minecraft.net/"
                    + sample.hash().substring(0, 2) + "/" + sample.hash();
            progress.stage("测试官方源与镜像源速度…");
            useMirror = net.mirrorFaster(official, Mirror.asset(sample.hash()));
            Log.info("资源下载源: " + (useMirror ? "BMCLAPI 镜像" : "Mojang 官方"));
        }

        final boolean finalUseMirror = useMirror;
        Log.info("开始下载 " + assets.size() + " 个资源对象");
        AtomicInteger finished = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(ASSET_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "mclite-assets");
            thread.setDaemon(true);
            return thread;
        });
        List<Future<?>> futures = new ArrayList<>(assets.size());
        try {
            for (Asset asset : assets) {
                token.check();
                futures.add(pool.submit(() -> {
                    token.check();
                    String official = "https://resources.download.minecraft.net/"
                            + asset.hash().substring(0, 2) + "/" + asset.hash();
                    String mirror = Mirror.asset(asset.hash());
                    Path target = paths.assetObject(asset.hash());
                    String preferred = finalUseMirror ? mirror : official;
                    String fallback = finalUseMirror ? official : mirror;
                    net.downloadFallback(preferred, fallback, target, asset.hash(), asset.size(),
                            "资源 " + asset.name(), true);
                    int done = finished.incrementAndGet();
                    if (done % 40 == 0 || done == assets.size()) {
                        progress.stage("下载资源文件（" + done + "/" + assets.size() + "）");
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
                    String message = cause == null || cause.getMessage() == null
                            ? "资源下载失败" : cause.getMessage();
                    throw new IOException(message, cause);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        progress.stage("资源下载完成");
    }

    private void linkAssets(Map<String, Object> objects, Path targetBase, String stage) throws IOException {
        progress.stage(stage + "…");
        int copied = 0;
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            token.check();
            String name = entry.getKey();
            String hash = Json.str(Json.obj(entry.getValue()), "hash");
            if (hash == null) {
                continue;
            }
            Path source = paths.assetObject(hash);
            Path target = GamePaths.resolveUnder(targetBase, name);
            if (Files.isRegularFile(target) && Files.size(target) == Files.size(source)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            try {
                Files.deleteIfExists(target);
                try {
                    Files.createLink(target, source);
                } catch (IOException | UnsupportedOperationException ex) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new IOException("整理旧版资源失败: " + name, ex);
            }
            copied++;
            if (copied % 80 == 0) {
                progress.stage(stage + "（" + copied + " 个文件）");
            }
        }
        Log.info(stage + "完成，共创建 " + copied + " 个文件");
    }

    public static void extractNatives(Path jar, Path nativesDir, List<String> excludes) throws IOException {
        if (!Files.isRegularFile(jar)) {
            throw new IOException("原生库文件不存在: " + jar.getFileName());
        }
        Files.createDirectories(nativesDir);
        try (InputStream input = Files.newInputStream(jar);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
                    zip.closeEntry();
                    continue;
                }
                boolean excluded = false;
                for (String prefix : excludes) {
                    if (name.startsWith(prefix)) {
                        excluded = true;
                        break;
                    }
                }
                if (!excluded && name.startsWith("META-INF/")) {
                    excluded = true;
                }
                if (excluded) {
                    zip.closeEntry();
                    continue;
                }
                Path target = GamePaths.resolveUnder(nativesDir, name);
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                zip.closeEntry();
            }
        }
    }

    public static long estimate(VersionMeta meta, boolean includeAssets) {
        Set<String> seen = new HashSet<>();
        long total = 0;
        total += addEstimate(seen, meta.client().url(), meta.client().size());
        Rules.Env env = Rules.Env.current();
        for (VersionMeta.Library library : meta.libraries()) {
            if (!library.applies(env)) {
                continue;
            }
            VersionMeta.Download artifact = library.artifact();
            if (artifact != null) {
                total += addEstimate(seen, artifact.url(), artifact.size());
            }
            VersionMeta.Download nativeDownload = library.nativeFor(env);
            if (nativeDownload != null) {
                total += addEstimate(seen, nativeDownload.url(), nativeDownload.size());
            }
        }
        if (meta.loggingFile() != null) {
            total += addEstimate(seen, meta.loggingFile().url(), meta.loggingFile().size());
        }
        if (meta.assetIndex() != null) {
            total += addEstimate(seen, meta.assetIndex().url(), meta.assetIndex().size());
            if (includeAssets) {
                total += Math.max(0, meta.assetIndex().totalSize());
            }
        }
        return total;
    }

    private static long addEstimate(Set<String> seen, String url, long size) {
        if (url == null || !seen.add(url)) {
            return 0;
        }
        return Math.max(0, size);
    }

    private record Asset(String name, String hash, long size) {
    }
}
