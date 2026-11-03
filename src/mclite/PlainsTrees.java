package mclite;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Applies the launcher-wide plains tree density as a small data pack. */
public final class PlainsTrees {
    public static final String MANAGED = "MCLitePlainsTrees.zip";
    public static final String LEGACY = "SparsePlainsTrees.zip";
    public static final String MODE_VANILLA = "vanilla";
    public static final String MODE_SPARSE = "sparse";
    public static final String MODE_NONE = "none";
    public static final String MODE_DENSE = "dense";

    private PlainsTrees() {
    }

    public static boolean valid(String mode) {
        return MODE_VANILLA.equals(mode) || MODE_SPARSE.equals(mode)
                || MODE_NONE.equals(mode) || MODE_DENSE.equals(mode);
    }

    public static void apply(Path gameDir, String mode) throws IOException {
        String value = valid(mode) ? mode : MODE_SPARSE;
        Path global = GlobalDatapacks.folder(gameDir);
        Files.createDirectories(global);
        removeLegacy(gameDir);
        deleteManaged(global);
        if (!MODE_VANILLA.equals(value)) {
            Files.write(global.resolve(MANAGED), zip(value));
        }
        for (Path world : GlobalDatapacks.worlds(gameDir)) {
            deleteManaged(world.resolve("datapacks"));
        }
        if (!MODE_VANILLA.equals(value)) {
            GlobalDatapacks.sync(gameDir);
        }
    }

    public static void cleanupLegacy(Path gameDir) throws IOException {
        removeLegacy(gameDir);
    }

    private static void removeLegacy(Path gameDir) throws IOException {
        Files.deleteIfExists(GlobalDatapacks.folder(gameDir).resolve(LEGACY));
        for (Path world : GlobalDatapacks.worlds(gameDir)) {
            Files.deleteIfExists(world.resolve("datapacks").resolve(LEGACY));
        }
    }

    private static void deleteManaged(Path folder) throws IOException {
        if (folder == null) {
            return;
        }
        Files.deleteIfExists(folder.resolve(MANAGED));
    }

    private static byte[] zip(String mode) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            entry(out, "pack.mcmeta", """
                    {
                      "pack": {
                        "description": "MC Lite plains tree density",
                        "pack_format": 101,
                        "min_format": [101, 1],
                        "max_format": 101
                      }
                    }
                    """);
            entry(out, "data/minecraft/worldgen/placed_feature/trees_plains.json",
                    trees(mode));
        }
        return bytes.toByteArray();
    }

    private static String trees(String mode) {
        String count = switch (mode) {
            case MODE_NONE -> "0";
            case MODE_DENSE -> "8";
            default -> """
                    {
                      "type": "minecraft:weighted_list",
                      "distribution": [
                        {"data": 0, "weight": 99},
                        {"data": 1, "weight": 1}
                      ]
                    }
                    """;
        };
        return """
                {
                  "feature": "minecraft:trees_plains",
                  "placement": [
                    {"type": "minecraft:count", "count": %s},
                    {"type": "minecraft:in_square"},
                    {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
                    {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
                    {
                      "type": "minecraft:block_predicate_filter",
                      "predicate": {
                        "type": "minecraft:would_survive",
                        "state": {"Name": "minecraft:oak_sapling", "Properties": {"stage": "0"}}
                      }
                    },
                    {"type": "minecraft:biome"}
                  ]
                }
                """.formatted(count);
    }

    private static void entry(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
