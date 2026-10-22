package mclite;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.TimeZone;

public final class RegionGate {
    private static boolean restricted;
    private static boolean locked = RegionGate.class.getResource("/mclite/region.lock") != null;
    private static String mode = "";
    private static String source = "";

    private RegionGate() {
    }

    public static void lock() {
        locked = true;
    }

    public static boolean locked() {
        return locked;
    }

    public static void configure(String[] args) {
        String requested = null;
        for (String arg : args) {
            if (arg.startsWith("--region=")) {
                requested = arg.substring("--region=".length());
            } else if ("--region".equals(arg)) {
                requested = "auto";
            }
        }
        if (locked) {
            requested = "auto";
        }
        if (requested == null) {
            restricted = false;
            mode = "";
            source = "";
            return;
        }
        mode = requested.toLowerCase(Locale.ROOT);
        if ("auto".equals(mode)) {
            String network = networkCountry();
            if (network != null) {
                restricted = "CN".equalsIgnoreCase(network);
                source = "network";
            } else {
                restricted = isMainlandChinaBySystem();
                source = "system";
            }
        } else {
            restricted = switch (mode) {
                case "cn", "china", "mainland" -> true;
                default -> false;
            };
            source = "argument";
        }
    }

    public static boolean restricted() {
        return restricted;
    }

    public static String mode() {
        return mode;
    }

    public static String source() {
        return source;
    }

    private static String networkCountry() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(1200))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://www.cloudflare.com/cdn-cgi/trace"))
                    .timeout(Duration.ofMillis(1500))
                    .header("User-Agent", "MCLite/1.0")
                    .GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            for (String line : response.body().split("\\R")) {
                if (line.startsWith("loc=")) {
                    String value = line.substring(4).trim();
                    return value.isEmpty() ? null : value.toUpperCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isMainlandChinaBySystem() {
        String country = Locale.getDefault().getCountry();
        if ("CN".equalsIgnoreCase(country)) {
            return true;
        }
        String zone = TimeZone.getDefault().getID();
        return "Asia/Shanghai".equals(zone)
                || "Asia/Chongqing".equals(zone)
                || "Asia/Harbin".equals(zone)
                || "Asia/Urumqi".equals(zone)
                || "PRC".equals(zone);
    }

    public static void requireGameDownloadAllowed() throws java.io.IOException {
        if (restricted) {
            throw new java.io.IOException(
                    "为遵守相关法律法规，本地区不提供游戏本体和整合包下载服务。");
        }
    }

    public static void showGuide(Component parent) {
        SwingUtilities.invokeLater(() -> new RegionDialog(parent).setVisible(true));
    }
}
