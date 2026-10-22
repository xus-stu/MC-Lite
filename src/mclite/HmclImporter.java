package mclite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HmclImporter {
    public record Entry(String name, Path path) {
        @Override
        public String toString() {
            String display = name == null || name.isBlank() || name.matches("\\?+")
                    ? path.getFileName() == null ? path.toString()
                    : path.getFileName().toString() : name;
            return display + "    " + path;
        }
    }

    private HmclImporter() {
    }

    public static List<Entry> detect() {
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Path home : homes()) {
            Path config = home.resolve("config");
            read(config.resolve("game-directories.json"), home, result, seen);
            read(config.resolve("user-game-directories.json"), home, result, seen);
            read(home.resolve("game-directories.json"), home, result, seen);
            readLegacy(home.resolve("config.json"), home, result, seen);
        }
        return result;
    }

    private static List<Path> homes() {
        List<Path> homes = new ArrayList<>();
        Path user = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            homes.add(user.resolve("Library/Application Support/hmcl"));
            homes.add(user.resolve("Library/Application Support/HMCL"));
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                homes.add(Path.of(appData).resolve("hmcl"));
                homes.add(Path.of(appData).resolve("HMCL"));
            }
            homes.add(user.resolve(".hmcl"));
        } else {
            homes.add(user.resolve(".local/share/hmcl"));
            homes.add(user.resolve(".hmcl"));
        }
        homes.add(Path.of(System.getProperty("user.dir")).resolve(".hmcl"));
        return homes;
    }

    private static void read(Path file, Path home, List<Entry> result, Set<String> seen) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Object root = Json.parse(Files.readString(file));
            Object array = root;
            if (root instanceof Map<?, ?> map) {
                array = map.get("directories");
            }
            for (Object raw : Json.arr(array)) {
                if (!(raw instanceof Map<?, ?> item)) {
                    continue;
                }
                Object pathValue = item.get("path");
                if (!(pathValue instanceof String text) || text.isBlank()) {
                    continue;
                }
                Path path = Path.of(text);
                if (!path.isAbsolute()) {
                    path = home.resolve(path);
                }
                path = path.toAbsolutePath().normalize();
                if (!Files.isDirectory(path) || !seen.add(path.toString())) {
                    continue;
                }
                String name = name(item.get("name"));
                result.add(new Entry(name, path));
            }
        } catch (Exception ex) {
            Log.warn("读取 HMCL 游戏目录失败 " + file + ": " + ex.getMessage());
        }
    }

    private static void readLegacy(Path file, Path home, List<Entry> result, Set<String> seen) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Object root = Json.parse(Files.readString(file));
            if (!(root instanceof Map<?, ?> map)) {
                return;
            }
            Object value = map.get("gameDir");
            if (!(value instanceof String text) || text.isBlank()) {
                return;
            }
            Path path = Path.of(text);
            if (!path.isAbsolute()) {
                path = home.resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            if (Files.isDirectory(path) && seen.add(path.toString())) {
                result.add(new Entry("HMCL 游戏目录", path));
            }
        } catch (Exception ignored) {
        }
    }

    private static String name(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (item instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }
}
