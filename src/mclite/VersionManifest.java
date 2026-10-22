package mclite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class VersionManifest {
    private final String latestRelease;
    private final String latestSnapshot;
    private final List<VersionEntry> versions;

    private VersionManifest(String latestRelease, String latestSnapshot, List<VersionEntry> versions) {
        this.latestRelease = latestRelease;
        this.latestSnapshot = latestSnapshot;
        this.versions = Collections.unmodifiableList(versions);
    }

    public static VersionManifest parse(String json) {
        Map<String, Object> root = Json.obj(Json.parse(json));
        Map<String, Object> latest = Json.obj(root.get("latest"));
        String latestRelease = Json.str(latest, "release", "");
        String latestSnapshot = Json.str(latest, "snapshot", "");
        List<VersionEntry> entries = new ArrayList<>();
        for (Object item : Json.arr(root.get("versions"))) {
            Map<String, Object> map = Json.obj(item);
            String id = Json.str(map, "id");
            String url = Json.str(map, "url");
            if (id == null || url == null || !VersionIds.valid(id)) {
                continue;
            }
            entries.add(new VersionEntry(
                    id,
                    Json.str(map, "type", "release"),
                    url,
                    Json.str(map, "sha1"),
                    Json.str(map, "releaseTime", ""),
                    Json.integer(map, "complianceLevel", 0)));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("官方版本列表中没有任何版本");
        }
        return new VersionManifest(latestRelease, latestSnapshot, entries);
    }

    public String latestRelease() {
        return latestRelease;
    }

    public String latestSnapshot() {
        return latestSnapshot;
    }

    public List<VersionEntry> versions() {
        return versions;
    }

    public VersionEntry find(String id) {
        if (id == null) {
            return null;
        }
        for (VersionEntry entry : versions) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public record VersionEntry(String id, String type, String url, String sha1,
                               String releaseTime, int complianceLevel, boolean local) {
        public VersionEntry(String id, String type, String url, String sha1,
                            String releaseTime, int complianceLevel) {
            this(id, type, url, sha1, releaseTime, complianceLevel, false);
        }

        public String displayType() {
            return local ? "本地实例" : Formatting.typeName(type);
        }
    }
}
