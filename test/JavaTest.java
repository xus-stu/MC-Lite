package mclite;

import java.nio.file.Files;
import java.nio.file.Path;

public final class JavaTest {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getProperty("mclite.home", ".test-home")).toAbsolutePath();
        Files.createDirectories(home);
        Log.init(home.resolve("logs/java-test.log"));
        Config config = Config.load();
        TaskProgress progress = new TaskProgress();
        CancellationToken token = new CancellationToken();
        JavaManager manager = new JavaManager(config, progress, token);
        String component = args.length > 0 ? args[0] : "java-runtime-epsilon";
        int major = args.length > 1 ? Integer.parseInt(args[1]) : 25;
        System.out.println("platform=" + JavaManager.platformKey());
        System.out.println("detected=" + manager.detectAll());
        JavaManager.JavaInfo info = manager.install(component, major);
        System.out.println("installed=" + info);
        System.out.println("probe=" + JavaManager.probe(info.path()));
        System.out.println("OK");
    }
}
