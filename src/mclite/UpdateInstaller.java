package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把下载好的新版本替换到当前安装位置。
 *
 * <p>更新包下载在 {@code ~/.mclite/updates/} 只是中转。真正启动前必须把它
 * 写回用户实际打开的 jar 或 macOS 应用包，否则下次启动仍会回到旧版本。</p>
 */
public final class UpdateInstaller {
    private static final Pattern VERSIONED_JAR = Pattern.compile(
            "^MCLite_v[0-9][0-9A-Za-z._-]*(?:-region)?\\.jar$");
    private static final Pattern APP_BUNDLE = Pattern.compile(
            "^(MC Lite(?: Region)?) v[^/]+\\.app$");
    private static final List<String> SCRIPT_NAMES = List.of(
            "run.sh", "run-region.sh", "run.bat", "run-region.bat",
            "start.bat", "debug.bat",
            "启动器.command", "启动器-地区版.command");

    public record Installed(Path launch, Path appBundle, boolean appMode) {
        public static Installed staged(Path staged) {
            return new Installed(staged, null, false);
        }
    }

    private record ScriptEdit(Path file, String oldText, String newText) {
    }

    private UpdateInstaller() {
    }

    public static Installed install(Path staged, String version, String channel)
            throws IOException {
        Path source = currentJar();
        if (source == null) {
            Log.warn("无法定位当前启动器位置，更新继续从缓存启动");
            return Installed.staged(staged);
        }
        return installFrom(source, staged, version, channel);
    }

    static Installed installFrom(Path source, Path staged, String version, String channel)
            throws IOException {
        if (!Files.isRegularFile(staged)) {
            throw new IOException("更新文件不存在: " + staged);
        }
        if (!Files.isRegularFile(source)) {
            return Installed.staged(staged);
        }
        if (isWindows()) {
            Path target = DesktopShortcut.updateIfInstalled(staged, version);
            if (!samePath(target, staged)) {
                return new Installed(target, null, false);
            }
            return installWindowsStandalone(source, staged, version, channel);
        }
        Path app = appBundleOf(source);
        if (app != null) {
            return installAppBundle(source, app, staged, version, channel);
        }
        return installPlain(source, staged, version, channel);
    }

    static Path currentJar() {
        try {
            var source = UpdateInstaller.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path : null;
        } catch (Exception ex) {
            return null;
        }
    }

    static Path appBundleOf(Path jar) {
        Path resources = jar.getParent();
        if (resources == null || !"Resources".equals(String.valueOf(resources.getFileName()))) {
            return null;
        }
        Path contents = resources.getParent();
        if (contents == null || !"Contents".equals(String.valueOf(contents.getFileName()))) {
            return null;
        }
        Path app = contents.getParent();
        if (app == null || !app.getFileName().toString().endsWith(".app")) {
            return null;
        }
        return Files.isDirectory(contents.resolve("MacOS")) ? app : null;
    }

    static Installed installPlain(Path source, Path staged, String version,
                                  String channel) throws IOException {
        Path dir = source.getParent();
        if (dir == null) {
            throw new IOException("无法确定安装目录: " + source);
        }
        if (!Files.isWritable(dir)) {
            throw new IOException("安装目录不可写: " + dir);
        }
        boolean region = "region".equals(channel);
        String oldName = source.getFileName().toString();
        String targetName = versionedName(oldName, version, region);
        boolean rename = targetName != null && !targetName.equals(oldName);
        Path target = rename ? dir.resolve(targetName) : source;
        List<ScriptEdit> edits = rename ? planScripts(source, target) : List.of();
        if (edits == null) {
            // 启动脚本不可写时保持原文件名，避免脚本指向一个不存在的 jar。
            rename = false;
            target = source;
            edits = List.of();
        }
        copyAtomically(staged, target);
        if (rename) {
            try {
                applyScripts(edits);
            } catch (IOException ex) {
                copyAtomically(target, source);
                deleteQuietly(target);
                restoreScripts(edits);
                throw ex;
            }
            deleteQuietly(source);
        }
        cleanOldJars(dir, target, region);
        Log.info("启动器已安装到 " + target + "（V" + version + "）");
        return new Installed(target, null, false);
    }

    private static Installed installWindowsStandalone(Path source, Path staged,
                                                      String version, String channel)
            throws IOException {
        Path dir = source.getParent();
        if (dir == null || !Files.isWritable(dir)) {
            return Installed.staged(staged);
        }
        boolean region = "region".equals(channel);
        String oldName = source.getFileName().toString();
        String targetName = versionedName(oldName, version, region);
        if (targetName == null || targetName.equals(oldName)) {
            // Windows 不能覆盖正在运行的 jar；没有版本号的文件名只能下次再试。
            return Installed.staged(staged);
        }
        Path target = dir.resolve(targetName);
        List<ScriptEdit> edits = planScripts(source, target);
        if (edits == null) {
            // 启动脚本改不了时继续从缓存启动，避免用户下次点到旧 jar。
            return Installed.staged(staged);
        }
        copyAtomically(staged, target);
        try {
            applyScripts(edits);
        } catch (IOException ex) {
            restoreScripts(edits);
            throw ex;
        }
        cleanOldJars(dir, target, region);
        Log.info("启动器已安装到 " + target + "（V" + version + "）");
        return new Installed(target, null, false);
    }

    static Installed installAppBundle(Path source, Path app, Path staged,
                                      String version, String channel)
            throws IOException {
        Path contents = app.resolve("Contents");
        Path resources = contents.resolve("Resources");
        Path macos = contents.resolve("MacOS");
        if (!Files.isWritable(resources)) {
            throw new IOException("应用包不可写: " + app);
        }
        boolean region = "region".equals(channel);
        String oldName = source.getFileName().toString();
        String targetName = "MCLite_v" + version + (region ? "-region.jar" : ".jar");
        Path target = resources.resolve(targetName);
        boolean rename = !targetName.equals(oldName);
        Path script = macos.resolve("MCLite");
        ScriptEdit edit = null;
        if (rename && Files.isRegularFile(script)) {
            String text = Files.readString(script, StandardCharsets.UTF_8);
            if (!text.contains(oldName) || !Files.isWritable(script)) {
                // 脚本不能改或没有引用旧文件名时，覆盖旧 jar，避免启动脚本失效。
                rename = false;
            } else {
                edit = new ScriptEdit(script, text, text.replace(oldName, targetName));
            }
        }
        if (!rename) {
            target = source;
        }
        copyAtomically(staged, target);
        if (rename && edit != null) {
            try {
                applyScript(edit);
            } catch (IOException ex) {
                copyAtomically(target, source);
                deleteQuietly(target);
                throw ex;
            }
        }
        try {
            updateInfoPlist(contents.resolve("Info.plist"), version, region);
        } catch (IOException ex) {
            Log.warn("更新应用包版本信息失败: " + ex.getMessage());
        }
        if (rename) {
            deleteQuietly(source);
        }
        cleanOldJars(resources, target, region);
        Path bundle = renameBundle(app, version, region);
        Path launch = bundle.resolve("Contents/Resources").resolve(target.getFileName());
        Log.info("macOS 应用包已更新到 " + bundle + "（V" + version + "）");
        return new Installed(launch, bundle, true);
    }

    static String versionedName(String name, String version, boolean region) {
        if (name == null || !VERSIONED_JAR.matcher(name).matches()) {
            return null;
        }
        return "MCLite_v" + version + (region ? "-region.jar" : ".jar");
    }

    private static List<ScriptEdit> planScripts(Path source, Path target) {
        String oldName = source.getFileName().toString();
        String newName = target.getFileName().toString();
        Path dir = source.getParent();
        if (dir == null) {
            return List.of();
        }
        List<Path> dirs = new ArrayList<>();
        dirs.add(dir);
        if (dir.getParent() != null) {
            dirs.add(dir.getParent());
        }
        List<ScriptEdit> edits = new ArrayList<>();
        for (Path candidateDir : dirs) {
            for (String name : SCRIPT_NAMES) {
                Path file = candidateDir.resolve(name);
                if (!Files.isRegularFile(file) || !Files.isWritable(file)) {
                    continue;
                }
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    if (text.contains(oldName)) {
                        edits.add(new ScriptEdit(file, text, text.replace(oldName, newName)));
                    }
                } catch (IOException ex) {
                    Log.warn("读取启动脚本失败: " + file + " · " + ex.getMessage());
                    return null;
                }
            }
        }
        // 脚本存在但不可写时回退到原地覆盖，保证启动链路不被断开。
        for (Path candidateDir : dirs) {
            for (String name : SCRIPT_NAMES) {
                Path file = candidateDir.resolve(name);
                if (!Files.isRegularFile(file) || Files.isWritable(file)) {
                    continue;
                }
                try {
                    if (Files.readString(file, StandardCharsets.UTF_8).contains(oldName)) {
                        Log.warn("启动脚本不可写，将原地覆盖旧 jar: " + file);
                        return null;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return edits;
    }

    private static void applyScripts(List<ScriptEdit> edits) throws IOException {
        for (ScriptEdit edit : edits) {
            applyScript(edit);
        }
    }

    private static void applyScript(ScriptEdit edit) throws IOException {
        writeTextAtomic(edit.file(), edit.newText());
        makeExecutable(edit.file());
    }

    private static void restoreScripts(List<ScriptEdit> edits) {
        for (ScriptEdit edit : edits) {
            try {
                writeTextAtomic(edit.file(), edit.oldText());
                makeExecutable(edit.file());
            } catch (IOException ex) {
                Log.warn("恢复启动脚本失败: " + edit.file() + " · " + ex.getMessage());
            }
        }
    }

    private static void updateInfoPlist(Path plist, String version, boolean region)
            throws IOException {
        if (!Files.isRegularFile(plist)) {
            return;
        }
        String text = Files.readString(plist, StandardCharsets.UTF_8);
        String display = (region ? "MC Lite Region" : "MC Lite") + " v" + version;
        String updated = replacePlistValue(text, "CFBundleDisplayName", display);
        updated = replacePlistValue(updated, "CFBundleName", display);
        updated = replacePlistValue(updated, "CFBundleShortVersionString", version);
        updated = replacePlistValue(updated, "CFBundleVersion", version);
        if (!updated.equals(text)) {
            writeTextAtomic(plist, updated);
        }
    }

    private static String replacePlistValue(String text, String key, String value) {
        Pattern pattern = Pattern.compile(
                "(<key>" + Pattern.quote(key) + "</key>\\s*<string>)(.*?)(</string>)",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(result -> result.group(1) + value + result.group(3));
    }

    private static Path renameBundle(Path app, String version, boolean region) {
        String name = app.getFileName().toString();
        Matcher matcher = APP_BUNDLE.matcher(name);
        if (!matcher.matches()) {
            return app;
        }
        String desired = (region ? "MC Lite Region" : "MC Lite") + " v" + version + ".app";
        if (desired.equals(name)) {
            return app;
        }
        Path parent = app.getParent();
        if (parent == null) {
            return app;
        }
        Path target = parent.resolve(desired);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Log.warn("应用包目标名称已存在，保留当前名称: " + target);
            return app;
        }
        try {
            Files.move(app, target);
            return target;
        } catch (IOException ex) {
            Log.warn("重命名应用包失败，保留当前名称: " + ex.getMessage());
            return app;
        }
    }

    private static void cleanOldJars(Path dir, Path keep, boolean region) {
        if (dir == null) {
            return;
        }
        String keepName = keep.getFileName().toString();
        try (var files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                if (!name.startsWith("MCLite_v") || !name.endsWith(".jar")) {
                    return;
                }
                if (name.equals(keepName)) {
                    return;
                }
                if (name.endsWith("-region.jar") != region) {
                    return;
                }
                if (!VERSIONED_JAR.matcher(name).matches()) {
                    return;
                }
                deleteQuietly(path);
            });
        } catch (IOException ex) {
            Log.warn("清理旧启动器文件失败: " + ex.getMessage());
        }
    }

    private static void copyAtomically(Path source, Path target) throws IOException {
        Path dir = target.getParent();
        if (dir == null) {
            throw new IOException("无法确定目标目录: " + target);
        }
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, target.getFileName().toString() + ".", ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            copyPermissions(source, temp);
            moveReplace(temp, target);
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void writeTextAtomic(Path file, String text) throws IOException {
        Path dir = file.getParent();
        if (dir == null) {
            throw new IOException("无法确定文件目录: " + file);
        }
        Path temp = Files.createTempFile(dir, file.getFileName().toString() + ".", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            copyPermissions(file, temp);
            moveReplace(temp, file);
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyPermissions(Path source, Path target) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(source);
            Files.setPosixFilePermissions(target, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
        }
    }

    private static void makeExecutable(Path file) {
        try {
            file.toFile().setExecutable(true, false);
        } catch (Exception ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static boolean samePath(Path left, Path right) {
        if (left == null || right == null) {
            return false;
        }
        Path a = left.toAbsolutePath().normalize();
        Path b = right.toAbsolutePath().normalize();
        if (a.equals(b)) {
            return true;
        }
        try {
            return Files.isSameFile(a, b);
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
