package mclite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.stream.Stream;

/**
 * 扫描存档的 region 文件，读出区块里 {@code structures.starts} 记录的结构起点。
 *
 * <p>这是「已经生成」的结构，比按种子硬算准，而且模组结构（mvs / structory / ctov 等）
 * 也能列出来。只读区块头，不改存档。</p>
 */
final class StructureScan {

    private StructureScan() {
    }

    record Found(String id, String dimension, int chunkX, int chunkZ) {
        int x() {
            return chunkX * 16 + 8;
        }

        int z() {
            return chunkZ * 16 + 8;
        }

        String dimensionLabel() {
            return switch (dimension) {
                case "minecraft:overworld" -> "主世界";
                case "minecraft:the_nether" -> "下界";
                case "minecraft:the_end" -> "末地";
                default -> dimension;
            };
        }
    }

    interface Listener {
        void progress(int done, int total);

        boolean cancelled();
    }

    private record Dimension(String id, Path regionDir) {
    }

    static List<Found> scan(Path saveDir, Listener listener) {
        List<Found> found = new ArrayList<>();
        if (saveDir == null || !Files.isDirectory(saveDir)) {
            return found;
        }
        List<Dimension> dimensions = dimensions(saveDir);
        int total = dimensions.size() * 1024;
        int done = 0;
        for (Dimension dimension : dimensions) {
            List<Path> files = regionFiles(dimension.regionDir());
            for (Path file : files) {
                if (listener != null && listener.cancelled()) {
                    return found;
                }
                try {
                    readRegion(file, dimension.id(), found);
                } catch (IOException | RuntimeException ignored) {
                    // 损坏/空区域跳过，不影响其它区域。
                }
                done += 1024;
                if (listener != null) {
                    listener.progress(done, total);
                }
            }
        }
        return found;
    }

    static List<Dimension> dimensions(Path saveDir) {
        List<Dimension> out = new ArrayList<>();
        Path legacy = saveDir.resolve("region");
        if (Files.isDirectory(legacy)) {
            out.add(new Dimension("minecraft:overworld", legacy));
        }
        Path root = saveDir.resolve("dimensions");
        if (Files.isDirectory(root)) {
            for (Path namespace : children(root)) {
                String ns = namespace.getFileName().toString();
                if (Files.isDirectory(namespace.resolve("region"))) {
                    out.add(new Dimension(ns, namespace.resolve("region")));
                }
                for (Path path : children(namespace)) {
                    if (Files.isDirectory(path.resolve("region"))) {
                        out.add(new Dimension(ns + ":" + path.getFileName(), path.resolve("region")));
                    }
                }
            }
        }
        return out;
    }

    private static List<Path> children(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static List<Path> regionFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static void readRegion(Path file, String dimension, List<Found> found) throws IOException {
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
            collect(nbt, dimension, found);
        }
    }

    private static byte[] decompress(byte[] data, int offset, int length, int compression)
            throws IOException {
        try {
            switch (compression) {
                case 1:
                    try (GZIPInputStream in = new GZIPInputStream(
                            new ByteArrayInputStream(data, offset, length))) {
                        return in.readAllBytes();
                    }
                case 2:
                    try (InflaterInputStream in = new InflaterInputStream(
                            new ByteArrayInputStream(data, offset, length))) {
                        return in.readAllBytes();
                    }
                case 3:
                    return Arrays.copyOfRange(data, offset, offset + length);
                default:
                    return null;
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static void collect(byte[] nbt, String dimension, List<Found> found) {
        Map<String, Object> root;
        try {
            root = Nbt.parse(nbt);
        } catch (IOException | RuntimeException ignored) {
            return;
        }
        Map<String, Object> level = Nbt.map(root.get("Level"));
        Map<String, Object> starts = Nbt.map(Nbt.at(level == null ? root : level, "structures", "starts"));
        if (starts == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : starts.entrySet()) {
            Map<String, Object> start = Nbt.map(entry.getValue());
            if (start == null) {
                continue;
            }
            String id = Nbt.text(start.get("id"));
            if (id == null || id.isBlank()) {
                id = entry.getKey();
            }
            int chunkX = (int) Nbt.num(start.get("ChunkX"), Integer.MIN_VALUE);
            int chunkZ = (int) Nbt.num(start.get("ChunkZ"), Integer.MIN_VALUE);
            if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
                continue;
            }
            found.add(new Found(id, dimension, chunkX, chunkZ));
        }
    }

    private static int intAt(byte[] data, int index) {
        return ((data[index] & 0xFF) << 24) | ((data[index + 1] & 0xFF) << 16)
                | ((data[index + 2] & 0xFF) << 8) | (data[index + 3] & 0xFF);
    }

    /** 结构中文名；未知的模组结构回落到原始 id。 */
    static String label(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String key = path.toLowerCase(java.util.Locale.ROOT);
        String vanilla = switch (key) {
            case "stronghold" -> "要塞（末地门）";
            case "fortress" -> "下界要塞";
            case "bastion_remnant" -> "堡垒遗迹";
            case "end_city" -> "末地城";
            case "ancient_city" -> "远古城市";
            case "trial_chambers" -> "试炼密室";
            case "mineshaft", "mineshaft_mesa" -> "废弃矿井";
            case "monument" -> "海底神殿";
            case "mansion" -> "林地府邸";
            case "pillager_outpost" -> "掠夺者前哨站";
            case "shipwreck", "shipwreck_beached" -> "沉船";
            case "buried_treasure" -> "埋藏的宝藏";
            case "desert_pyramid" -> "沙漠神殿";
            case "jungle_pyramid" -> "丛林神庙";
            case "igloo" -> "雪屋";
            case "swamp_hut" -> "女巫小屋";
            case "trail_ruins" -> "古迹废墟";
            case "ocean_ruin_cold", "ocean_ruin_warm" -> "海底废墟";
            case "ruined_portal" -> "废弃传送门";
            case "village_plains" -> "平原村庄";
            case "village_desert" -> "沙漠村庄";
            case "village_savanna" -> "热带草原村庄";
            case "village_snowy" -> "雪原村庄";
            case "village_taiga" -> "针叶林村庄";
            default -> null;
        };
        if (vanilla != null) {
            return vanilla;
        }
        if (key.startsWith("ruined_portal")) {
            return "废弃传送门";
        }
        if (key.contains("village")) {
            return "村庄";
        }
        if (key.contains("fortress")) {
            return "要塞 / 堡垒";
        }
        if (key.contains("tower")) {
            return "高塔";
        }
        if (key.contains("temple") || key.contains("shrine")) {
            return "神庙";
        }
        if (key.contains("ruin")) {
            return "遗迹";
        }
        if (key.contains("dungeon")) {
            return "地牢";
        }
        if (key.contains("outpost")) {
            return "前哨站";
        }
        return id;
    }

    /** 默认筛选里「常见遗迹」包含的结构：原版主要结构 + 明显是遗迹的模组结构。 */
    static boolean common(String id) {
        String key = id.toLowerCase(java.util.Locale.ROOT);
        for (String part : new String[]{"stronghold", "fortress", "bastion", "end_city",
                "ancient_city", "trial_chambers", "mineshaft", "monument", "mansion",
                "outpost", "temple", "shrine", "ruin", "dungeon", "village", "tower",
                "pyramid", "igloo", "hut", "shipwreck", "treasure", "trail_ruins"}) {
            if (key.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
