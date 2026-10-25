package mclite;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkinStore {
    private SkinStore() {
    }

    public static Path skinFile(Config config, String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }
        String safe = accountId.replaceAll("[^A-Za-z0-9._-]", "_");
        return config.accountsDir().resolve("skins").resolve(safe + ".png");
    }

    public static Image load(Config config, String accountId) {
        Path file = skinFile(config, accountId);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return ImageIO.read(file.toFile());
        } catch (IOException ex) {
            return null;
        }
    }

    public static void save(Config config, String accountId, Path source) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            throw new IOException("不是可识别的图片文件");
        }
        int w = image.getWidth();
        int h = image.getHeight();
        if (!(w == 64 && (h == 64 || h == 32))) {
            throw new IOException("Minecraft 皮肤必须是 64×64 或 64×32 的 PNG");
        }
        Path target = skinFile(config, accountId);
        if (target == null) {
            throw new IOException("没有选中的账户");
        }
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        ImageIO.write(image, "png", temp.toFile());
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        copyForGame(config, accountId);
    }

    public static void remove(Config config, String accountId) {
        Path file = skinFile(config, accountId);
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
        }
    }

    public static void copyForGame(Config config, String accountId) {
        copyForGame(config, accountId, config.gameDir());
    }

    public static void copyForGame(Config config, String accountId, Path gameDir) {
        Path source = skinFile(config, accountId);
        if (source == null || !Files.isRegularFile(source) || gameDir == null) {
            return;
        }
        try {
            Files.createDirectories(gameDir);
            Path gameSkin = gameDir.resolve("skin.png");
            Files.copy(source, gameSkin, StandardCopyOption.REPLACE_EXISTING);
            Path csl = gameDir.resolve("CustomSkinLoader/LocalSkin/skins")
                    .resolve(config.username() + ".png");
            Files.createDirectories(csl.getParent());
            Files.copy(source, csl, StandardCopyOption.REPLACE_EXISTING);
            ensureCustomSkinLoaderConfig(gameDir);
        } catch (IOException ex) {
            Log.warn("复制皮肤到游戏目录失败: " + ex.getMessage());
        }
    }

    private static void ensureCustomSkinLoaderConfig(Path gameDir) throws IOException {
        Path configFile = gameDir.resolve("CustomSkinLoader/CustomSkinLoader.json");
        Files.createDirectories(configFile.getParent());
        Map<String, Object> root = null;
        if (Files.isRegularFile(configFile)) {
            try {
                root = Json.obj(Json.parse(Files.readString(configFile)));
            } catch (RuntimeException ignored) {
            }
        }
        if (root == null) {
            root = new LinkedHashMap<>();
            root.put("version", "15.0.2");
            root.put("build", 15);
            Map<String, Object> mojang = new LinkedHashMap<>();
            mojang.put("name", "Mojang");
            mojang.put("type", "MojangAPI");
            mojang.put("apiRoot", "https://api.mojang.com/");
            mojang.put("sessionRoot", "https://sessionserver.mojang.com/");
            List<Object> defaults = new ArrayList<>();
            defaults.add(mojang);
            root.put("loadlist", defaults);
        }
        Object listValue = root.get("loadlist");
        List<Object> loadlist = listValue instanceof List<?>
                ? new ArrayList<>(Json.arr(listValue)) : new ArrayList<>();
        int localIndex = -1;
        for (int i = 0; i < loadlist.size(); i++) {
            if ("LocalSkin".equals(Json.str(Json.obj(loadlist.get(i)), "name"))) {
                localIndex = i;
                break;
            }
        }
        boolean changed = false;
        if (localIndex < 0) {
            Map<String, Object> local = new LinkedHashMap<>();
            local.put("name", "LocalSkin");
            local.put("type", "Legacy");
            local.put("checkPNG", false);
            local.put("skin", "LocalSkin/skins/{USERNAME}.png");
            local.put("model", "auto");
            local.put("cape", "LocalSkin/capes/{USERNAME}.png");
            local.put("elytra", "LocalSkin/elytra/{USERNAME}.png");
            loadlist.add(0, local);
            changed = true;
        } else if (localIndex > 0) {
            Object local = loadlist.remove(localIndex);
            loadlist.add(0, local);
            changed = true;
        }
        if (changed) {
            root.put("loadlist", loadlist);
            Files.writeString(configFile, toJson(root));
        }
    }

    public static byte[] toJsonBytes(Object value) {
        return toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(quote(String.valueOf(entry.getKey()))).append(':')
                        .append(toJson(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(',');
                out.append(toJson(list.get(i)));
            }
            return out.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
