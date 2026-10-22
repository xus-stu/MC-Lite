package mclite;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        Path source = skinFile(config, accountId);
        if (source == null || !Files.isRegularFile(source)) {
            return;
        }
        try {
            Path gameSkin = config.gameDir().resolve("skin.png");
            Files.copy(source, gameSkin, StandardCopyOption.REPLACE_EXISTING);
            Path csl = config.gameDir().resolve("CustomSkinLoader/LocalSkin/skins")
                    .resolve(config.username() + ".png");
            Files.createDirectories(csl.getParent());
            Files.copy(source, csl, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Log.warn("复制皮肤到游戏目录失败: " + ex.getMessage());
        }
    }
}
