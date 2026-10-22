package mclite;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Mirror {
    public static final String BASE = "https://bmclapi2.bangbang93.com";

    private Mirror() {
    }

    public static String versionJson(String id) {
        return BASE + "/version/" + encode(id) + "/json";
    }

    public static String client(String id) {
        return BASE + "/version/" + encode(id) + "/client";
    }

    public static String library(String path) {
        return BASE + "/maven/" + path;
    }

    public static String asset(String hash) {
        return BASE + "/assets/" + hash;
    }

    public static String manifest() {
        return BASE + "/mc/game/version_manifest_v2.json";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
