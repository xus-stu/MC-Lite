package mclite;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Modrinth {
    private static final String API = "https://api.modrinth.com/v2";

    private final Net net;

    public Modrinth(ProgressSink progress, CancellationToken token) {
        this.net = new Net(progress, token);
    }

    public record Project(String id, String slug, String title, String description,
                          String author, long downloads, String iconUrl, String projectType,
                          String summaryZh, String mcmodUrl, String source) {
        public Project(String id, String slug, String title, String description,
                       String author, long downloads, String iconUrl, String projectType) {
            this(id, slug, title, description, author, downloads, iconUrl, projectType,
                    null, null, "modrinth");
        }

        public Project withMcmod(String summaryZh, String mcmodUrl) {
            return new Project(id, slug, title, description, author, downloads, iconUrl,
                    projectType, summaryZh, mcmodUrl, source);
        }

        public boolean curseForge() {
            return "curseforge".equals(source);
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public record Dependency(String versionId, String projectId, String fileName, String type) {
        public boolean required() {
            return "required".equals(type);
        }
    }

    public record File(String url, String filename, String sha1, String sha512, long size) {
    }

    public record Version(String id, String projectId, String name, String versionNumber,
                          List<String> gameVersions, List<String> loaders,
                          List<Dependency> dependencies, List<File> files, String source) {
        public Version(String id, String projectId, String name, String versionNumber,
                       List<String> gameVersions, List<String> loaders,
                       List<Dependency> dependencies, List<File> files) {
            this(id, projectId, name, versionNumber, gameVersions, loaders, dependencies,
                    files, "modrinth");
        }

        public File primaryFile() {
            return files.isEmpty() ? null : files.get(0);
        }

        public boolean curseForge() {
            return "curseforge".equals(source);
        }
    }

    public List<Project> search(String query, String projectType, String gameVersion,
                                String loader) throws IOException {
        return search(query, projectType, gameVersion, loader, 0, 20);
    }

    public List<Project> search(String query, String projectType, String gameVersion,
                                String loader, int offset, int limit) throws IOException {
        StringBuilder facets = new StringBuilder("[[\"project_type:");
        facets.append(projectType).append("\"]");
        if (gameVersion != null && !gameVersion.isBlank()) {
            facets.append(",[\"versions:").append(gameVersion).append("\"]");
        }
        if (loader != null && !loader.isBlank() && !"any".equals(loader)) {
            facets.append(",[\"categories:").append(loader).append("\"]");
        }
        facets.append(']');
        String url = API + "/search?limit=" + Math.max(1, limit)
                + "&offset=" + Math.max(0, offset) + "&index=relevance&query="
                + URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8)
                + "&facets=" + URLEncoder.encode(facets.toString(), StandardCharsets.UTF_8);
        Map<String, Object> root = Json.obj(Json.parse(new String(
                net.getUnverifiedBytes(url, "Modrinth 搜索"), StandardCharsets.UTF_8)));
        List<Project> results = new ArrayList<>();
        for (Object raw : Json.arr(root.get("hits"))) {
            Map<String, Object> hit = Json.obj(raw);
            String id = Json.str(hit, "project_id");
            String title = Json.str(hit, "title");
            if (id == null || title == null) {
                continue;
            }
            results.add(new Project(id,
                    Json.str(hit, "slug", id),
                    title,
                    Json.str(hit, "description", ""),
                    Json.str(hit, "author", ""),
                    Json.lng(hit, "downloads", 0),
                    Json.str(hit, "icon_url"),
                    Json.str(hit, "project_type", projectType), null, null, "modrinth"));
        }
        return results;
    }

    public List<Version> versions(String projectId, String gameVersion, String loader)
            throws IOException {
        StringBuilder url = new StringBuilder(API).append("/project/")
                .append(URLEncoder.encode(projectId, StandardCharsets.UTF_8)).append("/version");
        List<String> params = new ArrayList<>();
        if (gameVersion != null && !gameVersion.isBlank()) {
            params.add("game_versions=" + URLEncoder.encode("[\"" + gameVersion + "\"]",
                    StandardCharsets.UTF_8));
        }
        if (loader != null && !loader.isBlank() && !"any".equals(loader)) {
            params.add("loaders=" + URLEncoder.encode("[\"" + loader + "\"]",
                    StandardCharsets.UTF_8));
        }
        if (!params.isEmpty()) {
            url.append('?').append(String.join("&", params));
        }
        return parseVersions(net.getUnverifiedBytes(url.toString(), "Modrinth 版本列表"));
    }

    public Version version(String versionId) throws IOException {
        byte[] bytes = net.getUnverifiedBytes(API + "/version/"
                + URLEncoder.encode(versionId, StandardCharsets.UTF_8), "Modrinth 版本详情");
        List<Version> versions = parseVersions(bytes);
        if (versions.isEmpty()) {
            throw new IOException("Modrinth 版本不存在: " + versionId);
        }
        return versions.get(0);
    }

    private List<Version> parseVersions(byte[] bytes) {
        Object parsed = Json.parse(new String(bytes, StandardCharsets.UTF_8));
        List<Object> rawList = parsed instanceof List<?> ? Json.arr(parsed) : List.of(parsed);
        List<Version> result = new ArrayList<>();
        for (Object raw : rawList) {
            Map<String, Object> map = Json.obj(raw);
            String id = Json.str(map, "id");
            if (id == null) {
                continue;
            }
            List<Dependency> deps = new ArrayList<>();
            for (Object depRaw : Json.arr(map.get("dependencies"))) {
                Map<String, Object> dep = Json.obj(depRaw);
                deps.add(new Dependency(Json.str(dep, "version_id"),
                        Json.str(dep, "project_id"),
                        Json.str(dep, "file_name"),
                        Json.str(dep, "dependency_type", "optional")));
            }
            List<File> files = new ArrayList<>();
            for (Object fileRaw : Json.arr(map.get("files"))) {
                Map<String, Object> file = Json.obj(fileRaw);
                String url = Json.str(file, "url");
                String filename = Json.str(file, "filename");
                if (url == null || filename == null) {
                    continue;
                }
                Map<String, Object> hashes = Json.obj(file.get("hashes"));
                files.add(new File(url, filename,
                        Json.str(hashes, "sha1"),
                        Json.str(hashes, "sha512"),
                        Json.lng(file, "size", -1)));
            }
            result.add(new Version(id,
                    Json.str(map, "project_id", ""),
                    Json.str(map, "name", ""),
                    Json.str(map, "version_number", ""),
                    stringList(map.get("game_versions")),
                    stringList(map.get("loaders")),
                    Collections.unmodifiableList(deps),
                    Collections.unmodifiableList(files), "modrinth"));
        }
        return result;
    }

    private static List<String> stringList(Object raw) {
        List<String> result = new ArrayList<>();
        for (Object item : Json.arr(raw)) {
            if (item instanceof String text) {
                result.add(text);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
