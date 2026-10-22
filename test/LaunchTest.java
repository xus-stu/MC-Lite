package mclite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LaunchTest {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getProperty("mclite.home", ".test-home")).toAbsolutePath();
        Path game = Path.of(System.getProperty("mclite.gameDir", ".test-game")).toAbsolutePath();
        Log.init(home.resolve("logs/launch-test.log"));
        Config config = Config.load();
        config.gameDir(game);
        config.username("TestUser");
        config.memoryMb(2048);
        VersionManifest manifest = Versions.fetch(config, ProgressSink.NOOP, new CancellationToken()).manifest();
        String version = args.length > 0 ? args[0] : "1.21.4";
        VersionManifest.VersionEntry entry = manifest.find(version);
        VersionMeta meta = VersionMeta.parse(Files.readString(
                new GamePaths(game).versionJson(version)), version);
        Path java = home.resolve("java/java-runtime-epsilon/jre.bundle/Contents/Home/bin/java");
        if (!Files.isRegularFile(java)) {
            throw new IllegalStateException("Java 25 runtime missing: " + java);
        }
        System.out.println("launching " + entry.id() + " with " + java);
        GameLauncher.LaunchResult result = GameLauncher.launch(config, meta, entry,
                new JavaManager.JavaInfo(java, 25, "Java 25", "java-runtime-epsilon", true));
        System.out.println("pid=" + result.process().pid() + " log=" + result.logFile());
        long deadline = System.currentTimeMillis() + 25_000;
        while (System.currentTimeMillis() < deadline && result.process().isAlive()) {
            Thread.sleep(1000);
        }
        System.out.println("aliveAfter25s=" + result.process().isAlive()
                + " exit=" + (result.process().isAlive() ? "-" : result.process().exitValue()));
        List<String> tail = Files.readAllLines(result.logFile());
        int from = Math.max(0, tail.size() - 60);
        for (int i = from; i < tail.size(); i++) {
            System.out.println("LOG " + tail.get(i));
        }
        if (result.process().isAlive()) {
            result.process().destroy();
            result.process().waitFor();
            System.out.println("destroyed");
        }
    }
}
