package mclite;

import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 窗口透明度。
 *
 * <p>Java 包装层禁止对带装饰（标题栏）的窗口调用 {@link Window#setOpacity}，
 * 但 macOS 的底层 {@code CPlatformWindow.setOpacity(float)} 其实支持。
 * 这里通过反射直接调用原生实现，从而在保留系统红绿灯的同时改透明度。
 * 需要启动参数打开相应模块——由 jar 清单的 {@code Add-Opens} 提供。</p>
 */
final class WindowFx {

    private WindowFx() {
    }

    static void opacity(Window window, float value) {
        if (window == null || !LauncherFrame.isMac()) {
            return;
        }
        float opacity = Math.max(0.8f, Math.min(1f, value));
        try {
            Field peerField = java.awt.Component.class.getDeclaredField("peer");
            peerField.setAccessible(true);
            Object peer = peerField.get(window);
            if (peer == null) {
                return;
            }
            Object platform = peer.getClass().getMethod("getPlatformWindow").invoke(peer);
            if (platform == null) {
                return;
            }
            Method setOpacity = platform.getClass().getMethod("setOpacity", float.class);
            setOpacity.invoke(platform, opacity);
        } catch (Throwable ignored) {
            // 模块没打开或平台不支持时静默跳过，不影响启动。
        }
    }
}
