package mclite.terrain;

import mclite.Json;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 从实例 mods/*.jar 的语言文件里收集生物群系中文名。
 *
 * <p>模组会改地形/加群系，名字一般都在 {@code assets/<ns>/lang/zh_cn.json}，
 * key 形如 {@code biome.<ns>.<path>}。这样 terralith / BOP 之类的群系也能显示中文。</p>
 */
final class ModNames {

    private ModNames() {
    }

    static Map<String, String> load(Path instanceDir) {
        Map<String, String> out = new HashMap<>();
        if (instanceDir == null) {
            return out;
        }
        Path mods = instanceDir.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return out;
        }
        try (var stream = Files.list(mods)) {
            for (Path jar : stream.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                collect(jar, out);
            }
        } catch (Exception ignored) {
            // 读不到就用默认名。
        }
        return out;
    }

    private static void collect(Path jar, Map<String, String> out) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            // zh_cn 优先；先扫一遍 zh_cn，再补 en_us。
            scan(zip, out, "zh_cn", true);
            scan(zip, out, "en_us", false);
        } catch (Exception ignored) {
            // 坏 jar 跳过。
        }
    }

    private static void scan(ZipFile zip, Map<String, String> out, String lang, boolean override) {
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith("assets/") || !name.endsWith("/lang/" + lang + ".json")) {
                continue;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Object parsed = Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                Map<String, Object> map = Json.obj(parsed);
                if (map == null) {
                    continue;
                }
                for (Map.Entry<String, Object> item : map.entrySet()) {
                    String key = item.getKey();
                    if (!key.startsWith("biome.") || !(item.getValue() instanceof String text)) {
                        continue;
                    }
                    String rest = key.substring("biome.".length());
                    int dot = rest.indexOf('.');
                    if (dot <= 0 || dot >= rest.length() - 1) {
                        continue;
                    }
                    String id = rest.substring(0, dot) + ":" + rest.substring(dot + 1);
                    if (override || !out.containsKey(id)) {
                        out.put(id, text);
                    }
                }
            } catch (Exception ignored) {
                // 单个语言文件失败不影响其它。
            }
        }
    }
}
