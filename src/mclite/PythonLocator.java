package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Finds Python interpreters from PATH, common install locations and PyCharm venvs. */
final class PythonLocator {
    record Interpreter(Path path, String version) {
        String label() {
            return "Python " + version + "  ·  " + path;
        }
    }

    private PythonLocator() {
    }

    static List<Interpreter> detect() {
        Map<String, Path> candidates = new LinkedHashMap<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                if (dir.isBlank()) {
                    continue;
                }
                addFromDirectory(Path.of(dir), candidates);
            }
        }
        for (String dir : new String[]{"/opt/homebrew/bin", "/usr/local/bin", "/usr/bin",
                System.getProperty("user.home") + "/.pyenv/shims",
                System.getProperty("user.home") + "/miniconda3/bin",
                System.getProperty("user.home") + "/anaconda3/bin",
                System.getProperty("user.home") + "/.local/bin"}) {
            addFromDirectory(Path.of(dir), candidates);
        }
        addPycharmVenvs(candidates);
        List<Interpreter> result = new ArrayList<>();
        for (Path path : candidates.values()) {
            String version = probe(path);
            if (version != null) {
                result.add(new Interpreter(path, version));
            }
        }
        return result;
    }

    private static void addFromDirectory(Path dir, Map<String, Path> out) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var list = Files.list(dir)) {
            for (Path file : list.toList()) {
                String name = file.getFileName().toString();
                if ((name.equals("python") || name.startsWith("python3"))
                        && Files.isRegularFile(file) && Files.isExecutable(file)) {
                    put(out, file);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void addPycharmVenvs(Map<String, Path> out) {
        Path projects = Path.of(System.getProperty("user.home"), "PycharmProjects");
        if (!Files.isDirectory(projects)) {
            return;
        }
        try (var list = Files.list(projects)) {
            for (Path project : list.toList()) {
                if (!Files.isDirectory(project)) {
                    continue;
                }
                for (String venv : new String[]{".venv", "venv", "env"}) {
                    Path python = project.resolve(venv).resolve("bin").resolve("python");
                    if (Files.isRegularFile(python) && Files.isExecutable(python)) {
                        put(out, python);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void put(Map<String, Path> out, Path python) {
        try {
            out.putIfAbsent(python.toRealPath().toString(), python);
        } catch (IOException ex) {
            out.putIfAbsent(python.toAbsolutePath().normalize().toString(), python);
        }
    }

    private static String probe(Path python) {
        try {
            Process process = new ProcessBuilder(python.toString(), "-c",
                    "import sys;print(sys.version.split()[0])")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 && !output.isEmpty() ? output : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
