package mclite;

import java.io.IOException;

/** Adds project and selected version context to content install failures. */
public final class ContentInstallException extends IOException {
    private final String project;
    private final String gameVersion;
    private final String loader;
    private final String supportedVersions;

    public ContentInstallException(Modrinth.Project project, String gameVersion,
                                   String loader, String supportedVersions, Throwable cause) {
        super(buildMessage(project, gameVersion, loader, supportedVersions, cause), cause);
        this.project = project == null ? "未知项目" : project.title();
        this.gameVersion = gameVersion == null ? "未指定" : gameVersion;
        this.loader = loader == null || loader.isBlank() ? "无" : loader;
        this.supportedVersions = supportedVersions == null ? "" : supportedVersions;
    }

    public String project() {
        return project;
    }

    public String gameVersion() {
        return gameVersion;
    }

    public String loader() {
        return loader;
    }

    public String supportedVersions() {
        return supportedVersions;
    }

    private static String buildMessage(Modrinth.Project project, String gameVersion,
                                       String loader, String supportedVersions, Throwable cause) {
        String reason = cause == null ? "未知错误"
                : cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
        StringBuilder message = new StringBuilder();
        message.append("项目：").append(project == null ? "未知项目" : project.title());
        if (project != null) {
            message.append("（").append(project.source()).append(" / ")
                    .append(project.id()).append("）");
        }
        message.append('\n').append("选择版本：").append(gameVersion == null ? "未指定" : gameVersion);
        if (loader != null && !loader.isBlank()) {
            message.append(" / ").append(loader);
        }
        if (supportedVersions != null && !supportedVersions.isBlank()) {
            message.append('\n').append("支持版本：").append(supportedVersions);
        }
        message.append('\n').append("原因：").append(reason);
        return message.toString();
    }
}
