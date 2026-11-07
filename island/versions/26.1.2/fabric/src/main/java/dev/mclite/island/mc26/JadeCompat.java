package dev.mclite.island.mc26;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Jade 可选集成：反射读取 Jade 当前准星目标的提示文本。
 *
 * <p>Jade 没有公开「读当前 tooltip」的 API，这里只碰它稳定的 public 字段
 * （{@code JadeClient.tickHandler.rootElement.getTooltip().getNarration()}），
 * 任何一步失败都返回 null，由调用方降级到自带的射线探针。
 */
final class JadeCompat {

    private JadeCompat() {
    }

    /** @return Jade 当前提示的第一行；没有目标/读不到时 null。 */
    static String targetLine() {
        try {
            Class<?> jadeClient = Class.forName("snownee.jade.JadeClient");
            Field handlerField = jadeClient.getDeclaredField("tickHandler");
            handlerField.setAccessible(true);
            Object handler = handlerField.get(null);
            if (handler == null) {
                return null;
            }
            Field rootField = handler.getClass().getField("rootElement");
            Object root = rootField.get(handler);
            if (root == null) {
                return null;
            }
            Method getTooltip = root.getClass().getMethod("getTooltip");
            Object tooltip = getTooltip.invoke(root);
            if (tooltip == null) {
                return null;
            }
            Method narration = tooltip.getClass().getMethod("getNarration");
            Object value = narration.invoke(tooltip);
            if (!(value instanceof String text) || text.isBlank()) {
                return null;
            }
            for (String line : text.split("\\R")) {
                if (!line.isBlank()) {
                    return line.trim();
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
