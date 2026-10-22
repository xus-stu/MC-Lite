package mclite;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

public final class LauncherApp {
    private LauncherApp() {
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv6Addresses", "true");
        System.setProperty("apple.awt.application.name", "MC Lite");
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        RegionGate.configure(args);

        Path screenshot = null;
        String screenshotPage = null;
        boolean selfTest = false;
        boolean crashDemo = false;
        for (String arg : args) {
            if (arg.startsWith("--screenshot=")) {
                screenshot = Path.of(arg.substring("--screenshot=".length())).toAbsolutePath();
                System.setProperty("mclite.offline", "true");
            } else if (arg.startsWith("--page=")) {
                screenshotPage = arg.substring("--page=".length());
            } else if (arg.equals("--self-test")) {
                selfTest = true;
            } else if (arg.equals("--crash-demo")) {
                crashDemo = true;
            }
        }

        Config config = Config.load();
        Log.init(config.logsDir().resolve("launcher.log"));
        Theme.applyCss(config.uiCssFile());
        if (!config.uiFontFamily().isBlank()) {
            Theme.setFontFamily(config.uiFontFamily());
        }
        Theme.install();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            Log.error("未捕获异常（" + thread.getName() + "）", error);
            if (!GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "发生未预期的错误：\n" + error.getMessage()
                                + "\n\n日志：" + Log.file(),
                        "MC Lite 错误", JOptionPane.ERROR_MESSAGE));
            }
        });

        if (selfTest) {
            int code = SelfTest.run(config);
            System.exit(code);
        }

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("当前环境没有图形界面，无法启动 MC Lite。");
            System.exit(2);
        }

        Path finalScreenshot = screenshot;
        String finalPage = screenshotPage;
        final boolean finalCrashDemo = crashDemo;
        SwingUtilities.invokeLater(() -> {
            try {
                LauncherFrame frame = new LauncherFrame(config);
                frame.setVisible(true);
                if (finalCrashDemo) {
                    Timer demo = new Timer(500, e -> frame.showCrashDemo());
                    demo.setRepeats(false);
                    demo.start();
                }
                if (finalScreenshot != null && finalPage != null) {
                    frame.selectPage(finalPage);
                    if ("project".equals(finalPage)) {
                        Timer delay = new Timer(2000, e -> frame.openFirstProjectForScreenshot());
                        delay.setRepeats(false);
                        delay.start();
                    } else if ("version-detail".equals(finalPage)) {
                        Timer delay = new Timer(2000, e -> frame.openFirstVersionDetailForScreenshot());
                        delay.setRepeats(false);
                        delay.start();
                    }
                }
                if (finalScreenshot != null) {
                    Timer timer = new Timer(8000, e -> {
                        try {
                            frame.saveScreenshot(finalScreenshot);
                            System.out.println("screenshot=" + finalScreenshot);
                        } catch (Exception ex) {
                            Log.error("截图失败", ex);
                        }
                        frame.dispose();
                        System.exit(0);
                    });
                    timer.setRepeats(false);
                    timer.start();
                }
            } catch (Throwable error) {
                Log.error("启动界面失败", error);
                JOptionPane.showMessageDialog(null, "无法启动界面：" + error.getMessage(),
                        "MC Lite", JOptionPane.ERROR_MESSAGE);
                System.exit(3);
            }
        });
    }
}
