package mclite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

public final class Net {
    public static final String USER_AGENT = "MCLite/1.0";
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "piston-meta.mojang.com",
            "launchermeta.mojang.com",
            "launcher.mojang.com",
            "piston-data.mojang.com",
            "libraries.minecraft.net",
            "resources.download.minecraft.net",
            "api.modrinth.com",
            "cdn.modrinth.com",
            "cdn-raw.modrinth.com",
            "search.mcmod.cn",
            "www.mcmod.cn",
            "meta.fabricmc.net",
            "maven.fabricmc.net",
            "meta.quiltmc.org",
            "maven.quiltmc.org",
            "maven.minecraftforge.net",
            "maven.neoforged.net",
            "bmclapi2.bangbang93.com",
            "api.curseforge.com",
            "edge.forgecdn.net",
            "mediafilez.forgecdn.net",
            "media.forgecdn.net"
    );
    private static final int MAX_METADATA_BYTES = 16 * 1024 * 1024;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_ATTEMPTS = 3;
    private static final int PROBE_BYTES = 128 * 1024;
    private static final int SEGMENT_COUNT = Math.max(1,
            Math.min(8, Integer.getInteger("mclite.download.segments", 4)));
    private static final long SEGMENT_MIN_BYTES = SEGMENT_COUNT > 1
            ? 8L * 1024 * 1024 : Long.MAX_VALUE;
    private static final int CLIENT_POOL_SIZE = 6;
    private static final HttpClient[] CLIENTS = new HttpClient[CLIENT_POOL_SIZE];
    private static final AtomicInteger CLIENT_INDEX = new AtomicInteger();
    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mclite-download-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    static {
        for (int i = 0; i < CLIENTS.length; i++) {
            CLIENTS[i] = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20))
                    .version(HttpClient.Version.HTTP_2)
                    .build();
        }
    }

    private final ProgressSink progress;
    private final CancellationToken token;
    private final Map<String, Boolean> sourceChoices = new ConcurrentHashMap<>();
    private final Object sourceLock = new Object();

    public Net(ProgressSink progress, CancellationToken token) {
        this.progress = progress == null ? ProgressSink.NOOP : progress;
        this.token = token == null ? new CancellationToken() : token;
    }

    private static HttpClient client() {
        return CLIENTS[Math.floorMod(CLIENT_INDEX.getAndIncrement(), CLIENT_POOL_SIZE)];
    }

    public byte[] getUnverifiedBytes(String url, String label) throws IOException {
        return getUnverifiedBytes(url, label, java.util.Map.of());
    }

    public byte[] getUnverifiedBytes(String url, String label,
                                     java.util.Map<String, String> headers) throws IOException {
        return fetchBytesChecked(url, null, -1, label, false, headers);
    }

    public byte[] getBytes(String url, String expectedSha1, long expectedSize, String label) throws IOException {
        requireSha1(expectedSha1);
        return fetchBytesChecked(url, expectedSha1, expectedSize, label, true);
    }

    private byte[] fetchBytesChecked(String url, String expectedSha1, long expectedSize,
                                     String label, boolean requireChecksum) throws IOException {
        return fetchBytesChecked(url, expectedSha1, expectedSize, label, requireChecksum,
                java.util.Map.of());
    }

    private byte[] fetchBytesChecked(String url, String expectedSha1, long expectedSize,
                                     String label, boolean requireChecksum,
                                     java.util.Map<String, String> headers) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            token.check();
            try {
                progress.stage(label);
                HttpRequest request = buildRequest(url, headers);
                HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
                InputStream body = response.body();
                token.register(body);
                ScheduledFuture<?> watchdog = WATCHDOG.schedule(
                        () -> closeQuietly(body),
                        downloadTimeout(expectedSize).toMillis(), TimeUnit.MILLISECONDS);
                try (InputStream in = body) {
                    if (response.statusCode() != 200) {
                        throw new IOException("HTTP " + response.statusCode() + "：" + label);
                    }
                    checkFinalUri(response.uri(), request.uri());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    MessageDigest digest = sha1();
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long total = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        token.check();
                        total += read;
                        if (total > MAX_METADATA_BYTES) {
                            throw new IOException("元数据文件异常过大：" + label);
                        }
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                    }
                    byte[] bytes = out.toByteArray();
                    verifyDigest(bytes, digest.digest(), expectedSha1, expectedSize, label, requireChecksum);
                    return bytes;
                } finally {
                    watchdog.cancel(false);
                    token.unregister(body);
                }
            } catch (CancellationToken.CancelledException ex) {
                throw ex;
            } catch (SecurityException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CancellationToken.CancelledException();
            } catch (IOException ex) {
                if (token.isCancelled()) {
                    throw new CancellationToken.CancelledException();
                }
                last = ex;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
            }
            sleepBeforeRetry(attempt);
        }
        throw last == null ? new IOException("下载失败：" + label) : last;
    }

    public void download(String url, Path target, String expectedSha1, long expectedSize,
                         String label, boolean verifyExistingSha1) throws IOException {
        download(url, target, expectedSha1, expectedSize, label, verifyExistingSha1,
                SEGMENT_MIN_BYTES);
    }

    public void download(String url, Path target, String expectedSha1, long expectedSize,
                         String label, boolean verifyExistingSha1, long segmentMinBytes)
            throws IOException {
        requireSha1(expectedSha1);
        token.check();
        try {
            if (Files.isRegularFile(target) && expectedSize >= 0 && Files.size(target) == expectedSize) {
                if (!verifyExistingSha1 || sha1Matches(target, expectedSha1)) {
                    progress.add(expectedSize);
                    return;
                }
                Log.warn("现有文件 SHA-1 不匹配，重新下载：" + target);
            }
        } catch (IOException ignored) {

        }

        if (expectedSize >= segmentMinBytes) {
            Segmented segmented = downloadSegmented(url, target, expectedSha1, expectedSize, label);
            if (segmented == Segmented.SUCCESS) {
                return;
            }
            if (segmented == Segmented.FAILED) {
                throw new IOException("分块下载失败：" + label);
            }
        }

        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            token.check();
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            long attemptWritten = 0;
            try {
                Files.createDirectories(target.getParent());
                progress.stage(label);
                HttpRequest request = buildRequest(url, Map.of(),
                        downloadTimeout(expectedSize));
                HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
                InputStream body = response.body();
                token.register(body);
                ScheduledFuture<?> watchdog = WATCHDOG.schedule(
                        () -> closeQuietly(body),
                        downloadTimeout(expectedSize).toMillis(), TimeUnit.MILLISECONDS);
                try (InputStream in = body) {
                    if (response.statusCode() != 200) {
                        throw new IOException("HTTP " + response.statusCode() + "：" + label);
                    }
                    checkFinalUri(response.uri(), request.uri());
                    Files.deleteIfExists(temp);
                    MessageDigest digest = sha1();
                    try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            token.check();
                            channel.write(ByteBuffer.wrap(buffer, 0, read));
                            digest.update(buffer, 0, read);
                            attemptWritten += read;
                            progress.add(read);
                        }
                    }
                    verifyDigest(null, digest.digest(), expectedSha1, expectedSize, label, true);
                    if (expectedSize >= 0 && attemptWritten != expectedSize) {
                        throw new IOException("文件大小不匹配：" + label);
                    }
                    moveIntoPlace(temp, target);
                    return;
                } finally {
                    watchdog.cancel(false);
                    token.unregister(body);
                }
            } catch (CancellationToken.CancelledException ex) {
                deleteQuietly(temp);
                throw ex;
            } catch (SecurityException ex) {
                deleteQuietly(temp);
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                deleteQuietly(temp);
                throw new CancellationToken.CancelledException();
            } catch (IOException ex) {
                deleteQuietly(temp);
                if (token.isCancelled()) {
                    throw new CancellationToken.CancelledException();
                }
                if (attemptWritten > 0) {
                    progress.rewind(attemptWritten);
                }
                last = ex;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
            }
            sleepBeforeRetry(attempt);
        }
        throw last == null ? new IOException("下载失败：" + label) : last;
    }

    private enum Segmented { SUCCESS, UNSUPPORTED, FAILED }

    private Segmented downloadSegmented(String url, Path target, String expectedSha1,
                                        long expectedSize, String label) throws IOException {
        if (!supportsRange(url)) {
            return Segmented.UNSUPPORTED;
        }
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        Files.createDirectories(target.getParent());
        Files.deleteIfExists(temp);
        progress.stage(label + " · 分块下载");
        java.util.concurrent.atomic.AtomicLong transferred = new java.util.concurrent.atomic.AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(SEGMENT_COUNT, runnable -> {
            Thread thread = new Thread(runnable, "mclite-segment");
            thread.setDaemon(true);
            return thread;
        });
        boolean success = false;
        try (RandomAccessFile file = new RandomAccessFile(temp.toFile(), "rw");
             FileChannel channel = file.getChannel()) {
            file.setLength(expectedSize);
            long chunk = (expectedSize + SEGMENT_COUNT - 1) / SEGMENT_COUNT;
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < SEGMENT_COUNT; i++) {
                long from = i * chunk;
                if (from >= expectedSize) {
                    break;
                }
                long to = Math.min(expectedSize - 1, from + chunk - 1);
                futures.add(pool.submit(() -> {
                    downloadSegment(url, channel, from, to, label, transferred);
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
                    Throwable cause = ex.getCause();
                    if (cause instanceof CancellationToken.CancelledException cancelled) {
                        throw cancelled;
                    }
                    if (cause instanceof SecurityException security) {
                        throw security;
                    }
                    throw cause instanceof IOException io ? io
                            : new IOException(cause == null ? "分块下载失败" : cause.getMessage(), cause);
                }
            }
            if (transferred.get() != expectedSize || Files.size(temp) != expectedSize) {
                throw new IOException("分块下载大小不匹配：" + label);
            }
            String actual = sha1Hex(temp);
            if (!actual.equalsIgnoreCase(expectedSha1)) {
                throw new SecurityException("SHA-1 校验失败：" + label);
            }
            moveIntoPlace(temp, target);
            success = true;
            return Segmented.SUCCESS;
        } catch (CancellationToken.CancelledException | SecurityException ex) {
            throw ex;
        } catch (IOException ex) {
            if (token.isCancelled()) {
                throw new CancellationToken.CancelledException();
            }
            Log.warn("分块下载失败，稍后重试：" + label + " · " + ex.getMessage());
            return transferred.get() > 0 ? Segmented.FAILED : Segmented.UNSUPPORTED;
        } finally {
            pool.shutdownNow();
            if (!success) {
                deleteQuietly(temp);
            }
        }
    }

    private void downloadSegment(String url, FileChannel channel, long from, long to,
                                 String label, java.util.concurrent.atomic.AtomicLong transferred)
            throws IOException {
        int attempts = 0;
        long segmentBytes = 0;
        while (true) {
            attempts++;
            token.check();
            try {
                HttpRequest request = buildRequest(url, Map.of(
                        "Range", "bytes=" + from + "-" + to));
                HttpResponse<InputStream> response = client().send(request,
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = response.body()) {
                    if (response.statusCode() != 206) {
                        throw new IOException("服务器未返回分段内容：" + label);
                    }
                    checkFinalUri(response.uri(), request.uri());
                    String contentRange = response.headers()
                            .firstValue("content-range").orElse("");
                    if (!contentRange.isEmpty()
                            && !contentRange.startsWith("bytes " + from + "-" + to + "/")) {
                        throw new IOException("分段范围不匹配：" + label);
                    }
                    long position = from;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        token.check();
                        channel.write(ByteBuffer.wrap(buffer, 0, read), position);
                        position += read;
                        segmentBytes += read;
                        transferred.addAndGet(read);
                        progress.add(read);
                    }
                }
                long expected = to - from + 1;
                if (segmentBytes != expected) {
                    throw new IOException("分段大小不匹配：" + label);
                }
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CancellationToken.CancelledException();
            } catch (SecurityException ex) {
                throw ex;
            } catch (IOException ex) {
                if (token.isCancelled()) {
                    throw new CancellationToken.CancelledException();
                }
                if (segmentBytes > 0 || attempts >= MAX_ATTEMPTS) {
                    throw ex;
                }
                sleepBeforeRetry(attempts);
            }
        }
    }

    private boolean supportsRange(String url) {
        try {
            HttpRequest request = buildRequest(url, Map.of("Range", "bytes=0-0"));
            HttpResponse<InputStream> response = client().send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                return response.statusCode() == 206;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public void downloadPreferred(String officialUrl, String mirrorUrl, Path target,
                               String expectedSha1, long expectedSize, String label,
                               boolean verifyExistingSha1) throws IOException {
        if (mirrorUrl == null || mirrorUrl.isBlank()) {
            download(officialUrl, target, expectedSha1, expectedSize, label, verifyExistingSha1);
            return;
        }
        if (expectedSize >= 0 && expectedSize < 256L * 1024) {
            // 小文件不值得先测速，直接官方优先、失败切镜像。
            downloadFallback(officialUrl, mirrorUrl, target, expectedSha1, expectedSize,
                    label, verifyExistingSha1);
            return;
        }
        boolean mirrorPreferred;
        try {
            mirrorPreferred = mirrorFaster(officialUrl, mirrorUrl);
        } catch (SecurityException ex) {
            throw ex;
        }
        String preferred = mirrorPreferred ? mirrorUrl : officialUrl;
        String fallback = mirrorPreferred ? officialUrl : mirrorUrl;
        try {
            download(preferred, target, expectedSha1, expectedSize, label, verifyExistingSha1);
        } catch (IOException | SecurityException ex) {
            Log.warn("下载源失败，切换备用源：" + label + " · " + ex.getMessage());
            download(fallback, target, expectedSha1, expectedSize, label, verifyExistingSha1);
        }
    }

    public boolean mirrorFaster(String officialUrl, String mirrorUrl) {
        if (mirrorUrl == null || mirrorUrl.isBlank()) {
            return false;
        }
        String key = sourceKey(officialUrl, mirrorUrl);
        Boolean cached = sourceChoices.get(key);
        if (cached != null) {
            return cached;
        }
        synchronized (sourceLock) {
            cached = sourceChoices.get(key);
            if (cached != null) {
                return cached;
            }
            boolean result = chooseSource(officialUrl, mirrorUrl);
            sourceChoices.put(key, result);
            return result;
        }
    }

    private boolean chooseSource(String officialUrl, String mirrorUrl) {
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "mclite-probe");
            thread.setDaemon(true);
            return thread;
        });
        Probe official;
        Probe mirror;
        try {
            Future<Probe> officialFuture = pool.submit(() -> probeSpeed(officialUrl));
            Future<Probe> mirrorFuture = pool.submit(() -> probeSpeed(mirrorUrl));
            official = await(officialFuture);
            mirror = await(mirrorFuture);
        } finally {
            pool.shutdownNow();
        }
        if (official == null && mirror == null) {
            return false;
        }
        if (official == null) {
            return true;
        }
        if (mirror == null) {
            return false;
        }
        if (sameResource(official.uri(), mirror.uri())) {
            Log.info("官方源与镜像返回同一文件，跳过重复下载");
            return false;
        }
        boolean mirrorFaster = mirror.bytesPerSecond() > official.bytesPerSecond() * 1.1;
        Log.info("下载源测速：官方 " + speedText(official)
                + "，镜像 " + speedText(mirror)
                + "，使用 " + (mirrorFaster ? "BMCLAPI 镜像" : "Mojang 官方"));
        return mirrorFaster;
    }

    private Probe probeSpeed(String url) {
        long start = System.nanoTime();
        try {
            URI uri = normalizeAndValidate(url);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Encoding", "identity")
                    .header("Range", "bytes=0-" + (PROBE_BYTES - 1))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client().send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200 && response.statusCode() != 206) {
                return null;
            }
            checkFinalUri(response.uri(), request.uri());
            long total = 0;
            long deadline = start + 4_000_000_000L;
            try (InputStream in = response.body()) {
                byte[] buffer = new byte[32 * 1024];
                while (total < PROBE_BYTES && System.nanoTime() < deadline) {
                    int max = (int) Math.min(buffer.length, PROBE_BYTES - total);
                    int read = in.read(buffer, 0, max);
                    if (read < 0) {
                        break;
                    }
                    total += read;
                }
            }
            if (total <= 0) {
                return null;
            }
            double seconds = Math.max(0.001, (System.nanoTime() - start) / 1e9);
            return new Probe(response.uri(), total, total / seconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Probe await(Future<Probe> future) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return null;
        } catch (ExecutionException ex) {
            return null;
        }
    }

    private static boolean sameResource(URI left, URI right) {
        return left != null && right != null
                && left.getScheme() != null && left.getScheme().equalsIgnoreCase(right.getScheme())
                && Objects.equals(left.getHost(), right.getHost())
                && Objects.equals(left.getRawPath(), right.getRawPath());
    }

    private static String sourceKey(String officialUrl, String mirrorUrl) {
        try {
            URI official = URI.create(officialUrl);
            URI mirror = URI.create(mirrorUrl);
            String path = mirror.getRawPath() == null ? "" : mirror.getRawPath();
            int slash = path.indexOf('/', 1);
            String prefix = slash > 0 ? path.substring(0, slash) : path;
            return official.getHost() + "|" + mirror.getHost() + "|" + prefix;
        } catch (RuntimeException ex) {
            return officialUrl + "|" + mirrorUrl;
        }
    }

    private static String speedText(Probe probe) {
        return Formatting.bytes(Math.round(probe.bytesPerSecond())) + "/s";
    }

    private record Probe(URI uri, long bytes, double bytesPerSecond) {
    }

    public void downloadFallback(String preferredUrl, String fallbackUrl, Path target,
                                 String expectedSha1, long expectedSize, String label,
                                 boolean verifyExistingSha1) throws IOException {
        try {
            download(preferredUrl, target, expectedSha1, expectedSize, label, verifyExistingSha1);
        } catch (IOException | SecurityException ex) {
            if (fallbackUrl == null || fallbackUrl.isBlank()) {
                throw ex;
            }
            Log.warn("下载源失败，切换备用源：" + label + " · " + ex.getMessage());
            download(fallbackUrl, target, expectedSha1, expectedSize, label, verifyExistingSha1);
        }
    }

    public static byte[] sha1Digest(String value) {
        return sha1().digest(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha1Hex(Path file) throws IOException {
        MessageDigest digest = sha1();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static boolean sha1Matches(Path file, String expected) throws IOException {
        return file != null && Files.isRegularFile(file)
                && sha1Hex(file).equalsIgnoreCase(expected);
    }

    private HttpRequest buildRequest(String url) {
        return buildRequest(url, java.util.Map.of());
    }

    private HttpRequest buildRequest(String url, java.util.Map<String, String> headers) {
        return buildRequest(url, headers, Duration.ofMinutes(2));
    }

    private HttpRequest buildRequest(String url, java.util.Map<String, String> headers,
                                     Duration timeout) {
        URI uri = normalizeAndValidate(url);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Encoding", "identity");
        for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder.GET().build();
    }

    public static URI normalizeAndValidate(String url) {
        if (url == null || url.isBlank()) {
            throw new SecurityException("下载地址为空");
        }
        URI original;
        try {
            original = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("非法下载地址");
        }
        String host = original.getHost();
        if (host == null) {
            throw new SecurityException("下载地址缺少主机名");
        }
        host = host.toLowerCase();
        if (!ALLOWED_HOSTS.contains(host)) {
            throw new SecurityException("拒绝非官方下载源：" + host);
        }
        if (original.getUserInfo() != null) {
            throw new SecurityException("下载地址不允许包含用户信息");
        }
        if (original.getPort() != -1 && original.getPort() != 443) {
            throw new SecurityException("只允许 HTTPS 443 端口");
        }
        String scheme = original.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw new SecurityException("只允许 HTTP(S) 下载地址");
        }
        String rawPath = original.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            throw new SecurityException("下载地址缺少路径");
        }
        StringBuilder https = new StringBuilder("https://").append(host).append(rawPath);
        if (original.getRawQuery() != null) {
            https.append('?').append(original.getRawQuery());
        }
        try {
            return new URI(https.toString());
        } catch (URISyntaxException ex) {
            throw new SecurityException("非法下载地址");
        }
    }

    private void checkFinalUri(URI uri, URI original) {
        if (uri == null || uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new SecurityException("下载被重定向到非 HTTPS 地址，已拒绝");
        }
        String host = uri.getHost();
        if (original != null && "bmclapi2.bangbang93.com"
                .equalsIgnoreCase(original.getHost()) && publicHost(host)) {
            // BMCLAPI 会把文件 302 到志愿者节点（bmclapi*.sakura.sld.tw、
            // bmclapi*.hurtmc.com 等），节点域名不固定，文件本身有 SHA-1 校验。
            return;
        }
        if (!allowedFinalHost(host)) {
            throw new SecurityException("下载被重定向到非官方地址，已拒绝：" + host);
        }
    }

    private static boolean allowedFinalHost(String value) {
        String host = value.toLowerCase(java.util.Locale.ROOT);
        if (ALLOWED_HOSTS.contains(host)) {
            return true;
        }
        return host.startsWith("bmclapi")
                && (host.endsWith(".sakura.sld.tw") || host.endsWith(".bangbang93.com"));
    }

    private static boolean publicHost(String host) {
        try {
            for (java.net.InetAddress address : java.net.InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void verifyDigest(byte[] bytes, byte[] actual, String expectedSha1,
                              long expectedSize, String label, boolean requireChecksum) {
        if (expectedSha1 != null) {
            requireSha1(expectedSha1);
            String actualHex = HexFormat.of().formatHex(actual);
            if (!actualHex.equalsIgnoreCase(expectedSha1)) {
                throw new SecurityException("SHA-1 校验失败：" + label);
            }
        } else if (requireChecksum) {
            throw new SecurityException("官方元数据缺少 SHA-1，已拒绝下载：" + label);
        }
        if (bytes != null && expectedSize >= 0 && bytes.length != expectedSize) {
            throw new SecurityException("文件大小校验失败：" + label);
        }
    }

    private static void requireSha1(String sha1) {
        if (sha1 == null || !sha1.matches("[0-9a-fA-F]{40}")) {
            throw new SecurityException("官方元数据中的 SHA-1 缺失或格式错误，已拒绝下载");
        }
    }

    private static Duration downloadTimeout(long expectedSize) {
        if (expectedSize <= 0) {
            return Duration.ofMinutes(2);
        }
        long seconds = Math.max(3, Math.min(120,
                expectedSize / (1024L * 1024L) + 2));
        return Duration.ofSeconds(seconds);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(600L * attempt + ThreadLocalRandom.current().nextLong(250));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationToken.CancelledException();
        }
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Java 运行时缺少 SHA-1 支持", ex);
        }
    }
}
