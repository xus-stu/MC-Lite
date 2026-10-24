package mclite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Integrity {
    private Integrity() {
    }

    public static List<String> missing(Config config, VersionMeta meta, String versionId) {
        GamePaths paths = new GamePaths(config.gameDir());
        List<String> missing = new ArrayList<>();
        if (!Files.isRegularFile(paths.versionJson(versionId))) {
            missing.add("版本信息");
        }
        String clientId = meta.clientJarId() == null ? meta.id() : meta.clientJarId();
        if (!Files.isRegularFile(paths.clientJar(clientId))) {
            missing.add("客户端 jar");
        }
        Rules.Env env = Rules.Env.current();
        for (VersionMeta.Library library : meta.libraries()) {
            if (!library.applies(env)) {
                continue;
            }
            VersionMeta.Download artifact = library.artifact();
            if (artifact != null && artifact.path() != null
                    && !Files.isRegularFile(GamePaths.resolveUnder(paths.librariesDir(), artifact.path()))) {
                missing.add(library.name());
            }
            VersionMeta.Download nativeFile = library.nativeFor(env);
            if (nativeFile != null && nativeFile.path() != null
                    && !Files.isRegularFile(GamePaths.resolveUnder(paths.librariesDir(), nativeFile.path()))) {
                missing.add(library.name() + " natives");
            }
        }
        if (meta.assetIndex() != null && !Files.isRegularFile(paths.assetIndex(meta.assetsId()))) {
            missing.add("资源索引");
        }
        if (paths.hasMarker(versionId) && !paths.assetsComplete(versionId)) {
            missing.add("资源文件");
        }
        return missing;
    }

    public static String summary(List<String> missing) {
        if (missing.isEmpty()) {
            return "";
        }
        int count = Math.min(5, missing.size());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                text.append("\n");
            }
            text.append("· ").append(missing.get(i));
        }
        if (missing.size() > count) {
            text.append("\n· 还有 ").append(missing.size() - count).append(" 项");
        }
        return text.toString();
    }
}
