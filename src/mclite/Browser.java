package mclite;

import java.awt.Desktop;
import java.net.URI;

public final class Browser {
    private Browser() {
    }

    public static boolean open(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String target = url.startsWith("//") ? "https:" + url : url;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder builder;
            if (os.contains("mac")) {
                builder = new ProcessBuilder("/usr/bin/open", target);
            } else if (os.contains("win")) {
                builder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target);
            } else {
                builder = new ProcessBuilder("xdg-open", target);
            }
            builder.redirectErrorStream(true).start();
            return true;
        } catch (Exception ex) {
            Log.warn("打开浏览器失败: " + ex.getMessage());
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(target));
                return true;
            }
        } catch (Exception ex) {
            Log.warn("Desktop 打开浏览器失败: " + ex.getMessage());
        }
        return false;
    }
}
