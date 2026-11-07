package dev.mclite.island.mc26;

import net.minecraft.client.Minecraft;

/** GUI Scale 的小封装：烘焙小控件时按它提分辨率，避免圆角被 3x 放大成阶梯。 */
final class UiScale26 {

    private UiScale26() {
    }

    static int current() {
        try {
            int scale = Minecraft.getInstance().getWindow().getGuiScale();
            return Math.max(1, Math.min(3, scale));
        } catch (Throwable ignored) {
            return 1;
        }
    }
}
