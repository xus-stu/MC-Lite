package mclite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ContentInstallTest {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getProperty("mclite.home", ".test-home")).toAbsolutePath();
        Path game = Path.of(System.getProperty("mclite.gameDir", ".test-content")).toAbsolutePath();
        Files.createDirectories(home);
        Files.createDirectories(game);
        Log.init(home.resolve("logs/content-test.log"));
        Config config = Config.load();
        TaskProgress progress = new TaskProgress();
        CancellationToken token = new CancellationToken();
        Modrinth modrinth = new Modrinth(progress, token);
        List<Modrinth.Project> projects = modrinth.search("Fabric API", "mod", "1.20.1", "fabric");
        if (projects.isEmpty()) {
            throw new IllegalStateException("no project");
        }
        Modrinth.Project project = projects.get(0);
        System.out.println("project=" + project.title() + " " + project.id());
        ContentInstaller.Result result = new ContentInstaller(config, progress, token)
                .install(project, "1.20.1", "fabric");
        System.out.println("result=" + result.summary() + " dir=" + result.gameDir());
        try (var files = Files.list(game.resolve("mods"))) {
            files.forEach(path -> System.out.println("mod=" + path.getFileName()));
        }
    }
}
