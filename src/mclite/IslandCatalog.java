package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MCLite 岛（本地音乐模组）的可选组件清单。
 *
 * <p>清单是启动器 release 的一个资产，启动器只在用户点「安装」时才联网；
 * mod 本体不随启动器分发。
 */
final class IslandCatalog {

    /**
     * 固定的 island-latest release：模组更新只覆盖这里的资产，不再需要发启动器版本。
     * 旧的 releases/latest 作为过渡期回退，老启动器仍然能读到清单。
     */
    static final String MANIFEST_URL =
            "https://github.com/xus-stu/MC-Lite/releases/download/island-latest/island-manifest.json";
    static final String LEGACY_MANIFEST_URL =
            "https://github.com/xus-stu/MC-Lite/releases/latest/download/island-manifest.json";

    private IslandCatalog() {
    }

    record Build(String version, String mc, String loader, int java,
                 String url, String sha256, long size, String notes) {

        String displayName() {
            return "MC " + mc + " · " + loader + " · v" + version
                    + " · " + (size > 0 ? (size / 1024) + " KB" : "未知大小");
        }
    }

    record Manifest(List<Build> builds) {
        static final Manifest EMPTY = new Manifest(List.of());
    }

    static Manifest fetch(Net net) throws IOException {
        List<String> urls = new ArrayList<>();
        urls.addAll(GithubSources.candidates(MANIFEST_URL));
        urls.addAll(GithubSources.candidates(LEGACY_MANIFEST_URL));
        // 清单会被 release 覆盖，镜像也可能缓存旧版本；加时间戳强制回源。
        String bust = "?ts=" + System.currentTimeMillis();
        for (int i = 0; i < urls.size(); i++) {
            urls.set(i, urls.get(i) + bust);
        }
        // 8 个镜像并行竞速，谁先返回完整清单用谁；不再逐个等超时。
        byte[] bytes = net.getFirstBytes(urls, "MCLite 岛组件清单", 8);
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    static Manifest parse(String json) throws IOException {
        try {
            Map<String, Object> root = Json.obj(Json.parse(json));
            int schema = Json.integer(root, "schema", 0);
            if (schema != 1) {
                throw new IOException("不支持的组件清单版本: " + schema);
            }
            List<Build> builds = new ArrayList<>();
            for (Object item : Json.arr(root.get("builds"))) {
                Map<String, Object> map = Json.obj(item);
                String version = Json.str(map, "version", "").trim();
                String mc = Json.str(map, "mc", "").trim();
                String loader = Json.str(map, "loader", "").trim().toLowerCase(Locale.ROOT);
                String url = Json.str(map, "url", "").trim();
                String sha256 = Json.str(map, "sha256", "").trim().toLowerCase(Locale.ROOT);
                long size = Json.lng(map, "size", -1L);
                int java = Json.integer(map, "java", 0);
                if (version.isEmpty() || mc.isEmpty() || loader.isEmpty()
                        || !url.startsWith("https://github.com/xus-stu/MC-Lite/releases/download/")
                        || !sha256.matches("[0-9a-f]{64}")
                        || size <= 0) {
                    continue;
                }
                builds.add(new Build(version, mc, loader, java, url, sha256, size,
                        Json.str(map, "notes", "")));
            }
            return new Manifest(List.copyOf(builds));
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("组件清单格式错误: " + ex.getMessage(), ex);
        }
    }

    static List<Build> compatible(Manifest manifest, InstalledInstance instance) {
        List<Build> result = new ArrayList<>();
        if (manifest == null || instance == null) {
            return result;
        }
        for (Build build : manifest.builds()) {
            if (build.mc().equals(instance.mcVersion())
                    && build.loader().equals(instance.loader())) {
                result.add(build);
            }
        }
        return result;
    }

    static boolean hasCompatibleBuild(Manifest manifest, InstalledInstance instance) {
        return !compatible(manifest, instance).isEmpty();
    }
}
