package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Installs the bundled pure-Python bridge client into a user-selected interpreter. */
final class PythonModuleInstaller {
    private static final List<String> FILES = List.of("__init__.py", "client.py", "ui_pyside6.py");

    private PythonModuleInstaller() {
    }

    static void install(Path python) throws Exception {
        Path temp = Files.createTempDirectory("mclite-pybridge-");
        try {
            Path packageDir = temp.resolve("mclite_bridge");
            Files.createDirectories(packageDir);
            copy("pyproject.toml", temp.resolve("pyproject.toml"));
            for (String name : FILES) {
                copy("mclite_bridge/" + name, packageDir.resolve(name));
            }
            Process process = new ProcessBuilder(python.toString(), "-m", "pip", "install",
                    "--upgrade", temp.toString())
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            int code = process.waitFor();
            if (code != 0) {
                throw new IOException("pip 退出码 " + code + "\n\n" + output);
            }
            if (output.isEmpty()) {
                throw new IOException("pip 没有输出，请确认所选文件是可用的 Python 解释器。");
            }
        } finally {
            deleteTree(temp);
        }
    }

    private static void copy(String resource, Path target) throws IOException {
        try (InputStream in = PythonModuleInstaller.class.getResourceAsStream("/mclite/pybridge/" + resource)) {
            if (in == null) {
                throw new IOException("JAR 内缺少 Python 模块资源: " + resource);
            }
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) {
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }
}
