package mclite.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** 存档 level.dat 的读取与定点修改（改前备份）。 */
final class LevelFile {

    static final int SURVIVAL = 0;
    static final int CREATIVE = 1;
    static final int ADVENTURE = 2;
    static final int SPECTATOR = 3;

    private LevelFile() {
    }

    record World(Path dir, String name, int gameType, boolean hardcore,
                 boolean allowCommands, long time, int[] spawn, double angle) {
        @Override
        public String toString() {
            String mode = switch (gameType) {
                case CREATIVE -> "创造";
                case ADVENTURE -> "冒险";
                case SPECTATOR -> "旁观";
                default -> "生存";
            };
            return name + " · " + (hardcore ? "极限" : mode);
        }
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

    static World read(Path dir) {
        String name = dir.getFileName().toString();
        int gameType = 0;
        boolean hardcore = false;
        boolean allowCommands = false;
        long time = 0L;
        int[] spawn = {0, 0, 0};
        double angle = 0;
        try {
            byte[] data = Nbt.gunzip(Files.readAllBytes(dir.resolve("level.dat")));
            Map<String, Object> root = Nbt.parse(data).root();
            Map<String, Object> level = Nbt.map(root.get("Data"));
            if (level != null) {
                String levelName = Nbt.text(level.get("LevelName"));
                if (levelName != null && !levelName.isBlank()) {
                    name = levelName;
                }
                gameType = (int) Nbt.num(level.get("GameType"), 0);
                allowCommands = Nbt.num(level.get("allowCommands"), 0) != 0;
                time = Nbt.num(level.get("Time"), 0L);
                Map<String, Object> diff = Nbt.map(level.get("difficulty_settings"));
                hardcore = diff != null && Nbt.num(diff.get("hardcore"), 0) != 0;
                if (Nbt.at(level, "spawn", "pos") instanceof int[] pos && pos.length >= 3) {
                    spawn = new int[]{pos[0], pos[1], pos[2]};
                }
                Object spawnAngle = Nbt.at(level, "spawn", "angle");
                if (spawnAngle instanceof Number number) {
                    angle = number.doubleValue();
                }
            }
        } catch (Exception ignored) {
            // 读不到就返回默认值。
        }
        return new World(dir, name, gameType, hardcore, allowCommands, time, spawn, angle);
    }

    /** 修改结果：备份文件名 + 实际改动的项。 */
    record Result(Path backup, List<String> changes) {
    }

    /**
     * 保存修改。hardcore 存档不允许关闭极限或改回普通生存。
     */
    static Result save(World world, int gameType, boolean hardcore, boolean allowCommands,
                       Long timeOverride) throws IOException {
        if (world.hardcore() && !hardcore) {
            throw new IOException("极限存档不允许关闭极限，也就不能改回普通生存（可以改创造/冒险/旁观）");
        }
        if (!world.hardcore() && hardcore) {
            // 升级极限时游戏模式必须是生存。
            gameType = SURVIVAL;
        }
        Path levelDat = world.dir().resolve("level.dat");
        byte[] raw = Files.readAllBytes(levelDat);
        byte[] data = Nbt.gunzip(raw);
        Nbt.Parsed parsed = Nbt.parse(data);
        List<String> changes = new ArrayList<>();
        if (gameType != world.gameType()) {
            Nbt.putInt(data, parsed.offsets(), "Data.GameType", gameType);
            changes.add("游戏模式 → " + modeName(gameType));
        }
        if (hardcore != world.hardcore()) {
            Nbt.putByte(data, parsed.offsets(), "Data.difficulty_settings.hardcore",
                    hardcore ? 1 : 0);
            changes.add("极限 → " + (hardcore ? "开" : "关"));
        }
        if (allowCommands != world.allowCommands()) {
            Nbt.putByte(data, parsed.offsets(), "Data.allowCommands", allowCommands ? 1 : 0);
            changes.add("允许命令 → " + (allowCommands ? "开" : "关"));
        }
        if (timeOverride != null && timeOverride >= 0) {
            Nbt.putLong(data, parsed.offsets(), "Data.Time", timeOverride);
            Nbt.putLong(data, parsed.offsets(), "Data.DayTime", timeOverride);
            changes.add("时间 → " + timeOverride);
        }
        if (changes.isEmpty()) {
            return new Result(null, changes);
        }
        Path backup = levelDat.resolveSibling("level.dat.bak-" + System.currentTimeMillis());
        Files.copy(levelDat, backup, StandardCopyOption.REPLACE_EXISTING);
        Path temp = levelDat.resolveSibling("level.dat.tmp");
        Nbt.write(temp, Nbt.gzip(data));
        Files.move(temp, levelDat, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        return new Result(backup, changes);
    }

    static String modeName(int gameType) {
        return switch (gameType) {
            case CREATIVE -> "创造";
            case ADVENTURE -> "冒险";
            case SPECTATOR -> "旁观";
            default -> "生存";
        };
    }

    /** 时间预设：返回 null 表示不改。 */
    static Long timePreset(String label) {
        return switch (label) {
            case "白天" -> 1000L;
            case "正午" -> 6000L;
            case "夜晚" -> 13000L;
            case "午夜" -> 18000L;
            default -> null;
        };
    }

    static String timeLabel(long time) {
        long t = ((time % 24000L) + 24000L) % 24000L;
        if (t < 1000 || t >= 23000) {
            return "日出/清晨";
        }
        if (t < 6000) {
            return "白天";
        }
        if (t < 12000) {
            return "下午";
        }
        if (t < 13000) {
            return "日落";
        }
        return "夜晚";
    }

    static String modeLine(World world) {
        String mode = world.hardcore()
                ? ("极限（" + modeName(world.gameType()) + "）")
                : modeName(world.gameType());
        return "模式：" + mode
                + " · 允许命令：" + (world.allowCommands() ? "开" : "关")
                + " · 时间：" + timeLabel(world.time())
                + " · 出生点 " + world.spawn()[0] + ", " + world.spawn()[1] + ", " + world.spawn()[2];
    }
}
