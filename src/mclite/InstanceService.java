package mclite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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

    /** Copies an instance folder and rewrites its id references for the new name. */
    public static void copy(Path root, String sourceId, String targetId) throws IOException {
        Path source = root.resolve(sourceId);
        Path target = root.resolve(targetId);
        if (!Files.isDirectory(source)) {
            throw new IOException("实例不存在: " + sourceId);
        }
        if (Files.exists(target)) {
            throw new IOException("实例已存在: " + targetId);
        }
        copyTree(source, target);
        renameVersionJson(target, sourceId, targetId, false);
        renameVersionJson(target, sourceId, targetId, true);
        Path oldJar = target.resolve(sourceId + ".jar");
        if (Files.isRegularFile(oldJar)) {
            Files.move(oldJar, target.resolve(targetId + ".jar"));
        }
        Path json = target.resolve(targetId + ".json");
        if (Files.isRegularFile(json)) {
            rewriteVersionJson(json, sourceId, targetId);
        }
        Path vanilla = target.resolve(targetId + ".vanilla.json");
        if (Files.isRegularFile(vanilla)) {
            rewriteVersionJson(vanilla, sourceId, targetId);
        }
        updateMarker(target, sourceId, targetId);
    }

    /** Exports one instance as a portable ZIP: {@code <id>/<id>.json + game files}. */
    public static void exportZip(Path root, String id, Path target) throws IOException {
        Path dir = root.resolve(id);
        if (!Files.isDirectory(dir)) {
            throw new IOException("实例不存在: " + id);
        }
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            putEntry(zip, "mclite-instance.properties",
                    ("id=" + id + "\nexported=" + System.currentTimeMillis() + "\n")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (var walk = Files.walk(dir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String relative = dir.relativize(file).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(id + "/" + relative));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    /** Imports an instance ZIP (zip-slip safe) and returns the instance id. */
    public static String importZip(Path root, Path archive, String preferredId) throws IOException {
        Path temp = Files.createTempDirectory("mclite-instance-");
        try {
            unzip(archive, temp);
            Path source = findInstanceDir(temp);
            String oldId = source.getFileName().toString();
            String id = preferredId == null || preferredId.isBlank() ? oldId : preferredId.trim();
            if (!id.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IOException("实例名称只能包含字母、数字、点、下划线和短横线");
            }
            Path target = root.resolve(id);
            if (Files.exists(target)) {
                throw new IOException("实例已存在: " + id);
            }
            copyTree(source, target);
            if (!oldId.equals(id)) {
                renameVersionJson(target, oldId, id, false);
                renameVersionJson(target, oldId, id, true);
                Path oldJar = target.resolve(oldId + ".jar");
                if (Files.isRegularFile(oldJar)) {
                    Files.move(oldJar, target.resolve(id + ".jar"));
                }
                Path json = target.resolve(id + ".json");
                if (Files.isRegularFile(json)) {
                    rewriteVersionJson(json, oldId, id);
                }
                Path vanilla = target.resolve(id + ".vanilla.json");
                if (Files.isRegularFile(vanilla)) {
                    rewriteVersionJson(vanilla, oldId, id);
                }
                updateMarker(target, oldId, id);
            }
            return id;
        } finally {
            deleteTree(temp);
        }
    }

    private static Path findInstanceDir(Path temp) throws IOException {
        try (var list = Files.list(temp)) {
            for (Path child : list.sorted().toList()) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (Files.isRegularFile(child.resolve(name + ".json"))) {
                    return child;
                }
            }
        }
        throw new IOException("ZIP 里没有找到实例文件夹（需要 <名称>/<名称>.json）");
    }

    private static void unzip(Path archive, Path target) throws IOException {
        Path base = target.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = base.resolve(entry.getName()).normalize();
                if (!out.startsWith(base)) {
                    throw new IOException("压缩包包含非法路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path out = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(path, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
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
