package mclite;

import java.util.List;

public final class Recommendations {
    public record Item(String slug, String title, String note, boolean selected) {
    }

    private Recommendations() {
    }

    public static List<Item> forLoader(String loader) {
        return forLoader(loader, null);
    }

    public static List<Item> forLoader(String loader, String gameVersion) {
        if ("fabric".equals(loader) || "quilt".equals(loader)) {
            return List.of(
                    new Item("sodium", "Sodium", "渲染优化，高帧率核心", true),
                    new Item("lithium", "Lithium", "服务端与逻辑优化", true),
                    new Item("immediatelyfast", "ImmediatelyFast", "立即模式渲染优化，提高帧率", true),
                    new Item("entityculling", "Entity Culling", "剔除不可见实体渲染", true),
                    new Item("moreculling", "More Culling", "进一步剔除不可见方块面", true),
                    new Item("badoptimizations", "BadOptimizations", "多项渲染与逻辑优化", true),
                    new Item("ferrite-core", "FerriteCore", "降低内存占用", true),
                    new Item("krypton", "Krypton", "网络线程优化", true),
                    new Item("sodium-extra", "Sodium Extra", "Sodium 画质与性能选项", true),
                    new Item("c2me-fabric", "C2ME", "实验性区块加载优化，追求极限帧率可试", false),
                    new Item("modmenu", "Mod Menu", "模组列表菜单", true),
                    new Item("cloth-config", "Cloth Config", "通用配置库", true),
                    new Item("iris", "Iris Shaders", "光影支持", false),
                    new Item("indium", "Indium", "Sodium 兼容层", false));
        }
        if ("forge".equals(loader) || "neoforge".equals(loader)) {
            if ("forge".equals(loader) && gameVersion != null && gameVersion.startsWith("1.21")) {
                return List.of(
                        new Item("immediatelyfast", "ImmediatelyFast", "立即模式渲染优化，提高帧率", true),
                        new Item("entityculling", "Entity Culling", "剔除不可见实体渲染", true),
                        new Item("badoptimizations", "BadOptimizations", "多项渲染与逻辑优化", true),
                        new Item("ferrite-core", "FerriteCore", "降低内存占用", true),
                        new Item("modernfix", "ModernFix", "启动与内存优化", true),
                        new Item("clumps", "Clumps", "合并经验球，降低卡顿", true),
                        new Item("jei", "Just Enough Items", "物品配方查询", true));
            }
            return List.of(
                    new Item("embeddium", "Embeddium", "渲染优化，提高帧率", true),
                    new Item("immediatelyfast", "ImmediatelyFast", "立即模式渲染优化，提高帧率", true),
                    new Item("entityculling", "Entity Culling", "剔除不可见实体渲染", true),
                    new Item("badoptimizations", "BadOptimizations", "多项渲染与逻辑优化", true),
                    new Item("ferrite-core", "FerriteCore", "降低内存占用", true),
                    new Item("modernfix", "ModernFix", "启动与内存优化", true),
                    new Item("clumps", "Clumps", "合并经验球，降低卡顿", true),
                    new Item("jei", "Just Enough Items", "物品配方查询", true));
        }
        return List.of();
    }
}
