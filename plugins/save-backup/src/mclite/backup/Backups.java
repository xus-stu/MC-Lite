package mclite.backup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 存档目录枚举、zip 备份/恢复。 */
final class Backups {

    private Backups() {
    }

    record World(Path dir, String name, long bytes) {
        @Override
        public String toString() {
            return name;
        }
    }

    record Archive(Path file, String world, Date time, long bytes) {
    }

    static List<World> worlds(Path instanceDir) {
        List<World> out = new ArrayList<>();
        if (instanceDir == null) {
            return out;
        }
        Path saves = instanceDir.resolve("saves");
        if (!Files.isDirectory(saves)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(saves)) {
            for (Path dir : stream.toList()) {
                if (Files.isRegularFile(dir.resolve("level.dat"))) {
                    out.add(new World(dir, dir.getFileName().toString(), size(dir)));
                }
            }
        } catch (IOException ignored) {
            return out;
        }
        out.sort(Comparator.comparing(w -> w.dir().getFileName().toString()));
        return out;
    }

    static List<Archive> archives(Path backupsDir) {
        List<Archive> out = new ArrayList<>();
        if (!Files.isDirectory(backupsDir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(backupsDir)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".zip")) {
                    continue;
                }
                String base = name.substring(0, name.length() - 4);
                int split = base.lastIndexOf("--");
                String world = split > 0 ? base.substring(0, split) : base;
                Date time = null;
                if (split > 0) {
                    try {
                        time = new SimpleDateFormat("yyyyMMdd-HHmmss")
                                .parse(base.substring(split + 2));
                    } catch (Exception ignored) {
                        // 文件名不是标准时间就按修改时间排。
                    }
                }
                if (time == null) {
                    time = new Date(file.toFile().lastModified());
                }
                out.add(new Archive(file, world, time, size(file)));
            }
        } catch (IOException ignored) {
            return out;
        }
        out.sort(Comparator.comparing(Archive::time).reversed());
        return out;
    }

    static long size(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ex) {
                            return 0L;
                        }
                    }).sum();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    /** 把世界目录打包成 zip（session.lock 跳过）。 */
    static Path backup(Path worldDir, Path backupsDir) throws IOException {
        Files.createDirectories(backupsDir);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Path target = backupsDir.resolve(worldDir.getFileName() + "--" + stamp + ".zip");
        for (int n = 2; Files.exists(target); n++) {
            target = backupsDir.resolve(worldDir.getFileName() + "--" + stamp + "-" + n + ".zip");
        }
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                Files.newOutputStream(target)))) {
            try (Stream<Path> stream = Files.walk(worldDir)) {
                List<Path> files = stream.filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().equals("session.lock"))
                        .sorted().toList();
                for (Path file : files) {
                    String entryName = worldDir.relativize(file).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(Files.getLastModifiedTime(file).toMillis());
                    zip.putNextEntry(entry);
                    try (InputStream in = Files.newInputStream(file)) {
                        in.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        } catch (IOException ex) {
            Files.deleteIfExists(target);
            throw ex;
        }
        return target;
    }

    /** 恢复：先把现有世界改名保留，再解压。 */
    static Path restore(Path savesDir, Path backupsDir, Path archive, String worldName,
                        boolean backupCurrent) throws IOException {
        Path target = savesDir.resolve(worldName);
        Path keep = null;
        if (Files.isDirectory(target)) {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            if (backupCurrent) {
                backup(target, backupsDir);
            }
            keep = savesDir.resolve(worldName + ".恢复前-" + stamp);
            Files.move(target, keep, StandardCopyOption.REPLACE_EXISTING);
        }
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(
                Files.newInputStream(archive)))) {
            Files.createDirectories(target);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = target.resolve(entry.getName()).normalize();
                if (!out.startsWith(target)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (BufferedOutputStream bos = new BufferedOutputStream(
                            Files.newOutputStream(out))) {
                        zip.transferTo(bos);
                    }
                }
            }
        } catch (IOException ex) {
            if (keep != null && !Files.exists(target)) {
                Files.move(keep, target, StandardCopyOption.REPLACE_EXISTING);
            }
            throw ex;
        }
        return keep;
    }

    static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.0f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / 1048576.0);
        }
        return String.format("%.2f GB", bytes / 1073741824.0);
    }
}
