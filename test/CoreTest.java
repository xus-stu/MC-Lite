package mclite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CoreTest {
    public static void main(String[] args) throws Exception {
        String version = args.length > 0 ? args[0] : "1.2.5";
        boolean assets = args.length <= 1 || !args[1].equals("--no-assets");
        Path home = Path.of(System.getProperty("mclite.home", ".test-home")).toAbsolutePath();
        Path game = Path.of(System.getProperty("mclite.gameDir", ".test-game")).toAbsolutePath();
        Files.createDirectories(home);
        Files.createDirectories(game);
        Log.init(home.resolve("logs/test.log"));
        Config config = Config.load();
        System.out.println("home=" + home);
        System.out.println("game=" + game);

        TaskProgress progress = new TaskProgress();
        CancellationToken token = new CancellationToken();
        Versions.FetchResult result = Versions.fetch(config, progress, token);
        System.out.println("manifest versions=" + result.manifest().versions().size()
                + " latest=" + result.manifest().latestRelease()
                + " cached=" + result.fromCache());

        VersionManifest.VersionEntry entry = result.manifest().find(version);
        if (entry == null) {
            throw new IllegalStateException("version not found: " + version);
        }
        System.out.println("selected=" + entry.id() + " type=" + entry.type() + " url=" + entry.url());

        Installer installer = new Installer(config, token, progress);
        Installer.InstallResult installed = installer.install(entry, assets);
        System.out.println("installed=" + installed.meta().id()
                + " assetsComplete=" + installed.assetsComplete()
                + " virtual=" + installed.virtualAssets()
                + " mapped=" + installed.mappedResources()
                + " estimated=" + Formatting.bytes(installed.estimatedBytes()));
        System.out.println("javaRequired=" + installed.meta().javaMajor()
                + " component=" + installed.meta().javaComponent());

        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = GameLauncher.buildCommand(config, installed.meta(), entry, java, true);
        System.out.println("command parts=" + command.size());
        for (int i = 0; i < Math.min(command.size(), 24); i++) {
            System.out.println("  [" + i + "] " + command.get(i));
        }
        System.out.println("OK");
    }
}
