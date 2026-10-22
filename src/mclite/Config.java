package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public final class Config {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path appHome;
    private Path gameDir;
    private Path accountsDir;
    private String accountId = "";
    private String username = "Steve";
    private String javaPath = "";
    private int memoryMb = 2048;
    private String jvmArgs = "";
    private String curseForgeApiKey = "";
    private String uiFontFamily = "";
    private final java.util.List<String> javaPaths = new java.util.ArrayList<>();
    private boolean forceModTranslation = true;
    private int windowWidth = 854;
    private int windowHeight = 480;
    private boolean downloadAssets = true;
    private boolean autoMirror = true;
    private boolean isolateVersions = true;
    private boolean autoMemory;
    private boolean autoJvmTuning = true;
    private boolean autoDownloadJava = true;
    private boolean sidebarCollapsed;
    private String filter = "release";
    private String lastVersion = "";

    private Config(Path appHome) {
        this.appHome = appHome;
    }

    public static Config load() {
        String overrideHome = System.getProperty("mclite.home");
        Path home = overrideHome != null && !overrideHome.isBlank()
                ? Path.of(overrideHome)
                : Path.of(System.getProperty("user.home"), ".mclite");
        home = home.toAbsolutePath().normalize();
        Config config = new Config(home);
        config.gameDir = defaultGameDir();
        config.accountsDir = home.resolve("accounts");

        Path file = config.configFile();
        if (Files.isRegularFile(file)) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException ex) {
                Log.warn("读取配置文件失败，将使用默认设置: " + ex.getMessage());
            }
            String savedGameDir = properties.getProperty("gameDir", "").trim();
            if (!savedGameDir.isEmpty()) {
                try {
                    config.gameDir = Path.of(savedGameDir).toAbsolutePath().normalize();
                } catch (RuntimeException ignored) {

                }
            }
            String savedAccounts = properties.getProperty("accountsDir", "").trim();
            if (!savedAccounts.isEmpty()) {
                try {
                    config.accountsDir = Path.of(savedAccounts).toAbsolutePath().normalize();
                } catch (RuntimeException ignored) {

                }
            }
            config.accountId = properties.getProperty("accountId", "").trim();
            config.username = sanitizeUsername(properties.getProperty("username", config.username));
            config.javaPath = properties.getProperty("javaPath", "").trim();
            config.memoryMb = clampMemory(parseInt(properties.getProperty("memoryMb"), 2048));
            config.jvmArgs = properties.getProperty("jvmArgs", "").trim();
            config.curseForgeApiKey = properties.getProperty("curseForgeApiKey", "").trim();
            config.uiFontFamily = properties.getProperty("uiFontFamily", "").trim();
            String savedJavaPaths = properties.getProperty("javaPaths", "");
            if (!savedJavaPaths.isBlank()) {
                for (String item : savedJavaPaths.split("\n")) {
                    if (!item.isBlank()) {
                        config.javaPaths.add(item.trim());
                    }
                }
            }
            if (config.curseForgeApiKey.isEmpty()) {
                String external = System.getProperty("mclite.curseforge.apikey",
                        System.getenv("MCLITE_CURSEFORGE_API_KEY"));
                if (external != null) {
                    config.curseForgeApiKey = external.trim();
                }
            }
            config.windowWidth = clampWidth(parseInt(properties.getProperty("windowWidth"), 854));
            config.windowHeight = clampHeight(parseInt(properties.getProperty("windowHeight"), 480));
            config.downloadAssets = Boolean.parseBoolean(properties.getProperty("downloadAssets", "true"));
            config.autoMirror = Boolean.parseBoolean(properties.getProperty("autoMirror", "true"));
            config.forceModTranslation = Boolean.parseBoolean(
                    properties.getProperty("forceModTranslation", "true"));
            config.isolateVersions = Boolean.parseBoolean(
                    properties.getProperty("isolateVersions", "true"));
            config.autoMemory = Boolean.parseBoolean(properties.getProperty("autoMemory", "false"));
            config.autoJvmTuning = Boolean.parseBoolean(
                    properties.getProperty("autoJvmTuning", "true"));
            config.autoDownloadJava = Boolean.parseBoolean(
                    properties.getProperty("autoDownloadJava", "true"));
            config.sidebarCollapsed = Boolean.parseBoolean(
                    properties.getProperty("sidebarCollapsed", "false"));
            String savedFilter = properties.getProperty("filter", "release");
            if (savedFilter.equals("release") || savedFilter.equals("snapshot") || savedFilter.equals("all")) {
                config.filter = savedFilter;
            }
            config.lastVersion = properties.getProperty("lastVersion", "").trim();
        }

        String gameDirOverride = System.getProperty("mclite.gameDir");
        if (gameDirOverride != null && !gameDirOverride.isBlank()) {
            config.gameDir = Path.of(gameDirOverride).toAbsolutePath().normalize();
        }
        config.gameDir = normalizeGameDir(config.gameDir);
        return config;
    }

    public Config copyForGameDir(Path dir) {
        Config copy = new Config(appHome);
        copy.gameDir = dir.toAbsolutePath().normalize();
        copy.accountsDir = accountsDir;
        copy.accountId = accountId;
        copy.username = username;
        copy.javaPath = javaPath;
        copy.memoryMb = memoryMb;
        copy.jvmArgs = jvmArgs;
        copy.curseForgeApiKey = curseForgeApiKey;
        copy.uiFontFamily = uiFontFamily;
        copy.javaPaths.addAll(javaPaths);
        copy.forceModTranslation = forceModTranslation;
        copy.windowWidth = windowWidth;
        copy.windowHeight = windowHeight;
        copy.downloadAssets = downloadAssets;
        copy.autoMirror = autoMirror;
        copy.isolateVersions = isolateVersions;
        copy.autoMemory = autoMemory;
        copy.autoJvmTuning = autoJvmTuning;
        copy.autoDownloadJava = autoDownloadJava;
        copy.sidebarCollapsed = sidebarCollapsed;
        copy.filter = filter;
        copy.lastVersion = lastVersion;
        return copy;
    }

    public static Path normalizeGameDir(Path path) {
        if (path == null) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        try {
            if (!Files.isDirectory(normalized.resolve("versions"))
                    && normalized.getParent() != null
                    && Files.isDirectory(normalized.getParent().resolve("versions"))) {
                return normalized.getParent().toAbsolutePath().normalize();
            }
        } catch (RuntimeException ignored) {
        }
        return normalized;
    }

    public static Path defaultGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("mac")) {
            return home.resolve("Library/Application Support/minecraft").toAbsolutePath().normalize();
        }
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, ".minecraft").toAbsolutePath().normalize();
            }
            return home.resolve(".minecraft").toAbsolutePath().normalize();
        }
        return home.resolve(".minecraft").toAbsolutePath().normalize();
    }

    public Path appHome() {
        return appHome;
    }

    public Path configFile() {
        return appHome.resolve("config.properties");
    }

    public Path uiCssFile() {
        return appHome.resolve("ui.css");
    }

    public Path cacheDir() {
        return appHome.resolve("cache");
    }

    public Path logsDir() {
        return appHome.resolve("logs");
    }

    public Path runtimesDir() {
        return appHome.resolve("java");
    }

    public Path gameDir() {
        return gameDir;
    }

    public void gameDir(Path value) {
        if (value != null) {
            this.gameDir = value.toAbsolutePath().normalize();
        }
    }

    public Path accountsDir() {
        return accountsDir;
    }

    public void accountsDir(Path value) {
        if (value != null) {
            this.accountsDir = value.toAbsolutePath().normalize();
        }
    }

    public String accountId() {
        return accountId;
    }

    public void accountId(String value) {
        this.accountId = value == null ? "" : value.trim();
    }

    public String username() {
        return username;
    }

    public void username(String value) {
        this.username = sanitizeUsername(value);
    }

    public String javaPath() {
        return javaPath;
    }

    public void javaPath(String value) {
        this.javaPath = value == null ? "" : value.trim();
    }

    public int memoryMb() {
        return memoryMb;
    }

    public boolean autoMemory() {
        return autoMemory;
    }

    public java.util.List<String> javaPaths() {
        return java.util.List.copyOf(javaPaths);
    }

    public void addJavaPath(Path path) {
        if (path == null) {
            return;
        }
        String value = path.toAbsolutePath().normalize().toString();
        if (!javaPaths.contains(value)) {
            javaPaths.add(value);
        }
    }

    public void removeJavaPath(String value) {
        if (value != null) {
            javaPaths.removeIf(item -> item.equals(value));
        }
    }

    public String uiFontFamily() {
        return uiFontFamily;
    }

    public void uiFontFamily(String value) {
        this.uiFontFamily = value == null ? "" : value.trim();
    }

    public boolean forceModTranslation() {
        return forceModTranslation;
    }

    public void forceModTranslation(boolean value) {
        this.forceModTranslation = value;
    }

    public boolean autoDownloadJava() {
        return autoDownloadJava;
    }

    public void autoDownloadJava(boolean value) {
        this.autoDownloadJava = value;
    }

    public boolean autoJvmTuning() {
        return autoJvmTuning;
    }

    public void autoJvmTuning(boolean value) {
        this.autoJvmTuning = value;
    }

    public void autoMemory(boolean value) {
        this.autoMemory = value;
    }

    public boolean sidebarCollapsed() {
        return sidebarCollapsed;
    }

    public void sidebarCollapsed(boolean value) {
        this.sidebarCollapsed = value;
    }

    public boolean isolateVersions() {
        return isolateVersions;
    }

    public void isolateVersions(boolean value) {
        this.isolateVersions = value;
    }

    public Path effectiveGameDir(String versionId) {
        if (!isolateVersions || versionId == null || versionId.isBlank()) {
            return gameDir;
        }
        if (!VersionIds.valid(versionId)) {
            return gameDir;
        }
        return gameDir.resolve("versions").resolve(versionId).toAbsolutePath().normalize();
    }

    public int effectiveMemoryMb() {
        if (!autoMemory) {
            return memoryMb;
        }
        long total = physicalMemoryMb();
        int recommended;
        if (total <= 0) {
            recommended = 2048;
        } else if (total <= 4096) {
            recommended = 1536;
        } else if (total <= 8192) {
            recommended = 3072;
        } else if (total <= 16384) {
            recommended = 6144;
        } else if (total <= 32768) {
            recommended = 8192;
        } else {
            recommended = 12288;
        }
        return clampMemory(recommended);
    }

    private static long physicalMemoryMb() {
        try {
            java.lang.management.OperatingSystemMXBean bean =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getTotalMemorySize() / (1024L * 1024L);
            }
        } catch (Throwable ignored) {
        }
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    public void memoryMb(int value) {
        this.memoryMb = clampMemory(value);
    }

    public String curseForgeApiKey() {
        return curseForgeApiKey;
    }

    public void curseForgeApiKey(String value) {
        this.curseForgeApiKey = value == null ? "" : value.trim();
    }

    public String jvmArgs() {
        return jvmArgs;
    }

    public void jvmArgs(String value) {
        this.jvmArgs = value == null ? "" : value.trim();
    }

    public int windowWidth() {
        return windowWidth;
    }

    public void windowWidth(int value) {
        this.windowWidth = clampWidth(value);
    }

    public int windowHeight() {
        return windowHeight;
    }

    public void windowHeight(int value) {
        this.windowHeight = clampHeight(value);
    }

    public boolean autoMirror() {
        return autoMirror;
    }

    public void autoMirror(boolean value) {
        this.autoMirror = value;
    }

    public boolean downloadAssets() {
        return downloadAssets;
    }

    public void downloadAssets(boolean value) {
        this.downloadAssets = value;
    }

    public String filter() {
        return filter;
    }

    public void filter(String value) {
        if (value != null && (value.equals("release") || value.equals("snapshot") || value.equals("all"))) {
            this.filter = value;
        }
    }

    public String lastVersion() {
        return lastVersion;
    }

    public void lastVersion(String value) {
        this.lastVersion = value == null ? "" : value.trim();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(appHome);
            Properties properties = new Properties();
            properties.setProperty("gameDir", gameDir.toString());
            properties.setProperty("accountsDir", accountsDir.toString());
            properties.setProperty("accountId", accountId);
            properties.setProperty("username", username);
            properties.setProperty("javaPath", javaPath);
            properties.setProperty("memoryMb", Integer.toString(memoryMb));
            properties.setProperty("jvmArgs", jvmArgs);
            properties.setProperty("curseForgeApiKey", curseForgeApiKey);
            properties.setProperty("uiFontFamily", uiFontFamily);
            properties.setProperty("javaPaths", String.join("\n", javaPaths));
            properties.setProperty("forceModTranslation",
                    Boolean.toString(forceModTranslation));
            properties.setProperty("windowWidth", Integer.toString(windowWidth));
            properties.setProperty("windowHeight", Integer.toString(windowHeight));
            properties.setProperty("downloadAssets", Boolean.toString(downloadAssets));
            properties.setProperty("autoMirror", Boolean.toString(autoMirror));
            properties.setProperty("isolateVersions", Boolean.toString(isolateVersions));
            properties.setProperty("autoMemory", Boolean.toString(autoMemory));
            properties.setProperty("autoJvmTuning", Boolean.toString(autoJvmTuning));
            properties.setProperty("autoDownloadJava", Boolean.toString(autoDownloadJava));
            properties.setProperty("sidebarCollapsed", Boolean.toString(sidebarCollapsed));
            properties.setProperty("filter", filter);
            properties.setProperty("lastVersion", lastVersion);
            Path temp = configFile().resolveSibling("config.properties.tmp");
            try (var out = Files.newOutputStream(temp, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(out, "MC Lite launcher settings");
            }
            Files.move(temp, configFile(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Log.warn("保存配置失败: " + ex.getMessage());
        }
    }

    public static boolean isValidUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,16}");
    }

    private static String sanitizeUsername(String value) {
        if (!isValidUsername(value)) {
            return "Steve";
        }
        return value;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static int clampMemory(int value) {
        return Math.max(512, Math.min(32768, value));
    }

    private static int clampWidth(int value) {
        return Math.max(320, Math.min(3840, value));
    }

    private static int clampHeight(int value) {
        return Math.max(240, Math.min(2160, value));
    }

    public static String timestamp() {
        return TIME.format(LocalDateTime.now());
    }
}
