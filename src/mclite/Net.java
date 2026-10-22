package mclite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.HexFormat;
import java.util.Set;
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

    private final HttpClient client;
    private final ProgressSink progress;
    private final CancellationToken token;

    public Net(ProgressSink progress, CancellationToken token) {
        this.progress = progress == null ? ProgressSink.NOOP : progress;
        this.token = token == null ? new CancellationToken() : token;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .version(HttpClient.Version.HTTP_2)
                .build();
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
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = response.body()) {
                    if (response.statusCode() != 200) {
                        throw new IOException("HTTP " + response.statusCode() + "：" + label);
                    }
                    checkFinalUri(response.uri());
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
                }
            } catch (CancellationToken.CancelledException ex) {
                throw ex;
            } catch (SecurityException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CancellationToken.CancelledException();
            } catch (IOException ex) {
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

        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            token.check();
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            try {
                Files.createDirectories(target.getParent());
                progress.stage(label);
                HttpRequest request = buildRequest(url);
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = response.body()) {
                    if (response.statusCode() != 200) {
                        throw new IOException("HTTP " + response.statusCode() + "：" + label);
                    }
                    checkFinalUri(response.uri());
                    Files.deleteIfExists(temp);
                    MessageDigest digest = sha1();
                    long written = 0;
                    try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            token.check();
                            channel.write(ByteBuffer.wrap(buffer, 0, read));
                            digest.update(buffer, 0, read);
                            written += read;
                            progress.add(read);
                        }
                    }
                    verifyDigest(null, digest.digest(), expectedSha1, expectedSize, label, true);
                    if (expectedSize >= 0 && written != expectedSize) {
                        throw new IOException("文件大小不匹配：" + label);
                    }
                    moveIntoPlace(temp, target);
                    return;
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
                last = ex;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
            }
            sleepBeforeRetry(attempt);
        }
        throw last == null ? new IOException("下载失败：" + label) : last;
    }

    public void downloadRacing(String officialUrl, String mirrorUrl, Path target,
                               String expectedSha1, long expectedSize, String label,
                               boolean verifyExistingSha1) throws IOException {
        if (mirrorUrl == null || mirrorUrl.isBlank()) {
            download(officialUrl, target, expectedSha1, expectedSize, label, verifyExistingSha1);
            return;
        }
        token.check();
        progress.stage(label + "（官方 / 镜像竞速）");
        CancellationToken officialToken = new CancellationToken();
        CancellationToken mirrorToken = new CancellationToken();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2,
                runnable -> {
                    Thread thread = new Thread(runnable, "mclite-race");
                    thread.setDaemon(true);
                    return thread;
                });
        Path officialTarget = target.resolveSibling(target.getFileName() + ".race-official");
        Path mirrorTarget = target.resolveSibling(target.getFileName() + ".race-mirror");
        java.util.concurrent.CompletionService<Path> completion =
                new java.util.concurrent.ExecutorCompletionService<>(pool);
        java.util.List<java.util.concurrent.Future<Path>> futures = new java.util.ArrayList<>();
        try {
            futures.add(completion.submit(() -> {
                new Net(ProgressSink.NOOP, officialToken).download(officialUrl, officialTarget,
                        expectedSha1, expectedSize, label + " · 官方", false);
                return officialTarget;
            }));
            futures.add(completion.submit(() -> {
                new Net(ProgressSink.NOOP, mirrorToken).download(mirrorUrl, mirrorTarget,
                        expectedSha1, expectedSize, label + " · 镜像", false);
                return mirrorTarget;
            }));
            IOException last = null;
            for (int i = 0; i < 2; i++) {
                java.util.concurrent.Future<Path> done = completion.take();
                try {
                    Path winner = done.get();
                    if (officialTarget.equals(winner)) {
                        mirrorToken.cancel();
                    } else {
                        officialToken.cancel();
                    }
                    moveIntoPlace(winner, target);
                    progress.add(Math.max(0, expectedSize));
                    return;
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    last = cause instanceof IOException io ? io : new IOException(
                            cause == null ? "竞速下载失败" : cause.getMessage(), cause);
                }
            }
            throw last == null ? new IOException("官方和镜像下载均失败：" + label) : last;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            officialToken.cancel();
            mirrorToken.cancel();
            throw new CancellationToken.CancelledException();
        } finally {
            pool.shutdownNow();
            deleteQuietly(officialTarget);
            deleteQuietly(mirrorTarget);
            deleteQuietly(officialTarget.resolveSibling(officialTarget.getFileName() + ".part"));
            deleteQuietly(mirrorTarget.resolveSibling(mirrorTarget.getFileName() + ".part"));
        }
    }

    public boolean mirrorFaster(String officialUrl, String mirrorUrl) {
        if (mirrorUrl == null || mirrorUrl.isBlank()) {
            return false;
        }
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2,
                runnable -> {
                    Thread thread = new Thread(runnable, "mclite-probe");
                    thread.setDaemon(true);
                    return thread;
                });
        try {
            java.util.concurrent.CompletionService<Integer> completion =
                    new java.util.concurrent.ExecutorCompletionService<>(pool);
            java.util.List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
            futures.add(completion.submit(() -> {
                probe(officialUrl);
                return 0;
            }));
            futures.add(completion.submit(() -> {
                probe(mirrorUrl);
                return 1;
            }));
            for (int i = 0; i < 2; i++) {
                java.util.concurrent.Future<Integer> done = completion.take();
                try {
                    int winner = done.get();
                    for (java.util.concurrent.Future<Integer> future : futures) {
                        if (future != done) {
                            future.cancel(true);
                        }
                    }
                    return winner == 1;
                } catch (java.util.concurrent.ExecutionException ignored) {

                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return false;
    }

    private void probe(String url) throws IOException {
        URI uri = normalizeAndValidate(url);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=0-0")
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            checkFinalUri(response.uri());
            if (response.statusCode() >= 400) {
                throw new IOException("探测失败 HTTP " + response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationToken.CancelledException();
        }
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
        URI uri = normalizeAndValidate(url);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
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

    private void checkFinalUri(URI uri) {
        if (uri == null || uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())
                || !ALLOWED_HOSTS.contains(uri.getHost().toLowerCase())
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new SecurityException("下载被重定向到非官方地址，已拒绝");
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
