package mclite;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class Version {
    public static final String VALUE = load();

    private Version() {
    }

    private static String load() {
        try (InputStream in = Version.class.getResourceAsStream("/mclite/version.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                String value = properties.getProperty("version", "").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception ignored) {
        }
        return "1.0.3";
    }
}
