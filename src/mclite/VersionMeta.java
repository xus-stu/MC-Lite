package mclite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VersionMeta {
    private final String id;
    private final String clientJarId;
    private final String type;
    private final String mainClass;
    private final String assetsId;
    private final AssetIndex assetIndex;
    private final Download client;
    private final List<Library> libraries;
    private final List<Arg> jvmArgs;
    private final List<Arg> gameArgs;
    private final String legacyArguments;
    private final Download loggingFile;
    private final String loggingArgument;
    private final String javaComponent;
    private final int javaMajor;

    private VersionMeta(String id, String clientJarId, String type, String mainClass,
                        String assetsId, AssetIndex assetIndex,
                        Download client, List<Library> libraries, List<Arg> jvmArgs, List<Arg> gameArgs,
                        String legacyArguments, Download loggingFile, String loggingArgument,
                        String javaComponent, int javaMajor) {
        this.id = id;
        this.clientJarId = clientJarId;
        this.type = type;
        this.mainClass = mainClass;
        this.assetsId = assetsId;
        this.assetIndex = assetIndex;
        this.client = client;
        this.libraries = Collections.unmodifiableList(libraries);
        this.jvmArgs = Collections.unmodifiableList(jvmArgs);
        this.gameArgs = Collections.unmodifiableList(gameArgs);
        this.legacyArguments = legacyArguments;
        this.loggingFile = loggingFile;
        this.loggingArgument = loggingArgument;
        this.javaComponent = javaComponent;
        this.javaMajor = javaMajor;
    }

    public static VersionMeta parse(String json, String fallbackId) {
        return parse(json, fallbackId, fallbackId);
    }

    public static VersionMeta parse(String json, String fallbackId, String clientJarId) {
        return parseMap(Json.obj(Json.parse(json)), fallbackId, clientJarId);
    }

    public static VersionMeta parseMap(Map<String, Object> root, String fallbackId, String clientJarId) {
        String id = Json.str(root, "id", fallbackId);
        if (!VersionIds.valid(id)) {
            throw new IllegalArgumentException("版本元数据中的 id 非法");
        }
        String mainClass = Json.str(root, "mainClass");
        if (mainClass == null || !mainClass.matches("[A-Za-z0-9_.$]{1,200}")) {
            throw new IllegalArgumentException("版本元数据中的 mainClass 非法");
        }
        String type = Json.str(root, "type", "release");

        Map<String, Object> assetMap = Json.obj(root.get("assetIndex"));
        String assetsId = Json.str(assetMap, "id", Json.str(root, "assets", "legacy"));
        AssetIndex assetIndex = null;
        String assetUrl = Json.str(assetMap, "url");
        if (assetUrl != null) {
            assetIndex = new AssetIndex(
                    assetsId,
                    assetUrl,
                    Json.str(assetMap, "sha1"),
                    Json.lng(assetMap, "size", -1),
                    Json.lng(assetMap, "totalSize", 0));
        }

        Map<String, Object> downloads = Json.obj(root.get("downloads"));
        Download client = Download.parse(Json.obj(downloads.get("client")), null);
        if (client == null || client.url() == null) {
            throw new IllegalArgumentException("版本元数据缺少客户端下载地址");
        }

        List<Library> libraries = new ArrayList<>();
        for (Object item : Json.arr(root.get("libraries"))) {
            Library library = Library.parse(Json.obj(item));
            if (library != null) {
                libraries.add(library);
            }
        }

        Map<String, Object> arguments = Json.obj(root.get("arguments"));
        List<Arg> jvmArgs = Arg.parseList(arguments.get("jvm"));
        List<Arg> gameArgs = Arg.parseList(arguments.get("game"));
        String legacyArguments = Json.str(root, "minecraftArguments");

        String loggingArgument = null;
        Download loggingFile = null;
        Map<String, Object> logging = Json.obj(root.get("logging"));
        Map<String, Object> loggingClient = Json.obj(logging.get("client"));
        if (!loggingClient.isEmpty()) {
            loggingArgument = Json.str(loggingClient, "argument");
            Map<String, Object> file = Json.obj(loggingClient.get("file"));
            String fileId = Json.str(file, "id");
            String fileUrl = Json.str(file, "url");
            if (fileId != null && fileUrl != null) {
                loggingFile = new Download(fileId, fileUrl, Json.str(file, "sha1"),
                        Json.lng(file, "size", -1));
            }
        }

        Map<String, Object> javaVersion = Json.obj(root.get("javaVersion"));
        String javaComponent = Json.str(javaVersion, "component");
        int javaMajor = Json.integer(javaVersion, "majorVersion", 0);
        if (javaMajor <= 0) {
            javaMajor = inferJavaMajor(assetIndex != null ? assetIndex.id() : assetsId);
        }

        return new VersionMeta(id, clientJarId == null ? id : clientJarId, type, mainClass,
                assetsId, assetIndex, client, libraries,
                jvmArgs, gameArgs, legacyArguments, loggingFile, loggingArgument,
                javaComponent, javaMajor);
    }

    private static int inferJavaMajor(String assetsId) {
        return 8;
    }

    public String id() {
        return id;
    }

    public String clientJarId() {
        return clientJarId;
    }

    public String type() {
        return type;
    }

    public String mainClass() {
        return mainClass;
    }

    public String assetsId() {
        return assetsId;
    }

    public AssetIndex assetIndex() {
        return assetIndex;
    }

    public Download client() {
        return client;
    }

    public List<Library> libraries() {
        return libraries;
    }

    public List<Arg> jvmArgs() {
        return jvmArgs;
    }

    public List<Arg> gameArgs() {
        return gameArgs;
    }

    public String legacyArguments() {
        return legacyArguments;
    }

    public Download loggingFile() {
        return loggingFile;
    }

    public String loggingArgument() {
        return loggingArgument;
    }

    public String javaComponent() {
        return javaComponent;
    }

    public int javaMajor() {
        return javaMajor;
    }

    public record AssetIndex(String id, String url, String sha1, long size, long totalSize) {
    }

    public record Download(String path, String url, String sha1, long size) {
        public static Download parse(Map<String, Object> map, String fallbackPath) {
            if (map == null || map.isEmpty()) {
                return null;
            }
            String url = Json.str(map, "url");
            if (url == null) {
                return null;
            }
            return new Download(Json.str(map, "path", fallbackPath), url,
                    Json.str(map, "sha1"), Json.lng(map, "size", -1));
        }
    }

    public record Arg(List<Rules.Rule> rules, List<String> values) {
        public List<String> collect(Rules.Env env) {
            if (!Rules.allows(rules, env)) {
                return List.of();
            }
            return values;
        }

        public static List<Arg> parseList(Object raw) {
            List<Object> list = Json.arr(raw);
            if (list.isEmpty()) {
                return List.of();
            }
            List<Arg> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String value) {
                    result.add(new Arg(List.of(), List.of(value)));
                    continue;
                }
                Map<String, Object> map = Json.obj(item);
                List<Rules.Rule> rules = Rules.Rule.parseList(map.get("rules"));
                Object value = map.get("value");
                List<String> values = new ArrayList<>();
                if (value instanceof String single) {
                    values.add(single);
                } else {
                    for (Object part : Json.arr(value)) {
                        if (part instanceof String text) {
                            values.add(text);
                        }
                    }
                }
                if (!values.isEmpty()) {
                    result.add(new Arg(rules, Collections.unmodifiableList(values)));
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    public static final class Library {
        private final String name;
        private final String url;
        private final List<Rules.Rule> rules;
        private final Download artifact;
        private final Map<String, Download> classifiers;
        private final Map<String, String> natives;
        private final List<String> extractExclude;

        private Library(String name, String url, List<Rules.Rule> rules, Download artifact,
                        Map<String, Download> classifiers, Map<String, String> natives,
                        List<String> extractExclude) {
            this.name = name;
            this.url = url;
            this.rules = rules;
            this.artifact = artifact;
            this.classifiers = classifiers;
            this.natives = natives;
            this.extractExclude = extractExclude;
        }

        public static Library parse(Map<String, Object> map) {
            String name = Json.str(map, "name", "");
            String libraryUrl = Json.str(map, "url", "");
            Map<String, Object> downloads = Json.obj(map.get("downloads"));
            Download artifact = Download.parse(Json.obj(downloads.get("artifact")), null);
            if (artifact == null) {
                artifact = mavenDownload(name, libraryUrl);
            }
            Map<String, Download> classifiers = new LinkedHashMap<>();
            Map<String, Object> classifierMap = Json.obj(downloads.get("classifiers"));
            for (Map.Entry<String, Object> entry : classifierMap.entrySet()) {
                Download download = Download.parse(Json.obj(entry.getValue()), null);
                if (download != null) {
                    classifiers.put(entry.getKey(), download);
                }
            }
            Map<String, String> natives = new LinkedHashMap<>();
            Map<String, Object> nativesMap = Json.obj(map.get("natives"));
            for (Map.Entry<String, Object> entry : nativesMap.entrySet()) {
                if (entry.getValue() instanceof String classifier) {
                    natives.put(entry.getKey(), classifier);
                }
            }
            List<String> excludes = new ArrayList<>();
            Map<String, Object> extract = Json.obj(map.get("extract"));
            for (Object item : Json.arr(extract.get("exclude"))) {
                if (item instanceof String text) {
                    excludes.add(text);
                }
            }
            return new Library(name, libraryUrl, Rules.Rule.parseList(map.get("rules")), artifact,
                    Collections.unmodifiableMap(classifiers), Collections.unmodifiableMap(natives),
                    Collections.unmodifiableList(excludes));
        }

        public boolean applies(Rules.Env env) {
            return Rules.allows(rules, env);
        }

        private static Download mavenDownload(String name, String baseUrl) {
            if (name == null || name.isBlank() || baseUrl == null || baseUrl.isBlank()) {
                return null;
            }
            String[] parts = name.split(":");
            if (parts.length < 3 || parts.length > 4) {
                return null;
            }
            String group = parts[0];
            String artifact = parts[1];
            String version = parts[2];
            String classifier = parts.length == 4 && !parts[3].isBlank() ? "-" + parts[3] : "";
            String path = group.replace('.', '/') + "/" + artifact + "/" + version
                    + "/" + artifact + "-" + version + classifier + ".jar";
            String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return new Download(path, base + path, null, -1);
        }

        public Download artifact() {
            return artifact;
        }

        public Download nativeFor(Rules.Env env) {
            if (natives.isEmpty()) {
                return null;
            }
            String classifier = natives.get(env.osName());
            if (classifier == null) {
                return null;
            }
            String arch = env.osArch().equals("x86") ? "32" : "64";
            classifier = classifier.replace("${arch}", arch);
            return classifiers.get(classifier);
        }

        public Map<String, Download> classifiers() {
            return classifiers;
        }

        public Map<String, String> natives() {
            return natives;
        }

        public List<String> extractExclude() {
            return extractExclude;
        }

        public String name() {
            return name;
        }
    }
}
