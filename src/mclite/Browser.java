package mclite;

import java.awt.Desktop;
import java.net.URI;

public final class Browser {
    private Browser() {
    }

    public static void open(String url) {
        if (url == null || url.isBlank()) {
            return;
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
            return;
        } catch (Exception ignored) {
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(target));
            }
        } catch (Exception ignored) {
        }
    }
}
