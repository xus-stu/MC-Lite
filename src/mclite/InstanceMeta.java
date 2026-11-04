package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Per-instance user metadata (group and note) stored next to the game directory. */
final class InstanceMeta {
    record Entry(String group, String note) {
    }

    private InstanceMeta() {
    }

    private static Path file(Path gameDir) {
        return gameDir.resolve(".mclite").resolve("instance-meta.properties");
    }

    static Map<String, Entry> load(Path gameDir) {
        Map<String, Entry> result = new LinkedHashMap<>();
        Path file = file(gameDir);
        if (!Files.isRegularFile(file)) {
            return result;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException ex) {
            Log.warn("读取实例备注失败: " + ex.getMessage());
            return result;
        }
        for (String key : properties.stringPropertyNames()) {
            if (!key.endsWith(".note") && !key.endsWith(".group")) {
                continue;
            }
            String id = key.substring(0, key.lastIndexOf('.'));
            String group = properties.getProperty(id + ".group", "").trim();
            String note = properties.getProperty(id + ".note", "").trim();
            result.put(id, new Entry(group, note));
        }
        return result;
    }

    static void save(Path gameDir, Map<String, Entry> entries) {
        Path file = file(gameDir);
        try {
            Files.createDirectories(file.getParent());
            Properties properties = new Properties();
            for (Map.Entry<String, Entry> item : entries.entrySet()) {
                InstanceMeta.Entry entry = item.getValue();
                if (entry.group() != null && !entry.group().isBlank()) {
                    properties.setProperty(item.getKey() + ".group", entry.group());
                }
                if (entry.note() != null && !entry.note().isBlank()) {
                    properties.setProperty(item.getKey() + ".note", entry.note());
                }
            }
            Path temp = file.resolveSibling("instance-meta.properties.tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, "MC Lite instance metadata");
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Log.warn("保存实例备注失败: " + ex.getMessage());
        }
    }

    static void put(Path gameDir, String id, String group, String note) {
        Map<String, Entry> entries = load(gameDir);
        String cleanGroup = group == null ? "" : group.trim();
        String cleanNote = note == null ? "" : note.trim();
        if (cleanGroup.isEmpty() && cleanNote.isEmpty()) {
            entries.remove(id);
        } else {
            entries.put(id, new Entry(cleanGroup, cleanNote));
        }
        save(gameDir, entries);
    }

    static void remove(Path gameDir, String id) {
        Map<String, Entry> entries = load(gameDir);
        if (entries.remove(id) != null) {
            save(gameDir, entries);
        }
    }

    static void rename(Path gameDir, String oldId, String newId) {
        Map<String, Entry> entries = load(gameDir);
        Entry entry = entries.remove(oldId);
        if (entry != null) {
            entries.put(newId, entry);
            save(gameDir, entries);
        }
    }
}
