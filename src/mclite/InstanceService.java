package mclite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/** File-level instance operations, kept out of the Swing frame. */
public final class InstanceService {
    private InstanceService() {
    }

    public static String detectLoader(String versionId, Path dir, VersionMeta meta) {
        Properties properties = new Properties();
        Path marker = dir.resolve(".mclite-loader.properties");
        if (Files.isRegularFile(marker)) {
            try (var in = Files.newInputStream(marker)) {
                properties.load(in);
            } catch (IOException ignored) {
            }
        }
        String loader = properties.getProperty("loader", "").toLowerCase(java.util.Locale.ROOT);
        String id = versionId.toLowerCase(java.util.Locale.ROOT);
        String main = meta.mainClass() == null ? ""
                : meta.mainClass().toLowerCase(java.util.Locale.ROOT);
        String combined = loader + " " + id + " " + main;
        if (combined.contains("neoforge") || combined.contains("neoforged")) {
            return "neoforge";
        }
        if (combined.contains("fabric")) {
            return "fabric";
        }
        if (combined.contains("quilt")) {
            return "quilt";
        }
        if (combined.contains("forge") || combined.contains("bootstraplauncher")) {
            return "forge";
        }
        return null;
    }

    public static String detectBaseVersion(String versionId, Path dir, Path json) {
        Path marker = dir.resolve(".mclite-loader.properties");
        if (Files.isRegularFile(marker)) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(marker)) {
                properties.load(in);
            } catch (IOException ignored) {
            }
            String base = properties.getProperty("base", "").trim();
            if (!base.isEmpty()) {
                return base;
            }
        }
        try {
            Map<String, Object> root = Json.obj(Json.parse(Files.readString(json)));
            String parent = Json.str(root, "inheritsFrom", "").trim();
            if (!parent.isEmpty()) {
                return parent;
            }
        } catch (Exception ignored) {
        }
        return versionId;
    }

    public static String inheritedParent(Path json) {
        if (json == null || !Files.isRegularFile(json)) {
            return null;
        }
        try {
            Map<String, Object> root = Json.obj(Json.parse(Files.readString(json)));
            String parent = Json.str(root, "inheritsFrom", "").trim();
            return parent.isEmpty() ? null : parent;
        } catch (Exception ex) {
            return null;
        }
    }

    public static void rename(Path root, String oldId, String newId, Path oldDir, Path newDir)
            throws IOException {
        Files.move(oldDir, newDir);
        renameVersionJson(newDir, oldId, newId, false);
        renameVersionJson(newDir, oldId, newId, true);
        Path oldJar = newDir.resolve(oldId + ".jar");
        Path newJar = newDir.resolve(newId + ".jar");
        if (Files.isRegularFile(oldJar)) {
            Files.move(oldJar, newJar);
        }
        Path json = newDir.resolve(newId + ".json");
        if (Files.isRegularFile(json)) {
            rewriteVersionJson(json, oldId, newId);
        }
        Path vanilla = newDir.resolve(newId + ".vanilla.json");
        if (Files.isRegularFile(vanilla)) {
            rewriteVersionJson(vanilla, oldId, newId);
        }
        updateMarker(newDir, oldId, newId);
        updateInheritedVersions(root, oldId, newId);
    }

    private static void renameVersionJson(Path dir, String oldId, String newId, boolean vanilla)
            throws IOException {
        String suffix = vanilla ? ".vanilla.json" : ".json";
        Path oldFile = dir.resolve(oldId + suffix);
        if (!Files.isRegularFile(oldFile)) {
            return;
        }
        Path newFile = dir.resolve(newId + suffix);
        rewriteVersionJson(oldFile, oldId, newId);
        Files.move(oldFile, newFile);
    }

    private static void rewriteVersionJson(Path file, String oldId, String newId) throws IOException {
        Map<String, Object> root = Json.obj(Json.parse(Files.readString(file)));
        boolean changed = false;
        for (String key : new String[]{"id", "jar", "inheritsFrom"}) {
            if (oldId.equals(Json.str(root, key))) {
                root.put(key, newId);
                changed = true;
            }
        }
        if (changed) {
            Files.writeString(file, LoaderInstaller.prettyJson(root));
        }
    }

    private static void updateMarker(Path dir, String oldId, String newId) throws IOException {
        Path marker = dir.resolve(".mclite.properties");
        if (Files.isRegularFile(marker)) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(marker)) {
                properties.load(in);
            }
            if (oldId.equals(properties.getProperty("versionId"))) {
                properties.setProperty("versionId", newId);
                try (var out = Files.newOutputStream(marker)) {
                    properties.store(out, "MC Lite instance");
                }
            }
        }
        Path loader = dir.resolve(".mclite-loader.properties");
        if (Files.isRegularFile(loader)) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(loader)) {
                properties.load(in);
            }
            if (oldId.equals(properties.getProperty("base"))) {
                properties.setProperty("base", newId);
                try (var out = Files.newOutputStream(loader)) {
                    properties.store(out, "MC Lite loader");
                }
            }
        }
    }

    private static void updateInheritedVersions(Path root, String oldId, String newId) {
        try (var dirs = Files.list(root.resolve("versions"))) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                String id = dir.getFileName().toString();
                Path json = dir.resolve(id + ".json");
                if (!Files.isRegularFile(json)) {
                    continue;
                }
                try {
                    Map<String, Object> child = Json.obj(Json.parse(Files.readString(json)));
                    boolean changed = false;
                    if (oldId.equals(Json.str(child, "inheritsFrom"))) {
                        child.put("inheritsFrom", newId);
                        changed = true;
                    }
                    if (oldId.equals(Json.str(child, "jar"))) {
                        child.put("jar", newId);
                        changed = true;
                    }
                    if (changed) {
                        Files.writeString(json, LoaderInstaller.prettyJson(child));
                    }
                } catch (Exception ex) {
                    Log.warn("更新继承版本失败 " + json + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            Log.warn("扫描继承版本失败: " + ex.getMessage());
        }
    }

    public static void deleteTree(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
