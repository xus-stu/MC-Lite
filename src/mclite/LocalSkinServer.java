package mclite;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LocalSkinServer implements AutoCloseable {
    private final HttpServer server;
    private final byte[] skin;
    private final String skinHash;
    private final String username;
    private final String compactUuid;
    private final String root;
    private final KeyPair keys;

    private LocalSkinServer(byte[] skin, String username, UUID uuid) throws Exception {
        this.skin = skin;
        this.username = username;
        this.compactUuid = uuid.toString().replace("-", "");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        this.keys = generator.generateKeyPair();
        this.skinHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(skin));
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.root = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public static LocalSkinServer start(Config config, String accountId, String username) {
        Path file = SkinStore.skinFile(config, accountId);
        if (file == null || !Files.isRegularFile(file) || username == null || username.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username)
                    .getBytes(StandardCharsets.UTF_8));
            return new LocalSkinServer(Files.readAllBytes(file), username, uuid);
        } catch (Exception ex) {
            Log.warn("无法启动本地皮肤服务: " + ex.getMessage());
            return null;
        }
    }

    public List<String> jvmArguments(Path authlib) {
        return List.of("-javaagent:" + authlib + "=" + root,
                "-Dauthlibinjector.side=client");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                json(exchange, metadata());
            } else if (path.startsWith("/sessionserver/session/minecraft/profile/")) {
                json(exchange, profile());
            } else if (path.startsWith("/sessionserver/session/minecraft/hasJoined")) {
                json(exchange, profile());
            } else if (path.startsWith("/textures/")) {
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.getResponseHeaders().set("Cache-Control", "max-age=300");
                exchange.sendResponseHeaders(200, skin.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(skin);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception ex) {
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {
            }
        }
    }

    private Map<String, Object> metadata() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("signaturePublickey", pem(keys.getPublic().getEncoded()));
        root.put("skinDomains", List.of("127.0.0.1", "localhost"));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("serverName", "MC Lite");
        meta.put("implementationName", "MC Lite");
        meta.put("implementationVersion", Version.VALUE);
        meta.put("feature.non_email_login", true);
        root.put("meta", meta);
        return root;
    }

    private Map<String, Object> profile() {
        Map<String, Object> textures = new LinkedHashMap<>();
        Map<String, Object> skinEntry = new LinkedHashMap<>();
        skinEntry.put("url", root + "/textures/" + skinHash);
        textures.put("SKIN", skinEntry);
        Map<String, Object> texturePayload = new LinkedHashMap<>();
        texturePayload.put("timestamp", System.currentTimeMillis());
        texturePayload.put("profileId", compactUuid);
        texturePayload.put("profileName", username);
        texturePayload.put("textures", textures);
        String value = Base64.getEncoder().encodeToString(
                SkinStore.toJsonBytes(texturePayload));
        String signature;
        try {
            Signature signer = Signature.getInstance("SHA1withRSA");
            signer.initSign(keys.getPrivate());
            signer.update(value.getBytes(StandardCharsets.UTF_8));
            signature = Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception ex) {
            signature = "";
        }
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", "textures");
        property.put("value", value);
        property.put("signature", signature);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", compactUuid);
        result.put("name", username);
        result.put("properties", List.of(property));
        return result;
    }

    private void json(HttpExchange exchange, Object value) throws IOException {
        byte[] bytes = SkinStore.toJsonBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String pem(byte[] encoded) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END PUBLIC KEY-----";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
