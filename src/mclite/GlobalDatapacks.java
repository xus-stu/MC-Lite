package mclite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipFile;

/** Instance-level datapacks that are copied into every world before launch. */
public final class GlobalDatapacks {
    public static final String FOLDER = "datapacks";

    private GlobalDatapacks() {
    }

    public static Path folder(Path gameDir) {
        return gameDir.resolve(FOLDER);
    }

    public static List<Path> worlds(Path gameDir) throws IOException {
        Path saves = gameDir.resolve("saves");
        if (!Files.isDirectory(saves)) {
            return List.of();
        }
        List<Path> worlds = new ArrayList<>();
        try (var dirs = Files.newDirectoryStream(saves)) {
            for (Path dir : dirs) {
                if (Files.isDirectory(dir) && Files.isRegularFile(dir.resolve("level.dat"))) {
                    worlds.add(dir);
                }
            }
        }
        worlds.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return worlds;
    }

    public static Path install(Path gameDir, Path archive) throws IOException {
        validate(archive);
        Path folder = folder(gameDir);
        Files.createDirectories(folder);
        Path target = folder.resolve(archive.getFileName().toString());
        Files.copy(archive, target, StandardCopyOption.REPLACE_EXISTING);
        sync(gameDir);
        return target;
    }

    public static void validate(Path archive) throws IOException {
        if (archive == null || !Files.isRegularFile(archive)) {
            throw new IOException("数据包文件不存在");
        }
        if (!archive.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            throw new IOException("数据包需要是 .zip 文件");
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            if (zip.getEntry("pack.mcmeta") == null) {
                throw new IOException("ZIP 根目录缺少 pack.mcmeta");
            }
        }
    }

    /** Copy global datapacks into every existing world. Returns the number of worlds touched. */
    public static int sync(Path gameDir) throws IOException {
        Path folder = folder(gameDir);
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        List<Path> packs = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file)
                        && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".zip")) {
                    packs.add(file);
                }
            }
        }
        if (packs.isEmpty()) {
            return 0;
        }
        int worlds = 0;
        for (Path world : worlds(gameDir)) {
            Path targetDir = world.resolve("datapacks");
            Files.createDirectories(targetDir);
            for (Path pack : packs) {
                Path target = targetDir.resolve(pack.getFileName().toString());
                if (!Files.isRegularFile(target) || Files.size(target) != Files.size(pack)
                        || Files.getLastModifiedTime(target).toMillis()
                        != Files.getLastModifiedTime(pack).toMillis()) {
                    Files.copy(pack, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            worlds++;
        }
        return worlds;
    }
}
