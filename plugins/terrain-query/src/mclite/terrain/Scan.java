package mclite.terrain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** 存档世界列表、种子/出生点，以及已生成区块的生物群系扫描。 */
final class Scan {

    private Scan() {
    }

    record World(Path dir, String name, long seed, int spawnX, int spawnZ) {
        @Override
        public String toString() {
            return name;
        }
    }

    record BiomeHit(String id, String dimension, int chunks, int chunkX, int chunkZ, long distance) {
        int x() {
            return chunkX * 16 + 8;
        }

        int z() {
            return chunkZ * 16 + 8;
        }
    }

    interface Listener {
        void progress(int done, int total);

        boolean cancelled();
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
            stream.filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                    .forEach(path -> out.add(read(path)));
        } catch (IOException ignored) {
            return out;
        }
        out.sort(Comparator.comparing(World::name));
        return out;
    }

    private static World read(Path dir) {
        long seed = 0L;
        int spawnX = 0;
        int spawnZ = 0;
        String name = dir.getFileName().toString();
        try {
            Map<String, Object> level = Nbt.map(Nbt.at(Nbt.read(dir.resolve("level.dat")), "Data"));
            if (level != null) {
                String levelName = Nbt.text(level.get("LevelName"));
                if (levelName != null && !levelName.isBlank()) {
                    name = levelName;
                }
                if (Nbt.at(level, "spawn", "pos") instanceof int[] pos && pos.length >= 3) {
                    spawnX = pos[0];
                    spawnZ = pos[2];
                }
                Object settings = Nbt.at(level, "WorldGenSettings", "seed");
                if (settings != null) {
                    seed = Nbt.num(settings, 0L);
                }
            }
        } catch (Exception ignored) {
            // 用默认值。
        }
        Path settings = dir.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat");
        if (Files.isRegularFile(settings)) {
            try {
                Map<String, Object> data = Nbt.map(Nbt.at(Nbt.read(settings), "data"));
                if (data != null && data.containsKey("seed")) {
                    seed = Nbt.num(data.get("seed"), seed);
                }
            } catch (Exception ignored) {
                // 忽略。
            }
        }
        return new World(dir, name, seed, spawnX, spawnZ);
    }

    private record Dimension(String id, Path regionDir) {
    }

    /** 扫已生成的区块，按生物群系统计覆盖区块数并记最近坐标。 */
    static List<BiomeHit> biomes(World world, Listener listener) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, int[]> nearest = new LinkedHashMap<>();
        List<Dimension> dimensions = dimensions(world.dir());
        int total = dimensions.size() * 1024;
        int done = 0;
        for (Dimension dimension : dimensions) {
            for (Path file : regionFiles(dimension.regionDir())) {
                if (listener != null && listener.cancelled()) {
                    return result(counts, nearest, dimension.id());
                }
                try {
                    scanRegion(file, dimension.id(), world, counts, nearest);
                } catch (Exception ignored) {
                    // 坏区域跳过。
                }
                done += 1024;
                if (listener != null) {
                    listener.progress(done, total);
                }
            }
        }
        return result(counts, nearest, "");
    }

    private static List<BiomeHit> result(Map<String, int[]> counts, Map<String, int[]> nearest,
                                         String unused) {
        List<BiomeHit> out = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int[] n = nearest.get(entry.getKey());
            out.add(new BiomeHit(entry.getKey(), "",
                    entry.getValue()[0], n == null ? 0 : n[0], n == null ? 0 : n[1],
                    n == null ? 0 : n[2]));
        }
        out.sort((a, b) -> Integer.compare(b.chunks(), a.chunks()));
        return out;
    }

    private static List<Dimension> dimensions(Path saveDir) {
        List<Dimension> out = new ArrayList<>();
        Path legacy = saveDir.resolve("region");
        if (Files.isDirectory(legacy)) {
            out.add(new Dimension("主世界", legacy));
        }
        Path root = saveDir.resolve("dimensions");
        if (Files.isDirectory(root)) {
            try (Stream<Path> namespaces = Files.list(root)) {
                for (Path namespace : namespaces.sorted().toList()) {
                    String ns = namespace.getFileName().toString();
                    if (Files.isDirectory(namespace.resolve("region"))) {
                        out.add(new Dimension(label(ns), namespace.resolve("region")));
                    }
                    try (Stream<Path> inner = Files.list(namespace)) {
                        for (Path path : inner.sorted().toList()) {
                            if (Files.isDirectory(path.resolve("region"))) {
                                out.add(new Dimension(label(ns + ":" + path.getFileName()),
                                        path.resolve("region")));
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
                // 忽略。
            }
        }
        return out;
    }

    private static String label(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> id;
        };
    }

    private static List<Path> regionFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static void scanRegion(Path file, String dimension, World world,
                                   Map<String, int[]> counts, Map<String, int[]> nearest)
            throws IOException {
        byte[] data = Files.readAllBytes(file);
        if (data.length < 8192) {
            return;
        }
        for (int index = 0; index < 1024; index++) {
            int offset = intAt(data, index * 4);
            if (offset == 0) {
                continue;
            }
            long at = (long) (offset >>> 8) * 4096L;
            if (at + 5 > data.length) {
                continue;
            }
            int length = intAt(data, (int) at);
            int compression = data[(int) at + 4] & 0xFF;
            if (length <= 0 || at + 5 + length > data.length) {
                continue;
            }
            byte[] nbt = decompress(data, (int) at + 5, length, compression);
            if (nbt == null) {
                continue;
            }
            collect(nbt, dimension, world, counts, nearest);
        }
    }

    private static byte[] decompress(byte[] data, int offset, int length, int compression) {
        try {
            if (compression == 1) {
                try (GZIPInputStream in = new GZIPInputStream(
                        new ByteArrayInputStream(data, offset, length))) {
                    return in.readAllBytes();
                }
            }
            if (compression == 2) {
                try (InflaterInputStream in = new InflaterInputStream(
                        new ByteArrayInputStream(data, offset, length))) {
                    return in.readAllBytes();
                }
            }
            if (compression == 3) {
                return Arrays.copyOfRange(data, offset, offset + length);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static void collect(byte[] nbt, String dimension, World world,
                                Map<String, int[]> counts, Map<String, int[]> nearest) {
        Map<String, Object> root;
        try {
            root = Nbt.parse(nbt);
        } catch (Exception ignored) {
            return;
        }
        Map<String, Object> level = Nbt.map(root.get("Level"));
        Map<String, Object> chunk = level == null ? root : level;
        int chunkX = (int) Nbt.num(chunk.get("xPos"), Integer.MIN_VALUE);
        int chunkZ = (int) Nbt.num(chunk.get("zPos"), Integer.MIN_VALUE);
        if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
            return;
        }
        java.util.Set<String> found = new java.util.LinkedHashSet<>();
        for (Object raw : Nbt.list(chunk.get("sections")) == null
                ? List.of() : Nbt.list(chunk.get("sections"))) {
            Map<String, Object> section = Nbt.map(raw);
            if (section == null) {
                continue;
            }
            Map<String, Object> biomes = Nbt.map(section.get("biomes"));
            if (biomes == null) {
                continue;
            }
            for (Object entry : Nbt.list(biomes.get("palette")) == null
                    ? List.of() : Nbt.list(biomes.get("palette"))) {
                String id = Nbt.text(entry);
                if (id != null && !id.isBlank()) {
                    found.add(id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
                }
            }
        }
        if (found.isEmpty()) {
            return;
        }
        long dx = (long) chunkX * 16 + 8 - world.spawnX();
        long dz = (long) chunkZ * 16 + 8 - world.spawnZ();
        long distance = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        for (String id : found) {
            counts.computeIfAbsent(id, key -> new int[1])[0]++;
            int[] best = nearest.get(id);
            if (best == null || distance < best[2]) {
                nearest.put(id, new int[]{chunkX, chunkZ, (int) distance});
            }
        }
    }

    private static int intAt(byte[] data, int index) {
        return ((data[index] & 0xFF) << 24) | ((data[index + 1] & 0xFF) << 16)
                | ((data[index + 2] & 0xFF) << 8) | (data[index + 3] & 0xFF);
    }
}
