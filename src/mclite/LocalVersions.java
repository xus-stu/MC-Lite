package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LocalVersions {
    private LocalVersions() {
    }

    public static List<VersionManifest.VersionEntry> scan(GamePaths paths) {
        List<VersionManifest.VersionEntry> result = new ArrayList<>();
        Path versions = paths.versionsDir();
        if (!Files.isDirectory(versions)) {
            return result;
        }
        try (var dirs = Files.newDirectoryStream(versions)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                String id = dir.getFileName().toString();
                Path json = dir.resolve(id + ".json");
                if (!Files.isRegularFile(json)) {
                    continue;
                }
                try {
                    Map<String, Object> root = Json.obj(Json.parse(Files.readString(json, StandardCharsets.UTF_8)));
                    result.add(new VersionManifest.VersionEntry(id, "local", null, null,
                            Json.str(root, "releaseTime", ""), 0, true));
                } catch (IOException | RuntimeException ex) {
                    Log.warn("跳过无法读取的本地版本 " + id + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            Log.warn("扫描本地版本失败: " + ex.getMessage());
        }
        result.sort((a, b) -> b.id().compareToIgnoreCase(a.id()));
        return result;
    }

    public static VersionMeta load(GamePaths paths, String id) throws IOException {
        Merge merge = merge(paths, id, new HashSet<>());
        return VersionMeta.parseMap(merge.root, id, merge.jarId);
    }

    private record Merge(Map<String, Object> root, String jarId) {
    }

    @SuppressWarnings("unchecked")
    private static Merge merge(GamePaths paths, String id, Set<String> seen) throws IOException {
        if (!seen.add(id)) {
            throw new IOException("版本继承出现循环: " + id);
        }
        Path file = paths.versionJson(id);
        if (!Files.isRegularFile(file)) {
            throw new IOException("找不到本地版本 JSON: " + file);
        }
        Map<String, Object> child = Json.obj(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
        String parentId = Json.str(child, "inheritsFrom");
        String jarId = Json.str(child, "jar", id);
        Map<String, Object> root;
        if (parentId != null && !parentId.isBlank()) {
            Merge parent = merge(paths, parentId, seen);
            root = new LinkedHashMap<>(parent.root);
            jarId = Json.str(child, "jar", parent.jarId);
            root.put("libraries", mergeLibraries(Json.arr(root.get("libraries")),
                    Json.arr(child.get("libraries"))));
            Map<String, Object> args = new LinkedHashMap<>(Json.obj(root.get("arguments")));
            Map<String, Object> childArgs = Json.obj(child.get("arguments"));
            args.put("jvm", mergeList(Json.arr(args.get("jvm")), Json.arr(childArgs.get("jvm"))));
            args.put("game", mergeList(Json.arr(args.get("game")), Json.arr(childArgs.get("game"))));
            if (!args.isEmpty()) {
                root.put("arguments", args);
            }
            Map<String, Object> downloads = new LinkedHashMap<>(Json.obj(root.get("downloads")));
            downloads.putAll(Json.obj(child.get("downloads")));
            root.put("downloads", downloads);
            for (Map.Entry<String, Object> entry : child.entrySet()) {
                String key = entry.getKey();
                if (key.equals("libraries") || key.equals("arguments") || key.equals("downloads")
                        || key.equals("inheritsFrom") || key.equals("id") || key.equals("jar")) {
                    continue;
                }
                root.put(key, entry.getValue());
            }
        } else {
            root = new LinkedHashMap<>(child);
        }
        root.put("id", id);
        if (jarId != null && !jarId.isBlank()) {
            root.put("jar", jarId);
        }
        return new Merge(root, jarId == null || jarId.isBlank() ? id : jarId);
    }

    private static List<Object> mergeList(List<Object> parent, List<Object> child) {
        List<Object> result = new ArrayList<>(parent);
        result.addAll(child);
        return result;
    }

    private static List<Object> mergeLibraries(List<Object> parent, List<Object> child) {
        LinkedHashMap<String, Object> byName = new LinkedHashMap<>();
        for (Object item : parent) {
            Map<String, Object> map = Json.obj(item);
            String name = Json.str(map, "name", "anonymous-" + byName.size());
            byName.put(name, item);
        }
        for (Object item : child) {
            Map<String, Object> map = Json.obj(item);
            String name = Json.str(map, "name", "anonymous-" + byName.size());
            byName.put(name, item);
        }
        return new ArrayList<>(byName.values());
    }
}
