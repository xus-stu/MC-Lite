package mclite;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CurseForge {
    private static final String API = "https://api.curseforge.com/v1";
    private final Net net;
    private final String apiKey;

    public CurseForge(String apiKey, ProgressSink progress, CancellationToken token) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.net = new Net(progress, token);
    }

    private void requireKey() throws IOException {
        if (apiKey.isBlank()) {
            throw new IOException("CurseForge 需要 API Key。请到「设置 → 内容源」填写。");
        }
    }

    public List<Modrinth.Project> search(String query, String projectType, String gameVersion,
                                         String loader) throws IOException {
        return search(query, projectType, gameVersion, loader, 0, 20);
    }

    public List<Modrinth.Project> search(String query, String projectType, String gameVersion,
                                         String loader, int offset, int limit) throws IOException {
        return search(query, projectType, gameVersion, loader, offset, limit, "featured");
    }

    public List<Modrinth.Project> search(String query, String projectType, String gameVersion,
                                         String loader, int offset, int limit, String sort)
            throws IOException {
        requireKey();
        int classId = switch (projectType) {
            case "resourcepack", "shader" -> 12;
            case "modpack" -> 4471;
            default -> 6;
        };
        int sortField = switch (sort == null ? "" : sort) {
            case "popularity" -> 2;
            case "updated" -> 3;
            case "name" -> 4;
            case "totalDownloads" -> 6;
            default -> 1;
        };
        String sortOrder = "name".equals(sort) ? "asc" : "desc";
        StringBuilder url = new StringBuilder(API).append("/mods/search?gameId=432&classId=")
                .append(classId).append("&pageSize=").append(Math.max(1, limit))
                .append("&index=").append(Math.max(0, offset))
                .append("&sortField=").append(sortField)
                .append("&sortOrder=").append(sortOrder);
        if (query != null && !query.isBlank()) {
            url.append("&searchFilter=").append(encode(query));
        }
        if (gameVersion != null && !gameVersion.isBlank()) {
            url.append("&gameVersion=").append(encode(gameVersion));
        }
        int loaderType = loaderType(loader);
        if (loaderType > 0 && "mod".equals(projectType)) {
            url.append("&modLoaderType=").append(loaderType);
        }
        Map<String, Object> root = Json.obj(Json.parse(new String(
                net.getUnverifiedBytes(url.toString(), "CurseForge 搜索", headers()),
                StandardCharsets.UTF_8)));
        List<Modrinth.Project> result = new ArrayList<>();
        for (Object raw : Json.arr(root.get("data"))) {
            Map<String, Object> item = Json.obj(raw);
            long id = Json.lng(item, "id", 0);
            String name = Json.str(item, "name");
            if (id <= 0 || name == null) {
                continue;
            }
            String author = "";
            for (Object authorRaw : Json.arr(item.get("authors"))) {
                Map<String, Object> a = Json.obj(authorRaw);
                if (Json.str(a, "name") != null) {
                    author = Json.str(a, "name");
                    break;
                }
            }
            Map<String, Object> logo = Json.obj(item.get("logo"));
            String icon = Json.str(logo, "thumbnailUrl", Json.str(logo, "url"));
            result.add(new Modrinth.Project("cf:" + id,
                    Json.str(item, "slug", Long.toString(id)), name,
                    Json.str(item, "summary", ""), author,
                    Json.lng(item, "downloadCount", 0), icon, projectType,
                    null, null, "curseforge"));
        }
        return result;
    }

    public List<Modrinth.Version> versions(String projectId, String gameVersion, String loader)
            throws IOException {
        requireKey();
        String id = numericId(projectId);
        StringBuilder url = new StringBuilder(API).append("/mods/").append(id)
                .append("/files?pageSize=50&index=0");
        if (gameVersion != null && !gameVersion.isBlank()) {
            url.append("&gameVersion=").append(encode(gameVersion));
        }
        int loaderType = loaderType(loader);
        if (loaderType > 0) {
            url.append("&modLoaderType=").append(loaderType);
        }
        Map<String, Object> root = Json.obj(Json.parse(new String(
                net.getUnverifiedBytes(url.toString(), "CurseForge 文件列表", headers()),
                StandardCharsets.UTF_8)));
        List<Modrinth.Version> result = new ArrayList<>();
        for (Object raw : Json.arr(root.get("data"))) {
            Modrinth.Version version = parseFile(Json.obj(raw), id);
            if (version != null) {
                result.add(version);
            }
        }
        return result;
    }

    public Modrinth.Version file(String projectId, String fileId) throws IOException {
        requireKey();
        String id = numericId(projectId);
        String fid = numericId(fileId);
        Map<String, Object> root = Json.obj(Json.parse(new String(
                net.getUnverifiedBytes(API + "/mods/" + id + "/files/" + fid,
                        "CurseForge 文件", headers()), StandardCharsets.UTF_8)));
        return parseFile(Json.obj(root.get("data")), id);
    }

    private Modrinth.Version parseFile(Map<String, Object> file, String projectId) {
        long id = Json.lng(file, "id", 0);
        if (id <= 0) {
            return null;
        }
        String[] primary = {Json.str(file, "downloadUrl")};
        if (primary[0] == null || primary[0].isBlank()) {
            return null;
        }
        String filename = Json.str(file, "fileName");
        if (filename == null) {
            return null;
        }
        String sha1 = null;
        String sha512 = null;
        for (Object raw : Json.arr(file.get("hashes"))) {
            Map<String, Object> hash = Json.obj(raw);
            int algo = Json.integer(hash, "algo", 0);
            String value = Json.str(hash, "value");
            if (algo == 1) {
                sha1 = value;
            } else if (algo == 2) {
                sha512 = value;
            }
        }
        List<String> games = new ArrayList<>();
        List<String> loaders = new ArrayList<>();
        for (Object raw : Json.arr(file.get("gameVersions"))) {
            if (raw instanceof String value) {
                if (value.matches("(?i)fabric|forge|neoforge|quilt")) {
                    loaders.add(value.toLowerCase(java.util.Locale.ROOT));
                } else {
                    games.add(value);
                }
            }
        }
        List<Modrinth.Dependency> dependencies = new ArrayList<>();
        for (Object raw : Json.arr(file.get("dependencies"))) {
            Map<String, Object> dep = Json.obj(raw);
            long modId = Json.lng(dep, "modId", 0);
            int relation = Json.integer(dep, "relationType", 0);
            if (modId > 0) {
                dependencies.add(new Modrinth.Dependency(null, "cf:" + modId, null,
                        relation == 3 ? "required" : "optional"));
            }
        }
        return new Modrinth.Version("cf:" + id, "cf:" + projectId,
                Json.str(file, "displayName", filename),
                Json.str(file, "displayName", filename),
                Collections.unmodifiableList(games), Collections.unmodifiableList(loaders),
                Collections.unmodifiableList(dependencies),
                List.of(new Modrinth.File(primary[0], filename, sha1, sha512,
                        Json.lng(file, "fileLength", -1))), "curseforge");
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("Accept", "application/json");
        return headers;
    }

    private static String numericId(String id) throws IOException {
        String value = id != null && id.startsWith("cf:") ? id.substring(3) : id;
        if (value == null || !value.matches("\\d+")) {
            throw new IOException("非法 CurseForge 项目 ID");
        }
        return value;
    }

    private static int loaderType(String loader) {
        if (loader == null) {
            return 0;
        }
        return switch (loader) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> 0;
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
