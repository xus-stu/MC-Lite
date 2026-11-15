package mclite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 启动器缓存统计与清理：图标、内容索引、下载中转都在这里。
 *
 * <p>只动 {@link Config#cacheDir()} 下的内容，不碰存档、实例、Java 运行时；
 * 删不掉的文件（被占用）跳过，返回实际释放的字节数。</p>
 */
final class CacheCleaner {

    private CacheCleaner() {
    }

    static long size(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ignored) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    static long clear(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0L;
        }
        long before = size(dir);
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (path.equals(dir)) {
                    continue;
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 占用中的文件留着，下次再清。
                }
            }
        } catch (IOException ignored) {
            // 目录本身读不了就只能返回已释放的部分。
        }
        return Math.max(0L, before - size(dir));
    }
}
