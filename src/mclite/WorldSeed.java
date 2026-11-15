package mclite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 读取实例里存档的世界信息：名字、版本、最后游玩、种子、出生点。
 *
 * <p>26.x 把世界生成设置拆到了 {@code data/minecraft/world_gen_settings.dat}；
 * 旧版本还在 {@code level.dat} 的 {@code Data.WorldGenSettings}。两处都读。</p>
 */
final class WorldSeed {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private WorldSeed() {
    }

    record World(Path dir, String name, long lastPlayed, String version, long seed,
                 Map<String, Long> dimensionSeeds) {
        boolean hasSeed() {
            return seed != 0L || !dimensionSeeds.isEmpty();
        }

        String when() {
            return lastPlayed <= 0 ? "" : TIME.format(Instant.ofEpochMilli(lastPlayed));
        }

        String label() {
            StringBuilder text = new StringBuilder(name);
            if (!version.isBlank()) {
                text.append(" · ").append(version);
            }
            String when = when();
            if (!when.isBlank()) {
                text.append(" · ").append(when);
            }
            return text.toString();
        }

        @Override
        public String toString() {
            return label();
        }

        Long seedOf(String dimension) {
            return dimensionSeeds.getOrDefault(dimension, seed);
        }
    }

    private record Seed(long seed, Map<String, Long> dimensions) {
    }

    static List<World> list(Path instanceDir) {
        List<World> worlds = new ArrayList<>();
        if (instanceDir == null) {
            return worlds;
        }
        Path saves = instanceDir.resolve("saves");
        if (!Files.isDirectory(saves)) {
            return worlds;
        }
        try (Stream<Path> stream = Files.list(saves)) {
            stream.filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                    .forEach(path -> worlds.add(read(path)));
        } catch (IOException ignored) {
            return worlds;
        }
        worlds.sort(Comparator.comparingLong(World::lastPlayed).reversed()
                .thenComparing(World::name));
        return worlds;
    }

    private static World read(Path dir) {
        Map<String, Object> level = null;
        try {
            level = Nbt.map(Nbt.at(Nbt.read(dir.resolve("level.dat")), "Data"));
        } catch (IOException | RuntimeException ignored) {
            level = null;
        }
        String name = level == null ? null : Nbt.text(level.get("LevelName"));
        if (name == null || name.isBlank()) {
            name = dir.getFileName().toString();
        }
        String version = level == null ? ""
                : String.valueOf(Nbt.text(Nbt.at(level, "Version", "Name")) == null ? ""
                : Nbt.text(Nbt.at(level, "Version", "Name")));
        long lastPlayed = level == null ? 0L : Nbt.num(level.get("LastPlayed"), 0L);
        Seed seed = seed(dir, level);
        return new World(dir, name, lastPlayed, version, seed.seed(), seed.dimensions());
    }

    private static Seed seed(Path dir, Map<String, Object> level) {
        // 26.x：世界生成设置独立成文件。
        Path settings = dir.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat");
        if (Files.isRegularFile(settings)) {
            try {
                Map<String, Object> data = Nbt.map(Nbt.at(Nbt.read(settings), "data"));
                if (data != null && data.containsKey("seed")) {
                    return new Seed(Nbt.num(data.get("seed"), 0L), dimensions(data));
                }
            } catch (IOException | RuntimeException ignored) {
                // 落到 level.dat 的旧格式。
            }
        }
        if (level != null) {
            Map<String, Object> worldGen = Nbt.map(level.get("WorldGenSettings"));
            if (worldGen != null && worldGen.containsKey("seed")) {
                return new Seed(Nbt.num(worldGen.get("seed"), 0L), dimensions(worldGen));
            }
            if (level.containsKey("RandomSeed")) {
                return new Seed(Nbt.num(level.get("RandomSeed"), 0L), Map.of());
            }
        }
        return new Seed(0L, Map.of());
    }

    private static Map<String, Long> dimensions(Map<String, Object> settings) {
        Map<String, Long> out = new LinkedHashMap<>();
        Map<String, Object> dimensions = Nbt.map(settings.get("dimensions"));
        if (dimensions == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : dimensions.entrySet()) {
            Map<String, Object> dimension = Nbt.map(entry.getValue());
            if (dimension != null && dimension.containsKey("seed")) {
                out.put(entry.getKey(), Nbt.num(dimension.get("seed"), 0L));
            }
        }
        return out;
    }

    /** 出生点 XZ；读不到就回落到 0,0。 */
    static int[] spawn(Path worldDir) {
        try {
            Object pos = Nbt.at(Nbt.read(worldDir.resolve("level.dat")), "Data", "spawn", "pos");
            if (pos instanceof int[] values && values.length >= 3) {
                return new int[]{values[0], values[2]};
            }
        } catch (IOException | RuntimeException ignored) {
            // 用默认值。
        }
        return new int[]{0, 0};
    }
}
