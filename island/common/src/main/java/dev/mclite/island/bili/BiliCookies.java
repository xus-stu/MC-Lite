package dev.mclite.island.bili;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/** 本地保存的 B 站登录 cookie（只存三项 + uid）。 */
public final class BiliCookies {

    private static final Gson GSON = new Gson();

    public String sessdata = "";
    public String biliJct = "";
    public String dedeUserId = "";
    public long savedAt = 0L;

    public boolean loggedIn() {
        return sessdata != null && !sessdata.isBlank() && dedeUserId != null && !dedeUserId.isBlank();
    }

    public String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        if (sessdata != null && !sessdata.isBlank()) {
            sb.append("SESSDATA=").append(sessdata).append("; ");
        }
        if (biliJct != null && !biliJct.isBlank()) {
            sb.append("bili_jct=").append(biliJct).append("; ");
        }
        if (dedeUserId != null && !dedeUserId.isBlank()) {
            sb.append("DedeUserID=").append(dedeUserId).append("; ");
        }
        return sb.toString();
    }

    public static BiliCookies load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new BiliCookies();
        }
        try {
            BiliCookies cookies = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    BiliCookies.class);
            return cookies == null ? new BiliCookies() : cookies;
        } catch (IOException | JsonSyntaxException ex) {
            return new BiliCookies();
        }
    }

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 等不支持 POSIX 的文件系统忽略
        }
    }

    public void clear() {
        sessdata = "";
        biliJct = "";
        dedeUserId = "";
        savedAt = 0L;
    }
}
