package mclite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 启动器扫描到的一个已安装实例，以及它使用的加载器。 */
record InstalledInstance(String id, String mcVersion, String loader, Path versionDir) {

    static final String LOADER_FABRIC = "fabric";
    static final String LOADER_FORGE = "forge";
    static final String LOADER_NEOFORGE = "neoforge";
    static final String LOADER_UNKNOWN = "unknown";

    static List<InstalledInstance> scan(GamePaths paths) {
        List<InstalledInstance> result = new ArrayList<>();
        Path versions = paths.versionsDir();
        if (!Files.isDirectory(versions)) {
            return result;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(versions)) {
            for (Path dir : dirs) {
                Path json = dir.resolve(dir.getFileName() + ".json");
                if (!Files.isDirectory(dir) || !Files.isRegularFile(json)) {
                    continue;
                }
                InstalledInstance instance = read(dir.getFileName().toString(), dir, json);
                if (instance != null) {
                    result.add(instance);
                }
            }
        } catch (IOException ignored) {
            // 扫描失败时返回已找到的
        }
        result.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        return result;
    }

    static InstalledInstance read(String id, Path dir, Path jsonFile) {
        try {
            Map<String, Object> root = Json.obj(Json.parse(Files.readString(jsonFile)));
            String inherited = Json.str(root, "inheritsFrom", "").trim();
            String mcVersion = inherited.isEmpty() ? id : inherited;
            if (!looksLikeMinecraftVersion(mcVersion)) {
                mcVersion = id;
            }
            String loader = detectLoader(root);
            return new InstalledInstance(id, mcVersion, loader, dir);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String detectLoader(Map<String, Object> root) {
        for (Object item : Json.arr(root.get("libraries"))) {
            Map<String, Object> library = Json.obj(item);
            String name = Json.str(library, "name", "").toLowerCase(Locale.ROOT);
            if (name.contains("fabric-loader")) {
                return LOADER_FABRIC;
            }
            if (name.contains("net.neoforged:neoforge")) {
                return LOADER_NEOFORGE;
            }
            if (name.contains("net.minecraftforge:forge")) {
                return LOADER_FORGE;
            }
        }
        return LOADER_UNKNOWN;
    }

    private static boolean looksLikeMinecraftVersion(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '.' && c != '-' && c != '_' && !Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return Character.isDigit(value.charAt(0));
    }

    String displayName() {
        return id + "  ·  MC " + mcVersion + "  ·  " + loader;
    }
}
